package com.microservices.pro.paymentservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.pro.paymentservice.events.InventoryReservedEvent;
import com.microservices.pro.paymentservice.events.PaymentCompletedEvent;
import com.microservices.pro.paymentservice.events.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PaymentSagaHandler — Session 7 / Phase 5 Saga Choreography & Persistent Idempotency.
 *
 * Consumer group "payment-service" on "inventory-events".
 * Handles InventoryReserved events with persistent idempotency.
 */
@Service
public class PaymentSagaHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PaymentProcessor paymentProcessor;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired(required = false)
    private PaymentRepository paymentRepository;

    @Autowired(required = false)
    private IdempotencyRepository idempotencyRepository;

    public PaymentSagaHandler() {}

    public PaymentSagaHandler(PaymentProcessor paymentProcessor,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              PaymentRepository paymentRepository,
                              IdempotencyRepository idempotencyRepository) {
        this.paymentProcessor      = paymentProcessor;
        this.kafkaTemplate         = kafkaTemplate;
        this.paymentRepository     = paymentRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    /**
     * Step 3 of the Saga: process payment once inventory is reserved.
     */
    @KafkaListener(topics = "inventory-events", groupId = "payment-service")
    public void handleInventoryReserved(String rawEvent) {
        try {
            JsonNode root = objectMapper.readTree(rawEvent);
            String eventType = root.path("eventType").asText("");

            boolean isInventoryReserved = "InventoryReservedEvent".equalsIgnoreCase(eventType)
                    || "InventoryReserved".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("productId") && root.has("quantity") && !root.has("reason"));

            if (!isInventoryReserved) {
                log.debug("[SAGA] Ignored non-InventoryReserved event on inventory-events: {}", rawEvent);
                return;
            }

            InventoryReservedEvent event = objectMapper.treeToValue(root, InventoryReservedEvent.class);
            processPaymentForOrder(event);
        } catch (Exception e) {
            log.error("[SAGA] Failed to process inventory event: {}", rawEvent, e);
            throw new RuntimeException("Error processing inventory event", e);
        }
    }

    public void processPaymentForOrder(InventoryReservedEvent event) {
        log.info("[SAGA] Processing payment for order: {}", event.orderId());

        // Idempotency check: if payment for this order already completed or exists
        if (paymentRepository != null) {
            var existingPayment = paymentRepository.findByOrderId(event.orderId());
            if (existingPayment.isPresent()) {
                Payment payment = existingPayment.get();
                if ("COMPLETED".equals(payment.getStatus())) {
                    log.info("[SAGA] Duplicate event: payment already COMPLETED for order: {}", event.orderId());
                    kafkaTemplate.send("payment-events", event.orderId(),
                            new PaymentCompletedEvent(event.orderId(), payment.getTransactionId()));
                    return;
                }
            }
        }

        try {
            String txId = paymentProcessor.processPayment(event.orderId());
            if (paymentRepository != null) {
                Payment payment = new Payment(
                        UUID.randomUUID().toString(),
                        event.orderId(),
                        BigDecimal.valueOf(100.00),
                        "COMPLETED",
                        txId
                );
                paymentRepository.save(payment);
            }
            if (idempotencyRepository != null) {
                IdempotencyRecord record = new IdempotencyRecord("order-" + event.orderId(), event.orderId());
                record.complete(txId);
                idempotencyRepository.save(record);
            }
            kafkaTemplate.send("payment-events", event.orderId(),
                    new PaymentCompletedEvent(event.orderId(), txId));
            log.info("[SAGA] Payment COMPLETED for order: {}", event.orderId());
        } catch (PaymentException e) {
            if (paymentRepository != null) {
                Payment failedPayment = new Payment(
                        UUID.randomUUID().toString(),
                        event.orderId(),
                        BigDecimal.valueOf(100.00),
                        "FAILED",
                        null
                );
                paymentRepository.save(failedPayment);
            }
            kafkaTemplate.send("payment-events", event.orderId(),
                    new PaymentFailedEvent(event.orderId(), e.getMessage()));
            log.warn("[SAGA] Payment FAILED for order: {} — triggering compensation", event.orderId());
        }
    }
}
