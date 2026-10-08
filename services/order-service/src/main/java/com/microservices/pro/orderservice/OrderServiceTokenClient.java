package com.microservices.pro.orderservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * OrderServiceTokenClient — Obtains a Keycloak access_token for order-service via
 * Client Credentials Flow when calling downstream services in a background/async context.
 */
@Service
public class OrderServiceTokenClient {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceTokenClient.class);

    @Value("${keycloak.token-endpoint:http://localhost:8180/realms/ecommerce-platform/protocol/openid-connect/token}")
    private String tokenEndpoint;

    @Value("${keycloak.client-id:order-service}")
    private String clientId;

    @Value("${keycloak.client-secret:order-service-secret-dev-only}")
    private String clientSecret;

    private final RestClient restClient;

    private String cachedToken = null;
    private Instant expiresAt = Instant.MIN;

    public OrderServiceTokenClient() {
        this.restClient = RestClient.create();
    }

    public OrderServiceTokenClient(String tokenEndpoint, String clientId, String clientSecret, RestClient restClient) {
        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = restClient != null ? restClient : RestClient.create();
    }

    public String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        log.info("[CLIENT-CREDENTIALS] Requesting new token for {}", clientId);
        return fetchNewToken();
    }

    @SuppressWarnings("unchecked")
    private String fetchNewToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        Map<String, Object> response = restClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("Failed to obtain access_token from Keycloak");
        }

        cachedToken = (String) response.get("access_token");
        int expiresIn = (Integer) response.getOrDefault("expires_in", 300);
        expiresAt = Instant.now().plusSeconds(expiresIn);

        log.info("[CLIENT-CREDENTIALS] Token obtained, expires in {}s", expiresIn);
        return cachedToken;
    }

    public void setCachedTokenForTesting(String token, Instant expiresAt) {
        this.cachedToken = token;
        this.expiresAt = expiresAt;
    }
}
