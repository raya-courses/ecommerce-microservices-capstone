package com.microservices.pro.orderservice.analytics.dto;

import java.util.Map;

public record HourlyOrdersDto(
        String hour,
        long count,
        Map<String, Long> statusCounts
) {}
