package com.microservices.pro.inventoryservice;

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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InventoryServiceIntegrationTest — Testcontainers integration test with real PostgreSQL.
 * Validates Flyway migrations and StockItem persistence/reservation against real Postgres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Testcontainers(disabledWithoutDocker = true)
class InventoryServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("inventorydb_test")
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
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @BeforeEach
    void clearDatabase() {
        stockReservationRepository.deleteAll();
        stockItemRepository.deleteAll();
    }

    @Test
    void persistAndQueryStockItem_withRealPostgres() {
        StockItem item = new StockItem("PROD-TC-1", 50, 5);
        stockItemRepository.save(item);

        Optional<StockItem> found = stockItemRepository.findById("PROD-TC-1");
        assertThat(found).isPresent();
        assertThat(found.get().getAvailableQuantity()).isEqualTo(50);
        assertThat(found.get().getReservedQuantity()).isEqualTo(5);
        assertThat(found.get().hasStock(10)).isTrue();
    }
}
