package com.microservices.pro.notificationservice;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationIdempotencyStore idempotencyStore;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(idempotencyStore);
    }

    @Test
    void handleOrderEvent_processesConfirmedEvent() {
        when(idempotencyStore.markIfNew(eq("order-123:CONFIRMED"), any(Duration.class))).thenReturn(true);
        when(idempotencyStore.hasProcessed("order-123:CONFIRMED")).thenReturn(true);

        String payload = "{\"orderId\":\"order-123\",\"transactionId\":\"TX-101\",\"eventType\":\"OrderConfirmedEvent\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, "order-123", payload);

        notificationService.handleOrderEvent(record);

        assertThat(notificationService.hasProcessed("order-123", "CONFIRMED")).isTrue();
        verify(idempotencyStore).markIfNew(eq("order-123:CONFIRMED"), any(Duration.class));
    }

    @Test
    void handleOrderEvent_processesCancelledEvent() {
        when(idempotencyStore.markIfNew(eq("order-123:CANCELLED"), any(Duration.class))).thenReturn(true);
        when(idempotencyStore.hasProcessed("order-123:CANCELLED")).thenReturn(true);

        String payload = "{\"orderId\":\"order-123\",\"reason\":\"Stock unavailable\",\"eventType\":\"OrderCancelledEvent\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, "order-123", payload);

        notificationService.handleOrderEvent(record);

        assertThat(notificationService.hasProcessed("order-123", "CANCELLED")).isTrue();
        verify(idempotencyStore).markIfNew(eq("order-123:CANCELLED"), any(Duration.class));
    }

    @Test
    void handleOrderEvent_ignoresNonTerminalOrderPlacedEvent() {
        String payload = "{\"orderId\":\"order-123\",\"productId\":\"P-1\",\"quantity\":2,\"eventType\":\"OrderPlacedEvent\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, "order-123", payload);

        notificationService.handleOrderEvent(record);

        verify(idempotencyStore, never()).markIfNew(anyString(), any(Duration.class));
    }

    @Test
    void handleOrderEvent_suppressesDuplicateConfirmedEvent() {
        when(idempotencyStore.markIfNew(eq("order-123:CONFIRMED"), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);

        String payload = "{\"orderId\":\"order-123\",\"transactionId\":\"TX-101\",\"eventType\":\"OrderConfirmedEvent\"}";
        ConsumerRecord<String, String> record1 = new ConsumerRecord<>("order-events", 0, 0L, "order-123", payload);
        ConsumerRecord<String, String> record2 = new ConsumerRecord<>("order-events", 0, 1L, "order-123", payload);

        notificationService.handleOrderEvent(record1);
        notificationService.handleOrderEvent(record2);

        verify(idempotencyStore, times(2)).markIfNew(eq("order-123:CONFIRMED"), any(Duration.class));
    }

    @Test
    void handleOrderEvent_suppressesDuplicateCancelledEvent() {
        when(idempotencyStore.markIfNew(eq("order-123:CANCELLED"), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);

        String payload = "{\"orderId\":\"order-123\",\"reason\":\"Payment failed\",\"eventType\":\"OrderCancelledEvent\"}";
        ConsumerRecord<String, String> record1 = new ConsumerRecord<>("order-events", 0, 0L, "order-123", payload);
        ConsumerRecord<String, String> record2 = new ConsumerRecord<>("order-events", 0, 1L, "order-123", payload);

        notificationService.handleOrderEvent(record1);
        notificationService.handleOrderEvent(record2);

        verify(idempotencyStore, times(2)).markIfNew(eq("order-123:CANCELLED"), any(Duration.class));
    }

    @Test
    void handleDeadLetter_executesWithoutException() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events-dlt", 0, 0L, "order-123", "bad-payload");
        notificationService.handleDeadLetter(record);
    }

    @Test
    void redisNotificationIdempotencyStore_markIfNew_usesAtomicSetIfAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("notification:idempotency:ord-99:CONFIRMED"), eq("PROCESSED"), any(Duration.class)))
                .thenReturn(true);

        RedisNotificationIdempotencyStore store = new RedisNotificationIdempotencyStore(redisTemplate);
        boolean result = store.markIfNew("ord-99:CONFIRMED", Duration.ofHours(24));

        assertThat(result).isTrue();
        verify(valueOperations).setIfAbsent(eq("notification:idempotency:ord-99:CONFIRMED"), eq("PROCESSED"), eq(Duration.ofHours(24)));
    }

    @Test
    void redisNotificationIdempotencyStore_markIfNew_returnsFalseOnDuplicate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("notification:idempotency:ord-99:CONFIRMED"), eq("PROCESSED"), any(Duration.class)))
                .thenReturn(false);

        RedisNotificationIdempotencyStore store = new RedisNotificationIdempotencyStore(redisTemplate);
        boolean result = store.markIfNew("ord-99:CONFIRMED", Duration.ofHours(24));

        assertThat(result).isFalse();
    }

    @Test
    void redisNotificationIdempotencyStore_throwsExceptionOnRedisFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        RedisNotificationIdempotencyStore store = new RedisNotificationIdempotencyStore(redisTemplate);

        assertThatThrownBy(() -> store.markIfNew("ord-99:CONFIRMED", Duration.ofHours(24)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Redis idempotency store unavailable");
    }

    @Test
    void redisNotificationIdempotencyStore_hasProcessed_checksKey() {
        when(redisTemplate.hasKey("notification:idempotency:ord-99:CONFIRMED")).thenReturn(true);

        RedisNotificationIdempotencyStore store = new RedisNotificationIdempotencyStore(redisTemplate);
        assertThat(store.hasProcessed("ord-99:CONFIRMED")).isTrue();
    }
}
