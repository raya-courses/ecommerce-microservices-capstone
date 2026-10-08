package com.microservices.pro.inventoryservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryReservedEvent(
        String orderId,
        String productId,
        int quantity,
        @JsonProperty("eventType") String eventType
) {
    public InventoryReservedEvent(String orderId, String productId, int quantity) {
        this(orderId, productId, quantity, "InventoryReservedEvent");
    }
}
