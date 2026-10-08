package com.microservices.pro.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createOrder_returnsOrderResponse() throws Exception {
        OrderRequest request = new OrderRequest("PROD-001", 2, new BigDecimal("49.98"), "CUST-1");
        OrderResponse response = new OrderResponse("ORD-123", "PENDING", "Order placed successfully");

        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", "CUST-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-123"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Order placed successfully"));
    }

    @Test
    void createOrder_whenRejected_returns409() throws Exception {
        OrderRequest request = new OrderRequest("PROD-001", 2, new BigDecimal("49.98"), "CUST-1");
        OrderResponse response = new OrderResponse(null, "REJECTED", "Insufficient stock: only 0 available");

        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void getOrder_whenOwner_returnsOrder() throws Exception {
        Order order = new Order("ORD-123", "PROD-001", 2, new BigDecimal("49.98"), OrderStatus.PENDING, "CUST-1");
        when(orderService.getOrder("ORD-123")).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/ORD-123")
                        .header("X-User-Id", "CUST-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-123"))
                .andExpect(jsonPath("$.customerId").value("CUST-1"));
    }

    @Test
    void getOrder_whenDifferentUser_returns403() throws Exception {
        Order order = new Order("ORD-123", "PROD-001", 2, new BigDecimal("49.98"), OrderStatus.PENDING, "CUST-1");
        when(orderService.getOrder("ORD-123")).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/ORD-123")
                        .header("X-User-Id", "CUST-OTHER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getOrders_returnsCustomerOrders() throws Exception {
        Order order = new Order("ORD-123", "PROD-001", 2, new BigDecimal("49.98"), OrderStatus.PENDING, "CUST-1");
        when(orderService.getOrdersByCustomerId("CUST-1")).thenReturn(List.of(order));

        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Id", "CUST-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value("ORD-123"));
    }

    @Test
    void getStatus_returnsOrderStatus() throws Exception {
        when(orderService.getOrderStatus("ORD-123")).thenReturn(OrderStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/orders/ORD-123/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("CONFIRMED"));
    }
}
