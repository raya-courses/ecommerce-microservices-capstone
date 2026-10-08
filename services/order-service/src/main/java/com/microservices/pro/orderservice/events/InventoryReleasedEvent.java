package com.microservices.pro.orderservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * InventoryReleasedEvent — Session 7 / Phase 5.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryReleasedEvent(
        String orderId,
        @JsonProperty("eventType") String eventType
) {
    public InventoryReleasedEvent(String orderId) {
        this(orderId, "InventoryReleasedEvent");
    }
}
