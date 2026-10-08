package com.microservices.pro.inventoryservice;

import com.microservices.pro.inventoryservice.events.InventoryReservationFailedEvent;
import com.microservices.pro.inventoryservice.events.InventoryReservedEvent;
import com.microservices.pro.inventoryservice.events.OrderCancelledEvent;
import com.microservices.pro.inventoryservice.events.OrderConfirmedEvent;
import com.microservices.pro.inventoryservice.events.OrderPlacedEvent;
import com.microservices.pro.inventoryservice.events.PaymentCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * InventorySagaTest — Unit tests for Saga handlers, compensations, and event routing.
 */
@ExtendWith(MockitoExtension.class)
class InventorySagaTest {

    @InjectMocks
    private InventorySagaHandler handler;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void handleOrderPlaced_publishesInventoryReservedEvent_whenStockAvailable() {
        OrderPlacedEvent event = new OrderPlacedEvent("order-123", "PROD-001", 2, new BigDecimal("200.00"), "cust-1");

        handler.handleOrderPlaced(event);

        verify(kafkaTemplate).send(
                eq("inventory-events"),
                eq("order-123"),
                any(InventoryReservedEvent.class));
    }

    @Test
    void handleOrderPlaced_publishesInventoryReservationFailedEvent_whenStockInsufficient() {
        OrderPlacedEvent event = new OrderPlacedEvent("order-fail", "PROD-001", 20, new BigDecimal("200.00"), "cust-1");
        doThrow(new InsufficientStockException("Out of stock"))
                .when(inventoryService).reserveStock("PROD-001", 20, "order-fail");

        handler.handleOrderPlaced(event);

        verify(kafkaTemplate).send(
                eq("inventory-events"),
                eq("order-fail"),
                any(InventoryReservationFailedEvent.class));
    }

    @Test
    void handleOrderEvents_parsesJsonAndCallsHandleOrderPlaced() {
        String json = "{\"orderId\":\"order-json\",\"productId\":\"PROD-001\",\"quantity\":3,\"amount\":150.00,\"customerId\":\"c1\",\"eventType\":\"OrderPlacedEvent\"}";

        handler.handleOrderEvents(json);

        verify(inventoryService).reserveStock("PROD-001", 3, "order-json");
        verify(kafkaTemplate).send(eq("inventory-events"), eq("order-json"), any(InventoryReservedEvent.class));
    }

    @Test
    void handleOrderEvents_ignoresNonOrderPlacedEvents() {
        String json = "{\"orderId\":\"order-other\",\"eventType\":\"SomeOtherEvent\"}";

        handler.handleOrderEvents(json);

        verifyNoInteractions(inventoryService);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void handleOrderEvents_throwsRuntimeExceptionOnMalformedJson() {
        assertThatThrownBy(() -> handler.handleOrderEvents("invalid-json"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void handlePaymentFailed_callsReleaseStock_andPublishesInventoryReleasedEvent() {
        String paymentFailedJson = "{\"orderId\":\"order-123\",\"reason\":\"PaymentFailed: card declined\"}";

        handler.handlePaymentFailed(paymentFailedJson);

        verify(inventoryService).releaseStock("order-123");
        verify(kafkaTemplate).send(
                eq("inventory-events"),
                eq("order-123"),
                any(com.microservices.pro.inventoryservice.events.InventoryReleasedEvent.class));
    }

    @Test
    void handlePaymentFailed_ignoresNonPaymentFailedEvents() {
        String json = "{\"orderId\":\"order-123\",\"eventType\":\"PaymentCompletedEvent\",\"transactionId\":\"tx-1\"}";

        handler.handlePaymentFailed(json);

        verifyNoInteractions(inventoryService);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void eventsCoverage() {
        InventoryReservationFailedEvent failEvent = new InventoryReservationFailedEvent("ord-1", "reason");
        assertThat(failEvent.orderId()).isEqualTo("ord-1");
        assertThat(failEvent.reason()).isEqualTo("reason");

        OrderConfirmedEvent confEvent = new OrderConfirmedEvent("ord-1", "tx-1");
        assertThat(confEvent.orderId()).isEqualTo("ord-1");
        assertThat(confEvent.transactionId()).isEqualTo("tx-1");

        PaymentCompletedEvent compEvent = new PaymentCompletedEvent("ord-1", "tx-1");
        assertThat(compEvent.orderId()).isEqualTo("ord-1");
        assertThat(compEvent.transactionId()).isEqualTo("tx-1");

        OrderCancelledEvent cancEvent = new OrderCancelledEvent("ord-1", "cancelled");
        assertThat(cancEvent.orderId()).isEqualTo("ord-1");
        assertThat(cancEvent.reason()).isEqualTo("cancelled");
    }
}
