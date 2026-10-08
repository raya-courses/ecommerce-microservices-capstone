package com.microservices.pro.orderservice;

import com.microservices.pro.orderservice.analytics.AnalyticsProcessedEventRepository;
import com.microservices.pro.orderservice.analytics.HourlyOrderMetricRepository;
import com.microservices.pro.orderservice.analytics.OrderAnalyticsRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "management.tracing.sampling.probability=1.0",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@AutoConfigureObservability
class ObservabilityTracingTest {

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private OutboxRepository outboxRepository;

    @MockBean
    private OrderAnalyticsRepository orderAnalyticsRepository;

    @MockBean
    private AnalyticsProcessedEventRepository analyticsProcessedEventRepository;

    @MockBean
    private HourlyOrderMetricRepository hourlyOrderMetricRepository;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaStringTemplate;

    @MockBean
    private OrderServiceTokenClient orderServiceTokenClient;

    @Autowired(required = false)
    private Tracer tracer;

    @Test
    void tracerBean_isLoadedAndInjectsTraceContext() {
        assertThat(tracer).isNotNull();

        Span span = tracer.nextSpan().name("test-span").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            assertThat(tracer.currentSpan()).isNotNull();
            String traceId = tracer.currentSpan().context().traceId();
            String spanId = tracer.currentSpan().context().spanId();

            assertThat(traceId).isNotBlank();
            assertThat(spanId).isNotBlank();

            // Verify Brave automatically populates traceId in MDC for structured JSON logs
            assertThat(MDC.get("traceId")).isEqualTo(traceId);
            assertThat(MDC.get("spanId")).isEqualTo(spanId);
        } finally {
            span.end();
        }
    }
}
