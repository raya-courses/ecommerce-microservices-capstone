package com.microservices.pro.orderservice.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * AnalyticsProcessedEvent — Persistent idempotency log for processed analytics events.
 */
@Entity
@Table(name = "analytics_processed_events")
public class AnalyticsProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected AnalyticsProcessedEvent() {
        // JPA
    }

    public AnalyticsProcessedEvent(String eventId, String orderId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
