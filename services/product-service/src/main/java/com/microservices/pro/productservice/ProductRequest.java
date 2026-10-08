package com.microservices.pro.productservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductRequest(
        String name,
        String description,
        BigDecimal price,
        String category
) {}
