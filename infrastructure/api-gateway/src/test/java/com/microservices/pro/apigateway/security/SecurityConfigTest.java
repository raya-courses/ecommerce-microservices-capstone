package com.microservices.pro.apigateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false"
})
@AutoConfigureWebTestClient
@Import(SecurityConfigTest.TestController.class)
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @RestController
    static class TestController {
        @GetMapping("/api/v1/products/123")
        public String getProduct() { return "product"; }

        @PostMapping("/api/v1/products")
        public String createProduct() { return "product-created"; }

        @PostMapping("/api/v1/inventory/items")
        public String createInventory() { return "inventory-created"; }

        @PostMapping("/api/v1/orders")
        public String createOrder() { return "order-created"; }

        @GetMapping("/api/v1/analytics/summary")
        public String getAnalyticsSummary() { return "analytics-summary"; }

        @GetMapping("/actuator/health")
        public String health() { return "up"; }
    }

    @Test
    void publicProductGet_allowsAnonymousAccess() {
        webTestClient.get()
                .uri("/api/v1/products/123")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void missingToken_onProtectedRoute_returns401Unauthorized() {
        webTestClient.post()
                .uri("/api/v1/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void customerToken_onOrderEndpoint_returns200Ok() {
        webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .post()
                .uri("/api/v1/orders")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void customerToken_onAdminProductCreate_returns403Forbidden() {
        webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .post()
                .uri("/api/v1/products")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminToken_onAdminProductCreate_returns200Ok() {
        webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .post()
                .uri("/api/v1/products")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void customerToken_onInventoryWrite_returns403Forbidden() {
        webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .post()
                .uri("/api/v1/inventory/items")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminToken_onInventoryWrite_returns200Ok() {
        webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .post()
                .uri("/api/v1/inventory/items")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void unauthenticated_onAnalyticsSummary_returns401Unauthorized() {
        webTestClient.get()
                .uri("/api/v1/analytics/summary")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void customerToken_onAnalyticsSummary_returns403Forbidden() {
        webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .get()
                .uri("/api/v1/analytics/summary")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminToken_onAnalyticsSummary_returns200Ok() {
        webTestClient.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .get()
                .uri("/api/v1/analytics/summary")
                .exchange()
                .expectStatus().isOk();
    }
}
