package com.microservices.pro.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.pro.orderservice.events.OrderPlacedEvent;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * OrderService — Saga initiator using Choreography with Transactional Outbox pattern
 * and Resilience4j protection on Inventory pre-check.
 *
 * Sequence:
 *   1. Synchronous Inventory pre-check via Feign protected by Resilience4j
 *      (@Bulkhead -> @TimeLimiter -> @CircuitBreaker -> @Retry).
 *   2. If stock is unavailable, reject immediately.
 *   3. Inside ONE local transaction:
 *      - Save the Order with status PENDING.
 *      - Save OrderPlacedEvent into the outbox_events table.
 *      - Do NOT directly send to Kafka from this transaction.
 *   4. Return immediately with PENDING.
 *   5. OutboxPublisher reliably polls and publishes to "order-events".
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private InventoryClient inventoryClient;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private OutboxRepository outboxRepository;

    public OrderService() {}

    public OrderService(InventoryClient inventoryClient,
                        OrderRepository orderRepository,
                        OutboxRepository outboxRepository) {
        this.inventoryClient = inventoryClient;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    @Bulkhead(name = "inventoryService", fallbackMethod = "inventoryBulkheadFallback")
    @TimeLimiter(name = "inventoryService", fallbackMethod = "inventoryTimeoutFallback")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "inventoryCircuitFallback")
    @Retry(name = "inventoryService")
    public CompletableFuture<StockCheckResponse> checkStockWithResilience(String productId, int quantity) {
        return CompletableFuture.supplyAsync(() -> inventoryClient.checkStock(productId, quantity));
    }

    public CompletableFuture<StockCheckResponse> inventoryCircuitFallback(String productId, int quantity, Throwable ex) {
        if (ex instanceof InsufficientStockException) {
            CompletableFuture<StockCheckResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
        log.error("[RESILIENCE] Inventory CircuitBreaker fallback triggered for product {}: {}", productId, ex.getMessage());
        CompletableFuture<StockCheckResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new ServiceUnavailableException("Inventory service is unavailable via circuit breaker"));
        return failed;
    }

    public CompletableFuture<StockCheckResponse> inventoryBulkheadFallback(String productId, int quantity, BulkheadFullException ex) {
        log.warn("[RESILIENCE] Inventory Bulkhead limit reached for product {}: {}", productId, ex.getMessage());
        CompletableFuture<StockCheckResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new ServiceUnavailableException("Inventory service is busy (bulkhead full)"));
        return failed;
    }

    public CompletableFuture<StockCheckResponse> inventoryTimeoutFallback(String productId, int quantity, TimeoutException ex) {
        log.warn("[RESILIENCE] Inventory Timeout exceeded for product {}: {}", productId, ex.getMessage());
        CompletableFuture<StockCheckResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new ServiceUnavailableException("Inventory service request timed out"));
        return failed;
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        StockCheckResponse stock;
        try {
            stock = checkStockWithResilience(request.productId(), request.quantity()).join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            if (cause instanceof InsufficientStockException) {
                return new OrderResponse(null, "REJECTED", cause.getMessage());
            }
            if (cause instanceof ServiceUnavailableException sue) {
                throw sue;
            }
            throw new ServiceUnavailableException("Failed to check inventory: " + cause.getMessage());
        } catch (InsufficientStockException ex) {
            return new OrderResponse(null, "REJECTED", ex.getMessage());
        }

        if (stock == null || !stock.available()) {
            int remaining = stock != null ? stock.remainingStock() : 0;
            return new OrderResponse(null, "REJECTED",
                    "Insufficient stock: only " + remaining + " available");
        }

        Order order = new Order(
                UUID.randomUUID().toString(),
                request.productId(),
                request.quantity(),
                request.amount(),
                OrderStatus.PENDING,
                request.customerId()
        );
        orderRepository.save(order);

        // Transactional Outbox pattern: save event in the same transaction instead of direct Kafka publish
        if (outboxRepository != null) {
            try {
                OrderPlacedEvent event = new OrderPlacedEvent(
                        order.getOrderId(),
                        request.productId(),
                        request.quantity(),
                        request.amount(),
                        request.customerId()
                );
                String payload = objectMapper.writeValueAsString(event);
                outboxRepository.save(new OutboxEvent(
                        order.getOrderId(),
                        "ORDER",
                        "OrderPlacedEvent",
                        payload
                ));
                log.info("[OUTBOX] Staged OrderPlacedEvent for orderId={}", order.getOrderId());
            } catch (Exception e) {
                log.error("[OUTBOX] Failed to stage OrderPlacedEvent for orderId={}", order.getOrderId(), e);
                throw new RuntimeException("Failed to stage outbox event", e);
            }
        }

        return new OrderResponse(order.getOrderId(), "PENDING", "Order received — processing...");
    }

    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    public List<Order> getOrdersByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public OrderStatus getOrderStatus(String orderId) {
        return getOrder(orderId).getStatus();
    }
}
