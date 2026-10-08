package com.microservices.pro.orderservice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * OutboxEvent — Session 22 / Phase 5 Transactional Outbox pattern.
 *
 * Persisted in the SAME local transaction as the business entity (Order).
 * The OutboxPublisher polls this table and publishes unpublished rows to Kafka.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aggregateId;    // orderId

    @Column(nullable = false)
    private String aggregateType;  // "ORDER"

    @Column(nullable = false)
    private String eventType;      // "OrderPlacedEvent", "OrderConfirmedEvent", "OrderCancelledEvent"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;        // JSON-serialized event

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean published;

    @Column
    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(String aggregateId, String aggregateType, String eventType, String payload) {
        this.aggregateId   = aggregateId;
        this.aggregateType = (aggregateType != null && !aggregateType.isBlank()) ? aggregateType : "ORDER";
        this.eventType     = eventType;
        this.payload       = payload;
        this.createdAt     = Instant.now();
        this.published     = false;
    }

    public OutboxEvent(String aggregateId, String eventType, String payload) {
        this(aggregateId, "ORDER", eventType, payload);
    }

    public Long getId()                { return id; }
    public String getAggregateId()     { return aggregateId; }
    public String getAggregateType()   { return aggregateType; }
    public String getEventType()       { return eventType; }
    public String getPayload()         { return payload; }
    public Instant getCreatedAt()      { return createdAt; }
    public boolean isPublished()       { return published; }
    public Instant getPublishedAt()    { return publishedAt; }

    public void markPublished() {
        this.published   = true;
        this.publishedAt = Instant.now();
    }
}
