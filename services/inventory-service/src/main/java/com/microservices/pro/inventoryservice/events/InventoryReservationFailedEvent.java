package com.microservices.pro.inventoryservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
