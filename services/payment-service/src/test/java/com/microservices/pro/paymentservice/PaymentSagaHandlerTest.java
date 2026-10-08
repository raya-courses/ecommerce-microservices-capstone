package com.microservices.pro.paymentservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PaymentSagaHandlerTest — Session 7 / Phase 5 Saga & Idempotency.
 */
@ExtendWith(MockitoExtension.class)
class PaymentSagaHandlerTest {

    @InjectMocks
    private PaymentSagaHandler handler;

    @Mock
    private PaymentProcessor paymentProcessor;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Test
    void handleInventoryReserved_ignoresOtherEventTypesOnTheSameTopic() {
        String inventoryReservationFailedJson = "{\"orderId\":\"order-123\",\"reason\":\"out of stock\",\"eventType\":\"InventoryReservationFailedEvent\"}";

        handler.handleInventoryReserved(inventoryReservationFailedJson);

        verify(paymentProcessor, never()).processPayment(anyString());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void handleInventoryReserved_publishesPaymentCompleted_onSuccess() {
        String inventoryReservedJson = "{\"orderId\":\"order-123\",\"productId\":\"PROD-001\",\"quantity\":2,\"eventType\":\"InventoryReservedEvent\"}";
        when(paymentProcessor.processPayment("order-123")).thenReturn("tx-456");

        handler.handleInventoryReserved(inventoryReservedJson);

        verify(kafkaTemplate).send(
                eq("payment-events"),
                eq("order-123"),
                any(com.microservices.pro.paymentservice.events.PaymentCompletedEvent.class));
    }

    @Test
    void handleInventoryReserved_publishesPaymentFailed_whenProcessorThrows() {
        String inventoryReservedJson = "{\"orderId\":\"order-123\",\"productId\":\"PROD-001\",\"quantity\":2,\"eventType\":\"InventoryReservedEvent\"}";
        when(paymentProcessor.processPayment("order-123"))
                .thenThrow(new PaymentException("card declined"));

        handler.handleInventoryReserved(inventoryReservedJson);

        verify(kafkaTemplate).send(
                eq("payment-events"),
                eq("order-123"),
                any(com.microservices.pro.paymentservice.events.PaymentFailedEvent.class));
    }

    @Test
    void handleInventoryReserved_duplicateCompletedEvent_doesNotProcessAgain() {
        String inventoryReservedJson = "{\"orderId\":\"order-123\",\"productId\":\"PROD-001\",\"quantity\":2,\"eventType\":\"InventoryReservedEvent\"}";
        Payment completedPayment = new Payment("pay-1", "order-123", BigDecimal.valueOf(100), "COMPLETED", "tx-existing");
        when(paymentRepository.findByOrderId("order-123")).thenReturn(Optional.of(completedPayment));

        handler.handleInventoryReserved(inventoryReservedJson);

        verify(paymentProcessor, never()).processPayment(anyString());
        verify(kafkaTemplate).send(
                eq("payment-events"),
                eq("order-123"),
                any(com.microservices.pro.paymentservice.events.PaymentCompletedEvent.class));
    }
}
