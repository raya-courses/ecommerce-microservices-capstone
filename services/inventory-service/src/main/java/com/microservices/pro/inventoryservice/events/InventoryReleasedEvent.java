package com.microservices.pro.inventoryservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryReleasedEvent(
        String orderId,
        @JsonProperty("eventType") String eventType
) {
    public InventoryReleasedEvent(String orderId) {
        this(orderId, "InventoryReleasedEvent");
    }
}
