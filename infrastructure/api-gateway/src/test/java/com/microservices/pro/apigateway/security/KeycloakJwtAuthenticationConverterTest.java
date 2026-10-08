package com.microservices.pro.apigateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KeycloakJwtAuthenticationConverterTest {

    private KeycloakJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KeycloakJwtAuthenticationConverter();
    }

    @Test
    void convert_extractsRolesAndMapsToSpringAuthorities() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "user-uuid-123")
                .claim("preferred_username", "admin1")
                .claim("email", "admin1@ecommerce.dev")
                .claim("realm_access", Map.of("roles", List.of("admin", "customer")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        AbstractAuthenticationToken authToken = converter.convert(jwt).block();

        assertNotNull(authToken);
        assertEquals("admin1", authToken.getName());

        Collection<String> authorities = authToken.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("ROLE_CUSTOMER"));
    }

    @Test
    void convert_handlesUpperAndLowerCasesGracefully() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "user-uuid-456")
                .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                .build();

        AbstractAuthenticationToken authToken = converter.convert(jwt).block();

        assertNotNull(authToken);
        assertEquals("user-uuid-456", authToken.getName()); // fallback to sub
        Collection<String> authorities = authToken.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertTrue(authorities.contains("ROLE_CUSTOMER"));
    }

    @Test
    void convert_whenNoRealmAccess_returnsEmptyAuthorities() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "anon-user")
                .build();

        AbstractAuthenticationToken authToken = converter.convert(jwt).block();

        assertNotNull(authToken);
        assertTrue(authToken.getAuthorities().isEmpty());
    }
}
