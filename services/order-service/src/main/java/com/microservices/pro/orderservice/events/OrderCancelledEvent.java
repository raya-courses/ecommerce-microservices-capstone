package com.microservices.pro.orderservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OrderCancelledEvent — Session 7 / Phase 5 terminal compensation-path event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledEvent(
        String orderId,
        String reason,
        String customerId,
        @JsonProperty("eventType") String eventType
) {
    public OrderCancelledEvent(String orderId, String reason) {
        this(orderId, reason, null, "OrderCancelledEvent");
    }

    public OrderCancelledEvent(String orderId, String reason, String customerId) {
        this(orderId, reason, customerId, "OrderCancelledEvent");
    }
}
