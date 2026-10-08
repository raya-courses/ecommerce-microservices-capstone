package com.microservices.pro.paymentservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PaymentFailedEvent — Session 7 / Phase 5.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent(
        String orderId,
        String reason,
        @JsonProperty("eventType") String eventType
) {
    public PaymentFailedEvent(String orderId, String reason) {
        this(orderId, reason, "PaymentFailedEvent");
    }
}
