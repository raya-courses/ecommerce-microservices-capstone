package com.microservices.pro.orderservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OrderConfirmedEvent — Session 7 / Phase 5 terminal happy-path event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderConfirmedEvent(
        String orderId,
        String transactionId,
        String customerId,
        @JsonProperty("eventType") String eventType
) {
    public OrderConfirmedEvent(String orderId, String transactionId) {
        this(orderId, transactionId, null, "OrderConfirmedEvent");
    }

    public OrderConfirmedEvent(String orderId, String transactionId, String customerId) {
        this(orderId, transactionId, customerId, "OrderConfirmedEvent");
    }
}
