package com.microservices.pro.orderservice;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTokenClientTest {

    @Test
    void getAccessToken_whenCachedTokenValid_returnsCachedWithoutCallingKeycloak() {
        OrderServiceTokenClient client = new OrderServiceTokenClient();
        // Set cached token valid for 5 minutes
        client.setCachedTokenForTesting("cached-valid-jwt", Instant.now().plusSeconds(300));

        String token = client.getAccessToken();

        assertEquals("cached-valid-jwt", token);
    }
}
