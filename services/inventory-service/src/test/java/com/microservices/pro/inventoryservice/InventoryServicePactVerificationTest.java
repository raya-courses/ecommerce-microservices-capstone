package com.microservices.pro.inventoryservice;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

/**
 * InventoryServicePactVerificationTest — Session 11, Lab 9B, Task 2.
 */
@Provider("inventory-service")
@PactFolder("../order-service/target/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.config.import=",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class InventoryServicePactVerificationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private InventoryService inventoryService;

    @MockBean
    private StockItemRepository stockItemRepository;

    @MockBean
    private StockReservationRepository stockReservationRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpringProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("PROD-001 has 100 units in stock")
    void setupProd001FullStock() {
        Mockito.when(stockItemRepository.findById("PROD-001"))
                .thenReturn(Optional.of(new StockItem("PROD-001", 100, 0)));
    }

    @State("PROD-003 is out of stock")
    void setupProd003OutOfStock() {
        Mockito.when(stockItemRepository.findById("PROD-003"))
                .thenReturn(Optional.of(new StockItem("PROD-003", 0, 0)));
    }
}
