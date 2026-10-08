package com.microservices.pro.paymentservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PaymentCompletedEvent — Session 7 / Phase 5.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCompletedEvent(
        String orderId,
        String transactionId,
        @JsonProperty("eventType") String eventType
) {
    public PaymentCompletedEvent(String orderId, String transactionId) {
        this(orderId, transactionId, "PaymentCompletedEvent");
    }
}
