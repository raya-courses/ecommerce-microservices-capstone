package com.microservices.pro.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UserContextFilterTest {

    private UserContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserContextFilter();
    }

    @Test
    void filter_whenAuthenticated_enrichesRequestHeadersAndStripsSpoofedHeaders() {
        MockServerHttpRequest initialRequest = MockServerHttpRequest.get("/api/v1/orders")
                .header("X-User-Id", "spoofed-id")
                .header("X-User-Roles", "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(initialRequest);

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "user-uuid-789")
                .claim("email", "john@ecommerce.dev")
                .build();

        Authentication auth = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                "john_doe"
        );

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block();

        assertNotNull(capturedExchange.get());
        var headers = capturedExchange.get().getRequest().getHeaders();

        assertEquals("john_doe", headers.getFirst("X-User-Id"));
        assertEquals("ROLE_CUSTOMER", headers.getFirst("X-User-Roles"));
        assertEquals("ROLE_CUSTOMER", headers.getFirst("X-User-Role"));
        assertEquals("john@ecommerce.dev", headers.getFirst("X-User-Email"));
    }

    @Test
    void filter_whenUnauthenticated_doesNotAddHeaders() {
        MockServerHttpRequest initialRequest = MockServerHttpRequest.get("/api/v1/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(initialRequest);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNotNull(capturedExchange.get());
        var headers = capturedExchange.get().getRequest().getHeaders();

        assertNull(headers.getFirst("X-User-Id"));
        assertNull(headers.getFirst("X-User-Roles"));
    }
}
