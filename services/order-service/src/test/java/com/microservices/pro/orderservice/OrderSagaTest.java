package com.microservices.pro.orderservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderSagaTest — Session 7 / Phase 5 Transactional Outbox.
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Test
    void createOrder_savesOrderWithPendingStatus() {
        OrderRequest request = new OrderRequest("PROD-001", 2, new BigDecimal("200.00"), "cust-1");
        when(inventoryClient.checkStock("PROD-001", 2))
                .thenReturn(new StockCheckResponse("PROD-001", 2, true, 98));

        orderService.createOrder(request);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void createOrder_writesOutboxEvent_withoutDirectKafkaSend() {
        OrderRequest request = new OrderRequest("PROD-001", 2, new BigDecimal("200.00"), "cust-1");
        when(inventoryClient.checkStock("PROD-001", 2))
                .thenReturn(new StockCheckResponse("PROD-001", 2, true, 98));

        orderService.createOrder(request);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());

        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getEventType()).isEqualTo("OrderPlacedEvent");
        assertThat(outboxEvent.getAggregateType()).isEqualTo("ORDER");
        assertThat(outboxEvent.isPublished()).isFalse();
        assertThat(outboxEvent.getPayload()).contains("PROD-001");
    }
}
