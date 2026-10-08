package com.microservices.pro.orderservice.analytics.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AnalyticsSummaryResponse(
        long totalOrders,
        long confirmedOrders,
        long cancelledOrders,
        long pendingOrders,
        double cancelledRatio,
        BigDecimal totalRevenue,
        Map<String, Long> ordersByStatus,
        List<HourlyOrdersDto> ordersPerHour,
        List<HourlyRevenueDto> revenuePerHour
) {}
