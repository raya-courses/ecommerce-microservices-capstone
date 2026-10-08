package com.microservices.pro.paymentservice;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaymentServiceIntegrationTest — Testcontainers integration test with real PostgreSQL.
 * Validates Flyway migrations and Payment persistence against real Postgres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Testcontainers(disabledWithoutDocker = true)
class PaymentServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("paymentdb_test")
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
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @BeforeEach
    void clearDatabase() {
        paymentRepository.deleteAll();
        idempotencyRepository.deleteAll();
    }

    @Test
    void persistAndQueryPayment_withRealPostgres() {
        Payment payment = new Payment("pay-tc-1", "ord-tc-1", new BigDecimal("75.50"), "COMPLETED", "tx-tc-99");
        paymentRepository.save(payment);

        Optional<Payment> found = paymentRepository.findById("pay-tc-1");
        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo("ord-tc-1");
        assertThat(found.get().getAmount()).isEqualByComparingTo(new BigDecimal("75.50"));
        assertThat(found.get().getStatus()).isEqualTo("COMPLETED");
        assertThat(found.get().getTransactionId()).isEqualTo("tx-tc-99");
    }
}
