package com.microservices.pro.inventoryservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedEvent(
        String orderId,
        String productId,
        int quantity,
        BigDecimal amount,
        String customerId,
        @JsonProperty("eventType") String eventType
) {
    public OrderPlacedEvent(String orderId, String productId, int quantity, BigDecimal amount, String customerId) {
        this(orderId, productId, quantity, amount, customerId, "OrderPlacedEvent");
    }
}
