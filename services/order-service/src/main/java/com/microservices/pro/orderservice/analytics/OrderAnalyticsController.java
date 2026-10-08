package com.microservices.pro.orderservice.analytics;

import com.microservices.pro.orderservice.analytics.dto.AnalyticsSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OrderAnalyticsController — ADMIN REST endpoint for order analytics.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class OrderAnalyticsController {

    private final OrderAnalyticsService orderAnalyticsService;

    public OrderAnalyticsController(OrderAnalyticsService orderAnalyticsService) {
        this.orderAnalyticsService = orderAnalyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary() {
        AnalyticsSummaryResponse summary = orderAnalyticsService.getSummary();
        return ResponseEntity.ok(summary);
    }
}
