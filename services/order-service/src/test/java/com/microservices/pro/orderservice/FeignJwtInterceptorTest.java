package com.microservices.pro.orderservice;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeignJwtInterceptorTest {

    private OrderServiceTokenClient mockTokenClient;
    private FeignJwtInterceptor interceptor;

    @BeforeEach
    void setUp() {
        mockTokenClient = Mockito.mock(OrderServiceTokenClient.class);
        interceptor = new FeignJwtInterceptor(mockTokenClient);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void apply_whenUserAuthorizationHeaderPresent_propagatesUserToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-jwt-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get(HttpHeaders.AUTHORIZATION);
        assertNotNull(authHeaders);
        assertTrue(authHeaders.contains("Bearer user-jwt-token"));
        verifyNoInteractions(mockTokenClient);
    }

    @Test
    void apply_whenNoRequestContext_fallsBackToClientCredentials() {
        RequestContextHolder.resetRequestAttributes();
        when(mockTokenClient.getAccessToken()).thenReturn("service-account-token");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get(HttpHeaders.AUTHORIZATION);
        assertNotNull(authHeaders);
        assertTrue(authHeaders.contains("Bearer service-account-token"));
        verify(mockTokenClient).getAccessToken();
    }

    @Test
    void apply_whenNoAuthorizationHeaderInRequest_fallsBackToClientCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(mockTokenClient.getAccessToken()).thenReturn("service-account-token-2");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get(HttpHeaders.AUTHORIZATION);
        assertNotNull(authHeaders);
        assertTrue(authHeaders.contains("Bearer service-account-token-2"));
        verify(mockTokenClient).getAccessToken();
    }

    @Test
    void apply_whenTokenClientFails_doesNotSetHeaderAndDoesNotThrow() {
        RequestContextHolder.resetRequestAttributes();
        when(mockTokenClient.getAccessToken()).thenThrow(new RuntimeException("Keycloak connection failed"));

        RequestTemplate template = new RequestTemplate();
        assertDoesNotThrow(() -> interceptor.apply(template));

        Collection<String> authHeaders = template.headers().get(HttpHeaders.AUTHORIZATION);
        assertNull(authHeaders);
    }
}
