package com.microservices.pro.inventoryservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * PaymentFailedEvent — Session 7.
 * Published by Payment Service to "payment-events" when payment fails.
 * Triggers compensation: Inventory releases the reservation, Order is cancelled.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent(String orderId, String reason) {}
