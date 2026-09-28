package com.microservices.pro.paymentservice;

import java.math.BigDecimal;

/**
 * PaymentRequest — Session 4 (original) + Session 22 (orderId for idempotency).
 */
public record PaymentRequest(BigDecimal amount, String orderId) {
    public PaymentRequest(BigDecimal amount) {
        this(amount, null);
    }
}
