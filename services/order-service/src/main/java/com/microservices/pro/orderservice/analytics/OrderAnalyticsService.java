package com.microservices.pro.orderservice.analytics;

import com.microservices.pro.orderservice.analytics.dto.AnalyticsSummaryResponse;
import com.microservices.pro.orderservice.analytics.dto.HourlyOrdersDto;
import com.microservices.pro.orderservice.analytics.dto.HourlyRevenueDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OrderAnalyticsService — Read-side service layer for order analytics.
 */
@Service
public class OrderAnalyticsService {

    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00:00'Z'").withZone(ZoneOffset.UTC);

    @Autowired(required = false)
    private OrderAnalyticsRepository orderAnalyticsRepository;

    @Autowired(required = false)
    private HourlyOrderMetricRepository hourlyOrderMetricRepository;

    public OrderAnalyticsService() {
    }

    public OrderAnalyticsService(OrderAnalyticsRepository orderAnalyticsRepository,
                                 HourlyOrderMetricRepository hourlyOrderMetricRepository) {
        this.orderAnalyticsRepository = orderAnalyticsRepository;
        this.hourlyOrderMetricRepository = hourlyOrderMetricRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary() {
        if (orderAnalyticsRepository == null) {
            return new AnalyticsSummaryResponse(0, 0, 0, 0, 0.0, BigDecimal.ZERO, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());
        }

        long totalOrders = orderAnalyticsRepository.count();
        long confirmedOrders = orderAnalyticsRepository.countByStatus("CONFIRMED");
        long cancelledOrders = orderAnalyticsRepository.countByStatus("CANCELLED");
        long pendingOrders = orderAnalyticsRepository.countByStatus("PENDING");

        double cancelledRatio = totalOrders > 0
                ? Math.round(((double) cancelledOrders / totalOrders) * 1000.0) / 1000.0
                : 0.0;

        BigDecimal totalRevenue = orderAnalyticsRepository.calculateTotalConfirmedRevenue();
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }

        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        ordersByStatus.put("CONFIRMED", confirmedOrders);
        ordersByStatus.put("CANCELLED", cancelledOrders);
        ordersByStatus.put("PENDING", pendingOrders);

        // Compute hourly metrics from orderAnalyticsRepository items
        List<OrderAnalyticsItem> items = orderAnalyticsRepository.findAll();

        Map<String, List<OrderAnalyticsItem>> byHour = items.stream()
                .collect(Collectors.groupingBy(item -> HOUR_FORMATTER.format(item.getCreatedAt()), TreeMap::new, Collectors.toList()));

        List<HourlyOrdersDto> ordersPerHour = new ArrayList<>();
        List<HourlyRevenueDto> revenuePerHour = new ArrayList<>();

        for (Map.Entry<String, List<OrderAnalyticsItem>> entry : byHour.entrySet()) {
            String hour = entry.getKey();
            List<OrderAnalyticsItem> hourItems = entry.getValue();

            Map<String, Long> statusCounts = hourItems.stream()
                    .collect(Collectors.groupingBy(OrderAnalyticsItem::getStatus, Collectors.counting()));

            ordersPerHour.add(new HourlyOrdersDto(hour, hourItems.size(), statusCounts));

            BigDecimal hourRevenue = hourItems.stream()
                    .filter(i -> "CONFIRMED".equalsIgnoreCase(i.getStatus()))
                    .map(OrderAnalyticsItem::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            revenuePerHour.add(new HourlyRevenueDto(hour, hourRevenue));
        }

        return new AnalyticsSummaryResponse(
                totalOrders,
                confirmedOrders,
                cancelledOrders,
                pendingOrders,
                cancelledRatio,
                totalRevenue,
                ordersByStatus,
                ordersPerHour,
                revenuePerHour
        );
    }
}
