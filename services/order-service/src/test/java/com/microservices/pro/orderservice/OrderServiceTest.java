package com.microservices.pro.orderservice;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Test
    void createOrder_returnsRejected_whenInventoryReportsUnavailable_andNeverTouchesRepositoryOrOutbox() {
        OrderRequest request = new OrderRequest("PROD-003", 1, new BigDecimal("50.00"), "cust-1");
        when(inventoryClient.checkStock("PROD-003", 1))
                .thenReturn(new StockCheckResponse("PROD-003", 1, false, 0));

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void createOrder_returnsPending_whenInventoryReportsAvailable() {
        OrderRequest request = new OrderRequest("PROD-001", 2, new BigDecimal("100.00"), "cust-1");
        when(inventoryClient.checkStock("PROD-001", 2))
                .thenReturn(new StockCheckResponse("PROD-001", 2, true, 98));

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.orderId()).isNotNull();
        verify(orderRepository).save(any(Order.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void getOrder_returnsOrder_whenFound() {
        Order order = new Order("ORD-1", "PROD-1", 1, BigDecimal.TEN, OrderStatus.PENDING, "cust-1");
        when(orderRepository.findById("ORD-1")).thenReturn(Optional.of(order));

        Order result = orderService.getOrder("ORD-1");
        assertThat(result.getOrderId()).isEqualTo("ORD-1");
        assertThat(result.getCustomerId()).isEqualTo("cust-1");
    }

    @Test
    void getOrder_throwsException_whenNotFound() {
        when(orderRepository.findById("ORD-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder("ORD-999"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("ORD-999");
    }

    @Test
    void getOrdersByCustomerId_returnsMatchingOrders() {
        Order order = new Order("ORD-1", "PROD-1", 1, BigDecimal.TEN, OrderStatus.PENDING, "cust-1");
        when(orderRepository.findByCustomerId("cust-1")).thenReturn(List.of(order));

        List<Order> result = orderService.getOrdersByCustomerId("cust-1");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderId()).isEqualTo("ORD-1");
    }

    @Test
    void resilience_circuitFallback_throwsServiceUnavailable() {
        CompletableFuture<StockCheckResponse> future = orderService.inventoryCircuitFallback(
                "PROD-1", 1, new RuntimeException("Connection refused")
        );
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void resilience_bulkheadFallback_throwsServiceUnavailable() {
        Bulkhead bulkhead = Bulkhead.ofDefaults("test");
        CompletableFuture<StockCheckResponse> future = orderService.inventoryBulkheadFallback(
                "PROD-1", 1, BulkheadFullException.createBulkheadFullException(bulkhead)
        );
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void resilience_timeoutFallback_throwsServiceUnavailable() {
        CompletableFuture<StockCheckResponse> future = orderService.inventoryTimeoutFallback(
                "PROD-1", 1, new TimeoutException("Timed out")
        );
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ServiceUnavailableException.class);
    }
}
