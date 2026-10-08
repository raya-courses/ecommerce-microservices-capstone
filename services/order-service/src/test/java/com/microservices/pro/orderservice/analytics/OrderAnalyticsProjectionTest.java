package com.microservices.pro.orderservice.analytics;

import com.microservices.pro.orderservice.events.OrderCancelledEvent;
import com.microservices.pro.orderservice.events.OrderConfirmedEvent;
import com.microservices.pro.orderservice.events.OrderPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAnalyticsProjectionTest {

    @Mock
    private OrderAnalyticsRepository orderAnalyticsRepository;

    @Mock
    private AnalyticsProcessedEventRepository analyticsProcessedEventRepository;

    @Mock
    private HourlyOrderMetricRepository hourlyOrderMetricRepository;

    @Mock
    private OrderAnalyticsMetrics metrics;

    private OrderAnalyticsProjection projection;

    @BeforeEach
    void setUp() {
        projection = new OrderAnalyticsProjection(
                orderAnalyticsRepository,
                analyticsProcessedEventRepository,
                hourlyOrderMetricRepository,
                metrics
        );
    }

    @Test
    void processOrderPlaced_persistsReadModel_andIncrementsMetrics() {
        when(analyticsProcessedEventRepository.existsById("ord-1:OrderPlaced")).thenReturn(false);
        when(orderAnalyticsRepository.findById("ord-1")).thenReturn(Optional.empty());
        when(hourlyOrderMetricRepository.findByHourBucketAndStatus(any(), eq("PENDING"))).thenReturn(Optional.empty());

        OrderPlacedEvent event = new OrderPlacedEvent("ord-1", "PROD-1", 2, new BigDecimal("150.00"), "cust-1");

        projection.processOrderPlaced(event);

        verify(analyticsProcessedEventRepository).save(any(AnalyticsProcessedEvent.class));
        ArgumentCaptor<OrderAnalyticsItem> captor = ArgumentCaptor.forClass(OrderAnalyticsItem.class);
        verify(orderAnalyticsRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("150.00");

        verify(hourlyOrderMetricRepository).save(any(HourlyOrderMetric.class));
        verify(metrics).recordOrderPlaced();
    }

    @Test
    void processOrderPlaced_duplicateEventSuppressed() {
        when(analyticsProcessedEventRepository.existsById("ord-1:OrderPlaced")).thenReturn(true);

        OrderPlacedEvent event = new OrderPlacedEvent("ord-1", "PROD-1", 2, new BigDecimal("150.00"), "cust-1");

        projection.processOrderPlaced(event);

        verify(analyticsProcessedEventRepository, never()).save(any());
        verify(orderAnalyticsRepository, never()).save(any());
        verify(metrics, never()).recordOrderPlaced();
    }

    @Test
    void processOrderConfirmed_updatesStatusAndRevenue_andIncrementsMetrics() {
        when(analyticsProcessedEventRepository.existsById("ord-1:OrderConfirmed")).thenReturn(false);
        OrderAnalyticsItem existing = new OrderAnalyticsItem("ord-1", "PROD-1", "cust-1", new BigDecimal("250.00"), "PENDING", Instant.now(), Instant.now());
        when(orderAnalyticsRepository.findById("ord-1")).thenReturn(Optional.of(existing));
        when(hourlyOrderMetricRepository.findByHourBucketAndStatus(any(), eq("CONFIRMED"))).thenReturn(Optional.empty());

        OrderConfirmedEvent event = new OrderConfirmedEvent("ord-1", "tx-999", "cust-1");

        projection.processOrderConfirmed(event);

        verify(analyticsProcessedEventRepository).save(any(AnalyticsProcessedEvent.class));
        verify(orderAnalyticsRepository).save(existing);
        assertThat(existing.getStatus()).isEqualTo("CONFIRMED");

        verify(hourlyOrderMetricRepository).save(any(HourlyOrderMetric.class));
        verify(metrics).recordOrderConfirmed(new BigDecimal("250.00"));
    }

    @Test
    void processOrderConfirmed_duplicateEventSuppressed() {
        when(analyticsProcessedEventRepository.existsById("ord-1:OrderConfirmed")).thenReturn(true);

        OrderConfirmedEvent event = new OrderConfirmedEvent("ord-1", "tx-999", "cust-1");

        projection.processOrderConfirmed(event);

        verify(analyticsProcessedEventRepository, never()).save(any());
        verify(orderAnalyticsRepository, never()).save(any());
        verify(metrics, never()).recordOrderConfirmed(any());
    }

    @Test
    void processOrderCancelled_updatesStatus_andIncrementsSagaFailureMetric() {
        when(analyticsProcessedEventRepository.existsById("ord-1:OrderCancelled")).thenReturn(false);
        OrderAnalyticsItem existing = new OrderAnalyticsItem("ord-1", "PROD-1", "cust-1", new BigDecimal("100.00"), "PENDING", Instant.now(), Instant.now());
        when(orderAnalyticsRepository.findById("ord-1")).thenReturn(Optional.of(existing));
        when(hourlyOrderMetricRepository.findByHourBucketAndStatus(any(), eq("CANCELLED"))).thenReturn(Optional.empty());

        OrderCancelledEvent event = new OrderCancelledEvent("ord-1", "Inventory out of stock", "cust-1");

        projection.processOrderCancelled(event);

        verify(analyticsProcessedEventRepository).save(any(AnalyticsProcessedEvent.class));
        verify(orderAnalyticsRepository).save(existing);
        assertThat(existing.getStatus()).isEqualTo("CANCELLED");

        verify(metrics).recordOrderCancelled("Inventory out of stock");
    }

    @Test
    void processOrderCancelled_duplicateEventSuppressed() {
        when(analyticsProcessedEventRepository.existsById("ord-1:OrderCancelled")).thenReturn(true);

        OrderCancelledEvent event = new OrderCancelledEvent("ord-1", "Inventory out of stock", "cust-1");

        projection.processOrderCancelled(event);

        verify(analyticsProcessedEventRepository, never()).save(any());
        verify(orderAnalyticsRepository, never()).save(any());
        verify(metrics, never()).recordOrderCancelled(any());
    }

    @Test
    void onOrderEvent_routesOrderPlacedJsonCorrectly() {
        String json = "{\"orderId\":\"ord-json\",\"productId\":\"P-1\",\"quantity\":1,\"amount\":80.00,\"customerId\":\"c1\",\"eventType\":\"OrderPlacedEvent\"}";
        when(analyticsProcessedEventRepository.existsById("ord-json:OrderPlaced")).thenReturn(false);
        when(orderAnalyticsRepository.findById("ord-json")).thenReturn(Optional.empty());
        when(hourlyOrderMetricRepository.findByHourBucketAndStatus(any(), eq("PENDING"))).thenReturn(Optional.empty());

        projection.onOrderEvent(json);

        verify(orderAnalyticsRepository).save(any(OrderAnalyticsItem.class));
        verify(metrics).recordOrderPlaced();
    }

    @Test
    void onOrderEvent_routesOrderConfirmedJsonCorrectly() {
        String json = "{\"orderId\":\"ord-json\",\"transactionId\":\"tx-123\",\"eventType\":\"OrderConfirmedEvent\"}";
        when(analyticsProcessedEventRepository.existsById("ord-json:OrderConfirmed")).thenReturn(false);
        when(orderAnalyticsRepository.findById("ord-json")).thenReturn(Optional.empty());
        when(hourlyOrderMetricRepository.findByHourBucketAndStatus(any(), eq("CONFIRMED"))).thenReturn(Optional.empty());

        projection.onOrderEvent(json);

        verify(orderAnalyticsRepository).save(any(OrderAnalyticsItem.class));
        verify(metrics).recordOrderConfirmed(any());
    }

    @Test
    void onOrderEvent_routesOrderCancelledJsonCorrectly() {
        String json = "{\"orderId\":\"ord-json\",\"reason\":\"Card declined\",\"eventType\":\"OrderCancelledEvent\"}";
        when(analyticsProcessedEventRepository.existsById("ord-json:OrderCancelled")).thenReturn(false);
        when(orderAnalyticsRepository.findById("ord-json")).thenReturn(Optional.empty());
        when(hourlyOrderMetricRepository.findByHourBucketAndStatus(any(), eq("CANCELLED"))).thenReturn(Optional.empty());

        projection.onOrderEvent(json);

        verify(orderAnalyticsRepository).save(any(OrderAnalyticsItem.class));
        verify(metrics).recordOrderCancelled("Card declined");
    }
}
