package com.microservices.pro.paymentservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.UUID;

/**
 * PaymentController — Session 4 (original) + Session 22 (idempotency).
 *
 * Session 22 change: every POST /api/payments can include an
 * Idempotency-Key header. The controller checks the header before
 * processing:
 *   - Key already in DB, status COMPLETED: return cached response (200)
 *   - Key already in DB, status PROCESSING: return 202 Accepted
 *   - Key not in DB: process and store
 *
 * Closes Session 4 Technical Debt:
 *   "No idempotency on payment retry — if Resilience4j @Retry fires the
 *    same payment request twice, the customer is charged twice."
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Value("${payment.failure-rate:0.5}")
    private double failureRate;

    @Value("${payment.delay-ms:0}")
    private long delayMs;

    private final Random random = new Random();

    @Autowired(required = false)
    private IdempotencyRepository idempotencyRepository;

    public PaymentController() {}

    public PaymentController(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestBody PaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
            throws InterruptedException {

        // ── Idempotency check (Session 22) ────────────────────────────────
        if (idempotencyKey != null && !idempotencyKey.isBlank() && idempotencyRepository != null) {
            var existing = idempotencyRepository.findById(idempotencyKey);
            if (existing.isPresent()) {
                IdempotencyRecord record = existing.get();
                log.info("[IDEMPOTENCY] Duplicate request detected for key={} status={}",
                        idempotencyKey, record.getStatus());
                if ("COMPLETED".equals(record.getStatus())) {
                    return ResponseEntity.ok(
                            new PaymentResponse(record.getResponsePayload(), "COMPLETED", request.amount()));
                }
                return ResponseEntity.accepted()
                        .body(new PaymentResponse(null, "PROCESSING", request.amount()));
            }
            // New key — register as PROCESSING before doing any work
            String orderId = request.orderId() != null ? request.orderId() : UUID.randomUUID().toString();
            idempotencyRepository.save(new IdempotencyRecord(idempotencyKey, orderId));
        }

        // ── Original Session 4 payment logic ──────────────────────────────
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }

        if (random.nextDouble() < failureRate) {
            if (idempotencyKey != null && idempotencyRepository != null) {
                idempotencyRepository.findById(idempotencyKey)
                        .ifPresent(r -> { r.fail("Simulated failure"); idempotencyRepository.save(r); });
            }
            log.warn("[PAYMENT] Failed for orderId={}", request.orderId());
            throw new RuntimeException("Payment gateway timeout");
        }

        String transactionId = UUID.randomUUID().toString();
        if (idempotencyKey != null && idempotencyRepository != null) {
            idempotencyRepository.findById(idempotencyKey)
                    .ifPresent(r -> { r.complete(transactionId); idempotencyRepository.save(r); });
        }
        log.info("[PAYMENT] Completed orderId={} txId={}", request.orderId(), transactionId);
        return ResponseEntity.ok(new PaymentResponse(
                transactionId,
                "APPROVED",
                request.amount()
        ));
    }
}
