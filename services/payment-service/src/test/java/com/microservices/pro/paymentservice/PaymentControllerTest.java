package com.microservices.pro.paymentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@TestPropertySource(properties = {
        "payment.failure-rate=0.0",
        "payment.delay-ms=0"
})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IdempotencyRepository idempotencyRepository;

    @MockBean
    private PaymentRepository paymentRepository;

    @Test
    void processPayment_whenZeroFailureRate_returnsApproved() throws Exception {
        PaymentRequest request = new PaymentRequest(new BigDecimal("99.99"), "ORD-1");

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amount").value(99.99))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void processPayment_whenDuplicateCompletedKey_returnsCachedResponse() throws Exception {
        PaymentRequest request = new PaymentRequest(new BigDecimal("99.99"), "ORD-1");
        IdempotencyRecord cached = new IdempotencyRecord("key-123", "ORD-1");
        cached.complete("tx-existing-123");

        when(idempotencyRepository.findById("key-123")).thenReturn(Optional.of(cached));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("tx-existing-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void processPayment_whenDuplicateProcessingKey_returnsAccepted() throws Exception {
        PaymentRequest request = new PaymentRequest(new BigDecimal("99.99"), "ORD-1");
        IdempotencyRecord inFlight = new IdempotencyRecord("key-processing", "ORD-1");

        when(idempotencyRepository.findById("key-processing")).thenReturn(Optional.of(inFlight));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "key-processing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void refundPayment_whenPaymentExists_returnsRefunded() throws Exception {
        Payment payment = new Payment("PAY-1", "ORD-1", new BigDecimal("99.99"), "COMPLETED", "TX-1");
        when(paymentRepository.findById("PAY-1")).thenReturn(Optional.of(payment));

        mockMvc.perform(post("/api/v1/payments/PAY-1/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TX-1"))
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        verify(paymentRepository).save(payment);
    }

    @Test
    void refundPayment_whenNotFound_returns404() throws Exception {
        when(paymentRepository.findById("PAY-999")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/payments/PAY-999/refund"))
                .andExpect(status().isNotFound());
    }
}
