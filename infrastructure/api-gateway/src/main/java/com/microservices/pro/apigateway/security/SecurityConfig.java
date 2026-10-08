package com.microservices.pro.apigateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * SecurityConfig — Reactive WebFlux security configuration for API Gateway as OAuth2 Resource Server.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    public SecurityConfig(KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter) {
        this.keycloakJwtAuthenticationConverter = keycloakJwtAuthenticationConverter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Actuator health & Prometheus metrics endpoints (permitted for scrape/monitoring)
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .pathMatchers("/actuator/**").hasRole("ADMIN")

                        // Public product catalog read
                        .pathMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()

                        // Product write endpoints (ADMIN only)
                        .pathMatchers(HttpMethod.POST, "/api/v1/products/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/products/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasRole("ADMIN")

                        // Inventory admin endpoints (ADMIN only)
                        .pathMatchers(HttpMethod.POST, "/api/v1/inventory/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/inventory/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/inventory/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/inventory/items/**").hasRole("ADMIN")

                        // Order analytics summary (ADMIN only)
                        .pathMatchers("/api/v1/analytics/**").hasRole("ADMIN")

                        // Customer order operations (CUSTOMER only)
                        .pathMatchers("/api/v1/orders/**").hasRole("CUSTOMER")

                        // All other routes require authentication
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter))
                )
                .build();
    }
}
