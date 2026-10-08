package com.microservices.pro.orderservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * InventoryReservationFailedEvent — Session 7 / Phase 5.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryReservationFailedEvent(
        String orderId,
        String reason,
        @JsonProperty("eventType") String eventType
) {
    public InventoryReservationFailedEvent(String orderId, String reason) {
        this(orderId, reason, "InventoryReservationFailedEvent");
    }
}
