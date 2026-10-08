package com.microservices.pro.apigateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * KeycloakJwtAuthenticationConverter — Extracts realm and resource roles from Keycloak JWT
 * and maps them to Spring Security GrantedAuthorities prefixed with ROLE_.
 */
@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String principal = jwt.getClaimAsString("preferred_username");
        if (principal == null || principal.isBlank()) {
            principal = jwt.getSubject();
        }
        return Mono.just(new JwtAuthenticationToken(jwt, authorities, principal));
    }

    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // 1. Extract realm roles from realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            for (Object roleObj : roles) {
                if (roleObj instanceof String role && !role.isBlank()) {
                    authorities.add(toGrantedAuthority(role));
                }
            }
        }

        // 2. Extract resource roles from resource_access.<client>.roles
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            for (Object clientVal : resourceAccess.values()) {
                if (clientVal instanceof Map<?, ?> clientMap && clientMap.get("roles") instanceof Collection<?> clientRoles) {
                    for (Object roleObj : clientRoles) {
                        if (roleObj instanceof String role && !role.isBlank()) {
                            authorities.add(toGrantedAuthority(role));
                        }
                    }
                }
            }
        }

        // 3. Fallback: check direct "roles" or "authorities" claims
        if (jwt.getClaim("roles") instanceof Collection<?> directRoles) {
            for (Object roleObj : directRoles) {
                if (roleObj instanceof String role && !role.isBlank()) {
                    authorities.add(toGrantedAuthority(role));
                }
            }
        }

        return authorities;
    }

    private GrantedAuthority toGrantedAuthority(String role) {
        String authority = role.trim().toUpperCase();
        if (!authority.startsWith("ROLE_")) {
            authority = "ROLE_" + authority;
        }
        return new SimpleGrantedAuthority(authority);
    }
}
