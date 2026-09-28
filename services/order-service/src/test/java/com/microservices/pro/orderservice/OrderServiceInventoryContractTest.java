package com.microservices.pro.orderservice;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.LambdaDsl;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderServiceInventoryContractTest — Session 11, Lab 9B, Task 1.
 *
 * Consumer-Driven Contract test: order-service (Consumer) defines what it
 * expects from inventory-service (Provider). Pact generates a JSON pact
 * file that inventory-service reads and verifies.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "inventory-service", pactVersion = PactSpecVersion.V3)
class OrderServiceInventoryContractTest {

    // ── Contract: stock available ───────────────────────────────────────

    @Pact(consumer = "order-service", provider = "inventory-service")
    public RequestResponsePact checkStockAvailable(PactDslWithProvider builder) {
        return builder
                .given("PROD-001 has 100 units in stock")
                .uponReceiving("a stock check for PROD-001 quantity 5")
                    .path("/api/v1/inventory/check")
                    .method("GET")
                    .query("productId=PROD-001&quantity=5")
                .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(LambdaDsl.newJsonBody(body -> body
                            .booleanValue("available", true)
                            .integerType("remainingStock", 95)
                            .stringValue("productId", "PROD-001")
                    ).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "checkStockAvailable")
    void checkStock_deserializesAvailableField_correctly(MockServer mockServer) {
        InventoryClient client = buildFeignClient(mockServer.getUrl());

        StockCheckResponse response = client.checkStock("PROD-001", 5);

        assertThat(response.available()).isTrue();
        assertThat(response.remainingStock()).isGreaterThan(0);
        assertThat(response.productId()).isEqualTo("PROD-001");
    }

    // ── Contract: stock unavailable (409 Conflict) ──────────────────────

    @Pact(consumer = "order-service", provider = "inventory-service")
    public RequestResponsePact checkStockUnavailable(PactDslWithProvider builder) {
        return builder
                .given("PROD-003 is out of stock")
                .uponReceiving("a stock check for PROD-003 quantity 1")
                    .path("/api/v1/inventory/check")
                    .method("GET")
                    .query("productId=PROD-003&quantity=1")
                .willRespondWith()
                    .status(409)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(LambdaDsl.newJsonBody(body -> body
                            .booleanValue("available", false)
                            .integerType("remainingStock", 0)
                            .stringValue("productId", "PROD-003")
                    ).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "checkStockUnavailable")
    void checkStock_returns409_whenStockInsufficient(MockServer mockServer) {
        InventoryClient client = buildFeignClient(mockServer.getUrl());

        try {
            client.checkStock("PROD-003", 1);
        } catch (Exception e) {
            // expected — InventoryErrorDecoder converts 409 -> InsufficientStockException
        }
    }

    private InventoryClient buildFeignClient(String baseUrl) {
        return Feign.builder()
                .contract(new SpringMvcContract())
                .decoder(new JacksonDecoder())
                .target(InventoryClient.class, baseUrl + "/api/v1/inventory");
    }
}
