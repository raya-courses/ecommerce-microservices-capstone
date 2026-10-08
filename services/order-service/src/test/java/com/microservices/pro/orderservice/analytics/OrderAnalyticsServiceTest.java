package com.microservices.pro.orderservice.analytics;

import com.microservices.pro.orderservice.analytics.dto.AnalyticsSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAnalyticsServiceTest {

    @Mock
    private OrderAnalyticsRepository orderAnalyticsRepository;

    @Mock
    private HourlyOrderMetricRepository hourlyOrderMetricRepository;

    private OrderAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new OrderAnalyticsService(orderAnalyticsRepository, hourlyOrderMetricRepository);
    }

    @Test
    void getSummary_returnsCalculatedSummaryWithCorrectTotalsAndRatios() {
        when(orderAnalyticsRepository.count()).thenReturn(100L);
        when(orderAnalyticsRepository.countByStatus("CONFIRMED")).thenReturn(75L);
        when(orderAnalyticsRepository.countByStatus("CANCELLED")).thenReturn(15L);
        when(orderAnalyticsRepository.countByStatus("PENDING")).thenReturn(10L);
        when(orderAnalyticsRepository.calculateTotalConfirmedRevenue()).thenReturn(new BigDecimal("15000.00"));

        Instant time1 = Instant.parse("2026-09-29T10:15:30Z");
        Instant time2 = Instant.parse("2026-09-29T10:45:00Z");
        Instant time3 = Instant.parse("2026-09-29T11:05:00Z");

        OrderAnalyticsItem item1 = new OrderAnalyticsItem("ord-1", "PROD-1", "cust-1", new BigDecimal("100.00"), "CONFIRMED", time1, time1);
        OrderAnalyticsItem item2 = new OrderAnalyticsItem("ord-2", "PROD-2", "cust-2", new BigDecimal("50.00"), "CANCELLED", time2, time2);
        OrderAnalyticsItem item3 = new OrderAnalyticsItem("ord-3", "PROD-3", "cust-3", new BigDecimal("200.00"), "CONFIRMED", time3, time3);

        when(orderAnalyticsRepository.findAll()).thenReturn(List.of(item1, item2, item3));

        AnalyticsSummaryResponse summary = service.getSummary();

        assertThat(summary.totalOrders()).isEqualTo(100L);
        assertThat(summary.confirmedOrders()).isEqualTo(75L);
        assertThat(summary.cancelledOrders()).isEqualTo(15L);
        assertThat(summary.pendingOrders()).isEqualTo(10L);
        assertThat(summary.cancelledRatio()).isEqualTo(0.15); // 15 / 100
        assertThat(summary.totalRevenue()).isEqualByComparingTo("15000.00");
        assertThat(summary.ordersByStatus()).containsEntry("CONFIRMED", 75L)
                .containsEntry("CANCELLED", 15L)
                .containsEntry("PENDING", 10L);

        // Hourly breakdown checks
        assertThat(summary.ordersPerHour()).hasSize(2);
        assertThat(summary.ordersPerHour().get(0).hour()).isEqualTo("2026-09-29T10:00:00Z");
        assertThat(summary.ordersPerHour().get(0).count()).isEqualTo(2);
        assertThat(summary.ordersPerHour().get(1).hour()).isEqualTo("2026-09-29T11:00:00Z");
        assertThat(summary.ordersPerHour().get(1).count()).isEqualTo(1);

        assertThat(summary.revenuePerHour()).hasSize(2);
        assertThat(summary.revenuePerHour().get(0).revenue()).isEqualByComparingTo("100.00");
        assertThat(summary.revenuePerHour().get(1).revenue()).isEqualByComparingTo("200.00");
    }

    @Test
    void getSummary_handlesEmptyRepositoryGracefully() {
        when(orderAnalyticsRepository.count()).thenReturn(0L);
        when(orderAnalyticsRepository.countByStatus("CONFIRMED")).thenReturn(0L);
        when(orderAnalyticsRepository.countByStatus("CANCELLED")).thenReturn(0L);
        when(orderAnalyticsRepository.countByStatus("PENDING")).thenReturn(0L);
        when(orderAnalyticsRepository.calculateTotalConfirmedRevenue()).thenReturn(null);
        when(orderAnalyticsRepository.findAll()).thenReturn(List.of());

        AnalyticsSummaryResponse summary = service.getSummary();

        assertThat(summary.totalOrders()).isZero();
        assertThat(summary.confirmedOrders()).isZero();
        assertThat(summary.cancelledOrders()).isZero();
        assertThat(summary.pendingOrders()).isZero();
        assertThat(summary.cancelledRatio()).isEqualTo(0.0);
        assertThat(summary.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.ordersPerHour()).isEmpty();
        assertThat(summary.revenuePerHour()).isEmpty();
    }
}
