package com.microservices.pro.paymentservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * PaymentController — Session 4 (original) + Session 22 (idempotency) + Phase 4 Capstone alignment.
 *
 * Endpoints:
 *   POST /api/v1/payments            - process payment with persistent idempotency
 *   POST /api/v1/payments/{id}/refund - refund payment and persist status
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Value("${payment.failure-rate:0.5}")
    private double failureRate;

    @Value("${payment.delay-ms:0}")
    private long delayMs;

    private final Random random = new Random();

    @Autowired(required = false)
    private IdempotencyRepository idempotencyRepository;

    @Autowired(required = false)
    private PaymentRepository paymentRepository;

    public PaymentController() {}

    public PaymentController(IdempotencyRepository idempotencyRepository, PaymentRepository paymentRepository) {
        this.idempotencyRepository = idempotencyRepository;
        this.paymentRepository = paymentRepository;
    }

    public PaymentController(IdempotencyRepository idempotencyRepository) {
        this(idempotencyRepository, null);
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestBody PaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
            throws InterruptedException {

        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : ("order-" + request.orderId());

        // ── Idempotency check ─────────────────────────────────────────
        if (idempotencyRepository != null) {
            var existing = idempotencyRepository.findById(effectiveKey);
            if (existing.isPresent()) {
                IdempotencyRecord record = existing.get();
                log.info("[IDEMPOTENCY] Duplicate request detected for key={} status={}",
                        effectiveKey, record.getStatus());
                if ("COMPLETED".equals(record.getStatus())) {
                    return ResponseEntity.ok(
                            new PaymentResponse(record.getResponsePayload(), "COMPLETED", request.amount()));
                }
                return ResponseEntity.accepted()
                        .body(new PaymentResponse(null, "PROCESSING", request.amount()));
            }
            // Register as PROCESSING before doing any work
            String orderId = request.orderId() != null ? request.orderId() : UUID.randomUUID().toString();
            idempotencyRepository.save(new IdempotencyRecord(effectiveKey, orderId));
        }

        // ── Simulated delay ──────────────────────────────────────────
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }

        // ── Simulated failure ────────────────────────────────────────
        if (random.nextDouble() < failureRate) {
            if (idempotencyRepository != null) {
                idempotencyRepository.findById(effectiveKey)
                        .ifPresent(r -> { r.fail("Simulated failure"); idempotencyRepository.save(r); });
            }
            if (paymentRepository != null) {
                Payment failed = new Payment(
                        UUID.randomUUID().toString(),
                        request.orderId() != null ? request.orderId() : "unknown",
                        request.amount(),
                        "FAILED",
                        null
                );
                paymentRepository.save(failed);
            }
            log.warn("[PAYMENT] Failed for orderId={}", request.orderId());
            throw new RuntimeException("Payment gateway timeout");
        }

        // ── Success path ─────────────────────────────────────────────
        String transactionId = UUID.randomUUID().toString();
        if (idempotencyRepository != null) {
            idempotencyRepository.findById(effectiveKey)
                    .ifPresent(r -> { r.complete(transactionId); idempotencyRepository.save(r); });
        }
        if (paymentRepository != null) {
            Payment completed = new Payment(
                    UUID.randomUUID().toString(),
                    request.orderId() != null ? request.orderId() : "unknown",
                    request.amount(),
                    "COMPLETED",
                    transactionId
            );
            paymentRepository.save(completed);
        }

        log.info("[PAYMENT] Completed orderId={} txId={}", request.orderId(), transactionId);
        return ResponseEntity.ok(new PaymentResponse(
                transactionId,
                "APPROVED",
                request.amount()
        ));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable String id) {
        if (paymentRepository == null) {
            return ResponseEntity.ok(new PaymentResponse(UUID.randomUUID().toString(), "REFUNDED", null));
        }

        Optional<Payment> paymentOpt = paymentRepository.findById(id)
                .or(() -> paymentRepository.findByOrderId(id))
                .or(() -> paymentRepository.findByTransactionId(id));

        if (paymentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Payment payment = paymentOpt.get();
        payment.setStatus("REFUNDED");
        paymentRepository.save(payment);

        log.info("[PAYMENT] Refunded payment id={} orderId={}", payment.getId(), payment.getOrderId());
        return ResponseEntity.ok(new PaymentResponse(
                payment.getTransactionId(),
                "REFUNDED",
                payment.getAmount()
        ));
    }
}
