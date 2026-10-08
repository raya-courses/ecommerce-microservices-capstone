package com.microservices.pro.orderservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.pro.orderservice.events.InventoryReleasedEvent;
import com.microservices.pro.orderservice.events.InventoryReservationFailedEvent;
import com.microservices.pro.orderservice.events.OrderCancelledEvent;
import com.microservices.pro.orderservice.events.OrderConfirmedEvent;
import com.microservices.pro.orderservice.events.PaymentCompletedEvent;
import com.microservices.pro.orderservice.events.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OrderSagaEventHandler — Session 7 / Phase 5 Choreography Saga.
 *
 * Handles the terminal Saga outcomes for an order.
 * Consumes:
 *   - "payment-events" (group "order-service"): PaymentCompletedEvent, PaymentFailedEvent
 *   - "inventory-events" (group "order-service-cancel"): InventoryReleasedEvent, InventoryReservationFailedEvent
 *
 * Emits terminal events reliably through Transactional Outbox:
 *   - OrderConfirmedEvent (when CONFIRMED)
 *   - OrderCancelledEvent (when CANCELLED)
 */
@Service
public class OrderSagaEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaEventHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private OutboxRepository outboxRepository;

    public OrderSagaEventHandler() {}

    public OrderSagaEventHandler(OrderRepository orderRepository, OutboxRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service")
    @Transactional
    public void handlePaymentEvent(String rawEvent) {
        try {
            JsonNode root = objectMapper.readTree(rawEvent);
            String eventType = root.path("eventType").asText("");

            boolean isCompleted = "PaymentCompletedEvent".equalsIgnoreCase(eventType)
                    || "PaymentCompleted".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("transactionId"));

            boolean isFailed = "PaymentFailedEvent".equalsIgnoreCase(eventType)
                    || "PaymentFailed".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("reason") && !root.has("transactionId"));

            if (isCompleted) {
                PaymentCompletedEvent event = objectMapper.treeToValue(root, PaymentCompletedEvent.class);
                handlePaymentCompleted(event);
            } else if (isFailed) {
                PaymentFailedEvent event = objectMapper.treeToValue(root, PaymentFailedEvent.class);
                handlePaymentFailed(event);
            } else {
                log.debug("[SAGA] Ignored unrecognized payment event: {}", rawEvent);
            }
        } catch (Exception e) {
            log.error("[SAGA] Failed to process payment event: {}", rawEvent, e);
            throw new RuntimeException("Error processing payment event", e);
        }
    }

    private void handlePaymentCompleted(PaymentCompletedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + event.orderId()));

        // Idempotency: if already confirmed, do not apply transition or emit duplicate outbox event
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("[SAGA] Duplicate event: Order {} is already CONFIRMED", event.orderId());
            return;
        }

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("[SAGA] Order {} CONFIRMED", event.orderId());

        // Emit terminal OrderConfirmedEvent via Outbox
        if (outboxRepository != null) {
            try {
                OrderConfirmedEvent confirmedEvent = new OrderConfirmedEvent(
                        order.getOrderId(),
                        event.transactionId(),
                        order.getCustomerId()
                );
                String payload = objectMapper.writeValueAsString(confirmedEvent);
                outboxRepository.save(new OutboxEvent(order.getOrderId(), "ORDER", "OrderConfirmedEvent", payload));
            } catch (Exception e) {
                log.error("[SAGA] Failed to save OrderConfirmedEvent to outbox for order {}", order.getOrderId(), e);
            }
        }
    }

    private void handlePaymentFailed(PaymentFailedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + event.orderId()));

        // Idempotency: if already cancelled or already failed, ignore duplicate
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            log.info("[SAGA] Duplicate event: Order {} already in state {}", event.orderId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);
        log.warn("[SAGA] Order {} payment failed, waiting for inventory release...", event.orderId());
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service-cancel")
    @Transactional
    public void handleInventoryReleased(String rawEvent) {
        try {
            JsonNode root = objectMapper.readTree(rawEvent);
            String eventType = root.path("eventType").asText("");

            boolean isReleased = "InventoryReleasedEvent".equalsIgnoreCase(eventType)
                    || "InventoryReleased".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("orderId") && !root.has("productId") && !root.has("reason"));

            boolean isReservationFailed = "InventoryReservationFailedEvent".equalsIgnoreCase(eventType)
                    || "InventoryReservationFailed".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("reason") && root.has("orderId"));

            if (isReleased) {
                InventoryReleasedEvent event = objectMapper.treeToValue(root, InventoryReleasedEvent.class);
                cancelOrder(event.orderId(), "Payment failed and inventory released");
            } else if (isReservationFailed) {
                InventoryReservationFailedEvent event = objectMapper.treeToValue(root, InventoryReservationFailedEvent.class);
                cancelOrder(event.orderId(), "Inventory reservation failed: " + event.reason());
            }
        } catch (Exception e) {
            log.error("[SAGA] Failed to process inventory compensation event: {}", rawEvent, e);
            throw new RuntimeException("Error processing inventory event", e);
        }
    }

    private void cancelOrder(String orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("[SAGA] Duplicate event: Order {} is already CANCELLED", orderId);
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("[SAGA] Order {} CANCELLED — reason: {}", orderId, reason);

        // Emit terminal OrderCancelledEvent via Outbox
        if (outboxRepository != null) {
            try {
                OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(
                        order.getOrderId(),
                        reason,
                        order.getCustomerId()
                );
                String payload = objectMapper.writeValueAsString(cancelledEvent);
                outboxRepository.save(new OutboxEvent(order.getOrderId(), "ORDER", "OrderCancelledEvent", payload));
            } catch (Exception e) {
                log.error("[SAGA] Failed to save OrderCancelledEvent to outbox for order {}", order.getOrderId(), e);
            }
        }
    }
}
