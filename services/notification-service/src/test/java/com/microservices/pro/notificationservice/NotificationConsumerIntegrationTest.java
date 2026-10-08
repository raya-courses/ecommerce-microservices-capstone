package com.microservices.pro.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@EmbeddedKafka(partitions = 1, topics = {"order-events"})
@DirtiesContext
class NotificationConsumerIntegrationTest {

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private NotificationIdempotencyStore idempotencyStore;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void kafkaConsumer_processesConfirmedEventFromTopic() {
        when(idempotencyStore.markIfNew(eq("order-kafka-1:CONFIRMED"), any(Duration.class))).thenReturn(true);

        String payload = "{\"orderId\":\"order-kafka-1\",\"transactionId\":\"TX-999\",\"eventType\":\"OrderConfirmedEvent\"}";
        kafkaTemplate.send("order-events", "order-kafka-1", payload);

        verify(idempotencyStore, timeout(10000)).markIfNew(eq("order-kafka-1:CONFIRMED"), any(Duration.class));
    }

    @Test
    void kafkaConsumer_processesCancelledEventFromTopic() {
        when(idempotencyStore.markIfNew(eq("order-kafka-2:CANCELLED"), any(Duration.class))).thenReturn(true);

        String payload = "{\"orderId\":\"order-kafka-2\",\"reason\":\"Out of stock\",\"eventType\":\"OrderCancelledEvent\"}";
        kafkaTemplate.send("order-events", "order-kafka-2", payload);

        verify(idempotencyStore, timeout(10000)).markIfNew(eq("order-kafka-2:CANCELLED"), any(Duration.class));
    }
}
