package com.microservices.pro.orderservice.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * OrderAnalyticsMetrics — Micrometer Prometheus metrics for order analytics dashboard.
 */
@Component
public class OrderAnalyticsMetrics {

    private final MeterRegistry meterRegistry;

    public OrderAnalyticsMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordOrderPlaced() {
        Counter.builder("ecommerce_orders_total")
                .tag("status", "PENDING")
                .description("Total orders by status")
                .register(meterRegistry)
                .increment();
    }

    public void recordOrderConfirmed(BigDecimal revenue) {
        Counter.builder("ecommerce_orders_total")
                .tag("status", "CONFIRMED")
                .description("Total orders by status")
                .register(meterRegistry)
                .increment();

        Counter.builder("ecommerce_orders_confirmed_total")
                .description("Total confirmed orders")
                .register(meterRegistry)
                .increment();

        if (revenue != null && revenue.compareTo(BigDecimal.ZERO) > 0) {
            Counter.builder("ecommerce_order_revenue_total")
                    .description("Total order revenue")
                    .register(meterRegistry)
                    .increment(revenue.doubleValue());
        }
    }

    public void recordOrderCancelled(String reason) {
        Counter.builder("ecommerce_orders_total")
                .tag("status", "CANCELLED")
                .description("Total orders by status")
                .register(meterRegistry)
                .increment();

        Counter.builder("ecommerce_orders_cancelled_total")
                .description("Total cancelled orders")
                .register(meterRegistry)
                .increment();

        Counter.builder("ecommerce_saga_failures_total")
                .description("Total saga failures / cancellations")
                .register(meterRegistry)
                .increment();
    }
}
