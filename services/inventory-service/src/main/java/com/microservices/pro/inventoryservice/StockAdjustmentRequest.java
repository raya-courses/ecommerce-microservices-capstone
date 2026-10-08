package com.microservices.pro.inventoryservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StockAdjustmentRequest(
        int quantity
) {}
