package com.microservices.pro.orderservice;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class FeignJwtInterceptorTest {

    private FeignJwtInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new FeignJwtInterceptor();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void apply_whenAuthorizationHeaderPresent_propagatesToTemplate() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-jwt-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get(HttpHeaders.AUTHORIZATION);
        assertNotNull(authHeaders);
        assertTrue(authHeaders.contains("Bearer test-jwt-token"));
    }

    @Test
    void apply_whenNoRequestContext_doesNotSetHeader() {
        RequestContextHolder.resetRequestAttributes();

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get(HttpHeaders.AUTHORIZATION);
        assertNull(authHeaders);
    }

    @Test
    void apply_whenNoAuthorizationHeader_doesNotSetHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get(HttpHeaders.AUTHORIZATION);
        assertNull(authHeaders);
    }
}
