package com.microservices.pro.notificationservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Durable Redis-backed notification idempotency store.
 * Uses atomic set-if-absent (SETNX) semantics with a configurable TTL.
 *
 * Guarantees:
 * - Duplicate OrderConfirmedEvent does not send twice.
 * - Duplicate OrderCancelledEvent does not send twice.
 * - Safe across JVM/container restart.
 * - Safe with multiple notification-service replicas (shared distributed state).
 * - Handles Kafka at-least-once redelivery.
 */
@Component
public class RedisNotificationIdempotencyStore implements NotificationIdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisNotificationIdempotencyStore.class);
    private static final String KEY_PREFIX = "notification:idempotency:";

    private final StringRedisTemplate redisTemplate;

    public RedisNotificationIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean markIfNew(String idempotencyKey, Duration ttl) {
        String key = KEY_PREFIX + idempotencyKey;
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "PROCESSED", ttl);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.error("[IDEMPOTENCY] Error checking idempotency key {} in Redis", key, e);
            throw new RuntimeException("Redis idempotency store unavailable", e);
        }
    }

    @Override
    public boolean hasProcessed(String idempotencyKey) {
        String key = KEY_PREFIX + idempotencyKey;
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("[IDEMPOTENCY] Error checking if key {} exists in Redis: {}", key, e.getMessage());
            return false;
        }
    }
}
