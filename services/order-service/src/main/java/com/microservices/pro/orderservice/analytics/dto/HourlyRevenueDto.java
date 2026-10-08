package com.microservices.pro.orderservice.analytics.dto;

import java.math.BigDecimal;

public record HourlyRevenueDto(
        String hour,
        BigDecimal revenue
) {}
