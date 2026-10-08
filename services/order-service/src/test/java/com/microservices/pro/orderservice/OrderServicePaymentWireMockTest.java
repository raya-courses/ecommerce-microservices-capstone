package com.microservices.pro.orderservice;

import com.microservices.pro.orderservice.analytics.AnalyticsProcessedEventRepository;
import com.microservices.pro.orderservice.analytics.HourlyOrderMetricRepository;
import com.microservices.pro.orderservice.analytics.OrderAnalyticsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderServicePaymentWireMockTest — Session 11, Lab 9B, Task 3.
 *
 * WireMock stubs the inventory-service HTTP layer and verifies Feign communication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
        "spring.cloud.openfeign.client.config.INVENTORY-SERVICE.url=http://localhost:${wiremock.server.port}",
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.config.import=",
        "spring.cache.type=none",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class OrderServicePaymentWireMockTest {

    @Autowired
    private OrderService orderService;

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
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaStringTemplate;

    @MockBean
    private OrderServiceTokenClient orderServiceTokenClient;

    // ── Happy Path: Inventory says available → order proceeds ──────────

    @Test
    void createOrder_proceedsPastStockCheck_whenInventoryReportsAvailable() {
        stubFor(get(urlPathEqualTo("/api/v1/inventory/check"))
                .withQueryParam("productId", equalTo("PROD-001"))
                .withQueryParam("quantity", equalTo("1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"productId\":\"PROD-001\",\"requestedQuantity\":1," +
                                "\"available\":true,\"remainingStock\":99}")));

        OrderResponse response = orderService.createOrder(
                new OrderRequest("PROD-001", 1, new BigDecimal("100.00"), "cust-1"));

        assertThat(response.status()).isEqualTo("PENDING");
    }

    // ── Failure Path: Inventory returns 409 → order REJECTED ───────────

    @Test
    void createOrder_returnsRejected_whenInventoryReports409() {
        stubFor(get(urlPathEqualTo("/api/v1/inventory/check"))
                .withQueryParam("productId", equalTo("PROD-003"))
                .withQueryParam("quantity", equalTo("1"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"productId\":\"PROD-003\",\"requestedQuantity\":1," +
                                "\"available\":false,\"remainingStock\":0}")));

        OrderResponse response = orderService.createOrder(
                new OrderRequest("PROD-003", 1, new BigDecimal("50.00"), "cust-1"));

        assertThat(response.status()).isEqualTo("REJECTED");
    }

    // ── Verify: correct URL + query params were sent by Feign ──────────

    @Test
    void createOrder_sendsCorrectQueryParams_toInventoryService() {
        stubFor(get(urlPathEqualTo("/api/v1/inventory/check"))
                .withQueryParam("productId", equalTo("PROD-002"))
                .withQueryParam("quantity", equalTo("3"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"productId\":\"PROD-002\",\"requestedQuantity\":3," +
                                "\"available\":true,\"remainingStock\":2}")));

        orderService.createOrder(
                new OrderRequest("PROD-002", 3, new BigDecimal("75.00"), "cust-2"));

        verify(getRequestedFor(urlPathEqualTo("/api/v1/inventory/check"))
                .withQueryParam("productId", equalTo("PROD-002"))
                .withQueryParam("quantity", equalTo("3")));
    }
}
