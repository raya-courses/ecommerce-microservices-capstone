package com.microservices.pro.orderservice;

import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InventoryErrorDecoderTest {

    private InventoryErrorDecoder decoder;
    private Request request;

    @BeforeEach
    void setUp() {
        decoder = new InventoryErrorDecoder();
        request = Request.create(
                Request.HttpMethod.GET,
                "/api/v1/inventory/check",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );
    }

    @Test
    void decode_when409_returnsInsufficientStockException() {
        Response response = Response.builder()
                .status(409)
                .reason("Conflict")
                .request(request)
                .headers(Collections.emptyMap())
                .build();

        Exception ex = decoder.decode("checkStock", response);

        assertInstanceOf(InsufficientStockException.class, ex);
    }

    @Test
    void decode_when404_returnsProductNotFoundException() {
        Response response = Response.builder()
                .status(404)
                .reason("Not Found")
                .request(request)
                .headers(Collections.emptyMap())
                .build();

        Exception ex = decoder.decode("checkStock", response);

        assertInstanceOf(ProductNotFoundException.class, ex);
    }

    @Test
    void decode_when503_returnsServiceUnavailableException() {
        Response response = Response.builder()
                .status(503)
                .reason("Service Unavailable")
                .request(request)
                .headers(Collections.emptyMap())
                .build();

        Exception ex = decoder.decode("checkStock", response);

        assertInstanceOf(ServiceUnavailableException.class, ex);
    }

    @Test
    void decode_when500_returnsDefaultFeignException() {
        Response response = Response.builder()
                .status(500)
                .reason("Internal Server Error")
                .request(request)
                .headers(Collections.emptyMap())
                .build();

        Exception ex = decoder.decode("checkStock", response);

        assertInstanceOf(feign.FeignException.class, ex);
    }
}
