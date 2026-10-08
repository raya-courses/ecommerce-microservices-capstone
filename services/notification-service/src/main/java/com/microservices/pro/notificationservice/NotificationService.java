package com.microservices.pro.notificationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * NotificationService — Session 13 / Phase 5 Saga terminal event notifications.
 *
 * Listens on "order-events" for terminal order events:
 *   - OrderConfirmedEvent -> sends order confirmation
 *   - OrderCancelledEvent -> sends order cancellation alert
 *
 * Implements:
 *   - @RetryableTopic consumer retries with backoff
 *   - @DltHandler for dead-letter processing
 *   - Durable Redis-backed idempotency store
 *   - Typed Jackson parsing without fragile raw string checks
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationIdempotencyStore idempotencyStore;

    public NotificationService(NotificationIdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "true"
    )
    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void handleOrderEvent(ConsumerRecord<String, String> record) {
        String payload = record.value();
        String orderId = record.key();

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("eventType").asText("");

            if (orderId == null || orderId.isBlank()) {
                orderId = root.path("orderId").asText("");
            }

            boolean isConfirmed = "OrderConfirmedEvent".equalsIgnoreCase(eventType)
                    || "OrderConfirmed".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("transactionId") && !root.has("productId"));

            boolean isCancelled = "OrderCancelledEvent".equalsIgnoreCase(eventType)
                    || "OrderCancelled".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("reason") && !root.has("productId"));

            if (isConfirmed) {
                String idempotencyKey = orderId + ":CONFIRMED";
                if (!idempotencyStore.markIfNew(idempotencyKey, DEFAULT_TTL)) {
                    log.info("[NOTIFICATION] Duplicate notification suppressed for {}", idempotencyKey);
                    return;
                }
                sendOrderConfirmation(orderId);
            } else if (isCancelled) {
                String idempotencyKey = orderId + ":CANCELLED";
                if (!idempotencyStore.markIfNew(idempotencyKey, DEFAULT_TTL)) {
                    log.info("[NOTIFICATION] Duplicate notification suppressed for {}", idempotencyKey);
                    return;
                }
                String reason = root.path("reason").asText("Payment or reservation failure");
                sendOrderCancellation(orderId, reason);
            } else {
                log.debug("[NOTIFICATION] Ignoring non-terminal event on order-events for order: {}", orderId);
            }
        } catch (Exception e) {
            log.error("[NOTIFICATION] Error processing notification event for order: {}", orderId, e);
            throw new RuntimeException("Failed to process notification", e);
        }
    }

    @DltHandler
    public void handleDeadLetter(ConsumerRecord<String, String> record) {
        log.error("[NOTIFICATION] [DLT] Message exhausted all retries for order: {} — payload: {}",
                record.key(), record.value());
    }

    public boolean hasProcessed(String orderId, String type) {
        return idempotencyStore.hasProcessed(orderId + ":" + type);
    }

    private void sendOrderConfirmation(String orderId) {
        log.info("[NOTIFICATION] ✉ Order confirmation sent for order: {}", orderId);
    }

    private void sendOrderCancellation(String orderId, String reason) {
        log.warn("[NOTIFICATION] ⚠ Order cancellation alert sent for order: {} — reason: {}", orderId, reason);
    }
}
