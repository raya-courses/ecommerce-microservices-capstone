package com.microservices.pro.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * UserContextFilter — Reactive GlobalFilter propagating verified user context to downstream services.
 * Replaces legacy custom JWT filter header injection.
 */
@Component
public class UserContextFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .flatMap(authentication -> {
                    ServerHttpRequest.Builder builder = exchange.getRequest().mutate();

                    // Strip any incoming user headers from untrusted clients
                    builder.headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Roles");
                        headers.remove("X-User-Role");
                        headers.remove("X-User-Email");
                    });

                    String userId = authentication.getName();
                    if (userId != null && !userId.isBlank()) {
                        builder.header("X-User-Id", userId);
                    }

                    String roles = authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.joining(","));
                    if (!roles.isBlank()) {
                        builder.header("X-User-Roles", roles);
                        builder.header("X-User-Role", roles);
                    }

                    if (authentication instanceof JwtAuthenticationToken jwtToken) {
                        String email = jwtToken.getToken().getClaimAsString("email");
                        if (email != null && !email.isBlank()) {
                            builder.header("X-User-Email", email);
                        }
                    }

                    return chain.filter(exchange.mutate().request(builder.build()).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
