package com.microservices.pro.orderservice;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publishPendingEvents_marksSuccessfulEventAsPublished() {
        OutboxEvent event = new OutboxEvent("order-123", "ORDER", "OrderPlacedEvent", "{\"orderId\":\"order-123\"}");
        when(outboxRepository.findUnpublished()).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(
                new SendResult<>(null, null));
        when(kafkaTemplate.send(eq("order-events"), eq("order-123"), any()))
                .thenReturn(future);

        outboxPublisher.publishPendingEvents();

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxRepository).save(event);
    }

    @Test
    void publishPendingEvents_failedPublishKeepsEventRetryable() {
        OutboxEvent event = new OutboxEvent("order-123", "ORDER", "OrderPlacedEvent", "{\"orderId\":\"order-123\"}");
        when(outboxRepository.findUnpublished()).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker down"));

        when(kafkaTemplate.send(eq("order-events"), eq("order-123"), any()))
                .thenReturn(failedFuture);

        outboxPublisher.publishPendingEvents();

        assertThat(event.isPublished()).isFalse();
        assertThat(event.getPublishedAt()).isNull();
        verify(outboxRepository, never()).save(event);
    }
}
