package com.microservices.pro.orderservice;

import com.microservices.pro.orderservice.analytics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderServiceIntegrationTest — Testcontainers integration test with real PostgreSQL.
 * Validates Flyway migrations, Order/Outbox persistence, and Order Analytics tables against real Postgres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Testcontainers(disabledWithoutDocker = true)
class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("orderdb_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OrderAnalyticsRepository orderAnalyticsRepository;

    @Autowired
    private AnalyticsProcessedEventRepository analyticsProcessedEventRepository;

    @Autowired
    private HourlyOrderMetricRepository hourlyOrderMetricRepository;

    @BeforeEach
    void clearDatabase() {
        hourlyOrderMetricRepository.deleteAll();
        analyticsProcessedEventRepository.deleteAll();
        orderAnalyticsRepository.deleteAll();
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void persistAndQueryOrder_withRealPostgres() {
        Order order = new Order("ord-tc-1", "PROD-100", 2, new BigDecimal("199.98"), OrderStatus.PENDING, "cust-tc");
        orderRepository.save(order);

        Optional<Order> found = orderRepository.findById("ord-tc-1");
        assertThat(found).isPresent();
        assertThat(found.get().getProductId()).isEqualTo("PROD-100");
        assertThat(found.get().getQuantity()).isEqualTo(2);
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void persistAndQueryAnalyticsReadModel_withRealPostgres() {
        Instant now = Instant.now();
        OrderAnalyticsItem item = new OrderAnalyticsItem("ord-tc-2", "PROD-200", "cust-tc", new BigDecimal("350.00"), "CONFIRMED", now, now);
        orderAnalyticsRepository.save(item);

        AnalyticsProcessedEvent event = new AnalyticsProcessedEvent("ord-tc-2:OrderConfirmed", "ord-tc-2", "OrderConfirmed", now);
        analyticsProcessedEventRepository.save(event);

        HourlyOrderMetric metric = new HourlyOrderMetric("2026-09-29T10:00:00Z", "CONFIRMED", 1, new BigDecimal("350.00"));
        hourlyOrderMetricRepository.save(metric);

        assertThat(orderAnalyticsRepository.findById("ord-tc-2")).isPresent();
        assertThat(orderAnalyticsRepository.calculateTotalConfirmedRevenue()).isEqualByComparingTo("350.00");
        assertThat(analyticsProcessedEventRepository.existsById("ord-tc-2:OrderConfirmed")).isTrue();
        assertThat(hourlyOrderMetricRepository.findByHourBucketAndStatus("2026-09-29T10:00:00Z", "CONFIRMED")).isPresent();
    }
}
