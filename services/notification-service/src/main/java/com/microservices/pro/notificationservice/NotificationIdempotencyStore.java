package com.microservices.pro.notificationservice;

import java.time.Duration;

/**
 * Contract for durable notification deduplication.
 * Protects against duplicate email/SMS dispatch across process restarts and horizontal replicas.
 */
public interface NotificationIdempotencyStore {

    /**
     * Atomically marks the key as processed if not already present.
     *
     * @param idempotencyKey unique notification identifier (e.g. orderId:CONFIRMED)
     * @param ttl duration to retain the key
     * @return true if newly registered, false if already processed (duplicate)
     */
    boolean markIfNew(String idempotencyKey, Duration ttl);

    /**
     * Checks if the key was previously marked as processed.
     */
    boolean hasProcessed(String idempotencyKey);
}
