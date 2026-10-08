package com.microservices.pro.orderservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OutboxPublisher — Session 22 / Phase 5 Transactional Outbox pattern.
 *
 * Polls the outbox_events table every second and publishes unpublished rows
 * to Kafka topic "order-events", then marks them as published with timestamp.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate    = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        if (kafkaTemplate == null) {
            return;
        }
        List<OutboxEvent> pending = outboxRepository.findUnpublished();
        if (pending.isEmpty()) return;

        log.debug("[OUTBOX] Publishing {} pending event(s)", pending.size());

        for (OutboxEvent event : pending) {
            try {
                // Synchronously wait for Kafka ack up to 5 seconds to ensure reliable delivery before marking
                kafkaTemplate.send("order-events", event.getAggregateId(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markPublished();
                outboxRepository.save(event);
                log.info("[OUTBOX] Published and marked: id={} orderId={} type={}",
                        event.getId(), event.getAggregateId(), event.getEventType());
            } catch (Exception e) {
                log.error("[OUTBOX] Failed to publish event id={} type={} — will retry on next poll",
                        event.getId(), event.getEventType(), e);
                // Do NOT mark as published — next poll will retry safely.
            }
        }
    }
}
