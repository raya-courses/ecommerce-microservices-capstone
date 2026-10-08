package com.microservices.pro.orderservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrderSagaEventHandlerTest — Session 7 / Phase 5 Saga Choreography & Idempotency.
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaEventHandlerTest {

    @InjectMocks
    private OrderSagaEventHandler handler;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Test
    void handlePaymentEvent_updatesOrderToPaymentFailed_onPaymentFailedJson() {
        Order existingOrder = new Order("order-123", "PROD-001", 2, new BigDecimal("200.00"), OrderStatus.PENDING, "cust-1");
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(existingOrder));

        String paymentFailedJson = "{\"orderId\":\"order-123\",\"reason\":\"PaymentFailed: card declined\",\"eventType\":\"PaymentFailedEvent\"}";

        handler.handlePaymentEvent(paymentFailedJson);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void handlePaymentEvent_updatesOrderToConfirmed_andEmitsOutbox_onPaymentCompleted() {
        Order existingOrder = new Order("order-123", "PROD-001", 2, new BigDecimal("200.00"), OrderStatus.PENDING, "cust-1");
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(existingOrder));

        String paymentCompletedJson = "{\"orderId\":\"order-123\",\"transactionId\":\"TX-999\",\"eventType\":\"PaymentCompletedEvent\"}";

        handler.handlePaymentEvent(paymentCompletedJson);

        verify(orderRepository).save(existingOrder);
        assertThat(existingOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("OrderConfirmedEvent");
        assertThat(outboxCaptor.getValue().getPayload()).contains("TX-999");
    }

    @Test
    void handlePaymentEvent_duplicateCompletedDoesNotCorruptStateOrReEmit() {
        Order alreadyConfirmed = new Order("order-123", "PROD-001", 2, new BigDecimal("200.00"), OrderStatus.CONFIRMED, "cust-1");
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(alreadyConfirmed));

        String paymentCompletedJson = "{\"orderId\":\"order-123\",\"transactionId\":\"TX-999\",\"eventType\":\"PaymentCompletedEvent\"}";

        handler.handlePaymentEvent(paymentCompletedJson);

        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void handlePaymentEvent_duplicateFailedDoesNotCorruptState() {
        Order alreadyFailed = new Order("order-123", "PROD-001", 2, new BigDecimal("200.00"), OrderStatus.PAYMENT_FAILED, "cust-1");
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(alreadyFailed));

        String paymentFailedJson = "{\"orderId\":\"order-123\",\"reason\":\"PaymentFailed: card declined\",\"eventType\":\"PaymentFailedEvent\"}";

        handler.handlePaymentEvent(paymentFailedJson);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleInventoryReleased_cancelsOrder_andEmitsOutboxEvent() {
        Order existingOrder = new Order("order-123", "PROD-001", 2, new BigDecimal("200.00"), OrderStatus.PAYMENT_FAILED, "cust-1");
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(existingOrder));

        String releasedJson = "{\"orderId\":\"order-123\",\"eventType\":\"InventoryReleasedEvent\"}";

        handler.handleInventoryReleased(releasedJson);

        verify(orderRepository).save(existingOrder);
        assertThat(existingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("OrderCancelledEvent");
    }

    @Test
    void handleInventoryReleased_duplicateReleaseDoesNotCorruptState() {
        Order alreadyCancelled = new Order("order-123", "PROD-001", 2, new BigDecimal("200.00"), OrderStatus.CANCELLED, "cust-1");
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(alreadyCancelled));

        String releasedJson = "{\"orderId\":\"order-123\",\"eventType\":\"InventoryReleasedEvent\"}";

        handler.handleInventoryReleased(releasedJson);

        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }
}
