package com.microservices.pro.orderservice;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * FeignJwtInterceptor — Forwards incoming customer Bearer token if present.
 * If no user token exists in the current request context (e.g. async/background execution),
 * obtains and forwards a Client Credentials token using OrderServiceTokenClient.
 */
@Component
public class FeignJwtInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignJwtInterceptor.class);

    private final ObjectProvider<OrderServiceTokenClient> tokenClientProvider;

    @Autowired
    public FeignJwtInterceptor(ObjectProvider<OrderServiceTokenClient> tokenClientProvider) {
        this.tokenClientProvider = tokenClientProvider;
    }

    public FeignJwtInterceptor(OrderServiceTokenClient tokenClient) {
        this.tokenClientProvider = new ObjectProvider<>() {
            @Override
            public OrderServiceTokenClient getObject(Object... args) {
                return tokenClient;
            }

            @Override
            public OrderServiceTokenClient getIfAvailable() {
                return tokenClient;
            }

            @Override
            public OrderServiceTokenClient getIfUnique() {
                return tokenClient;
            }

            @Override
            public OrderServiceTokenClient getObject() {
                return tokenClient;
            }
        };
    }

    @Override
    public void apply(RequestTemplate template) {
        // 1. If an incoming user Bearer token exists, propagate it
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String authHeader = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                template.header(HttpHeaders.AUTHORIZATION, authHeader);
                return;
            }
        }

        // 2. If no user token exists, fall back to Client Credentials
        if (tokenClientProvider != null) {
            OrderServiceTokenClient tokenClient = tokenClientProvider.getIfAvailable();
            if (tokenClient != null) {
                try {
                    String clientToken = tokenClient.getAccessToken();
                    if (clientToken != null && !clientToken.isBlank()) {
                        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken);
                    }
                } catch (Exception e) {
                    log.warn("[FEIGN-AUTH] Could not obtain client credentials token: {}", e.getMessage());
                }
            }
        }
    }
}
