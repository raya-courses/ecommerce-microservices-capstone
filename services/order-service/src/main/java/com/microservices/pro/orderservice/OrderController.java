package com.microservices.pro.orderservice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OrderController — Session 7 / Phase 4 Capstone API alignment.
 *
 * All endpoints mapped to /api/v1/orders.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody OrderRequest request) {

        String customerId = (userId != null && !userId.isBlank()) ? userId : request.customerId();
        OrderRequest effectiveRequest = new OrderRequest(
                request.productId(),
                request.quantity(),
                request.amount(),
                customerId
        );

        OrderResponse response = orderService.createOrder(effectiveRequest);
        if ("REJECTED".equals(response.status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(
            @PathVariable String orderId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {

        Order order = orderService.getOrder(orderId);
        if (userId != null && !userId.isBlank()) {
            boolean isAdmin = roles != null && roles.contains("ADMIN");
            if (!isAdmin && order.getCustomerId() != null && !userId.equals(order.getCustomerId())) {
                throw new SecurityException("Access denied: cannot access orders belonging to another user");
            }
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<List<Order>> getOrders(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {

        boolean isAdmin = roles != null && roles.contains("ADMIN");
        if (isAdmin) {
            return ResponseEntity.ok(orderService.getAllOrders());
        }
        if (userId != null && !userId.isBlank()) {
            return ResponseEntity.ok(orderService.getOrdersByCustomerId(userId));
        }
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<OrderStatus> getStatus(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrderStatus(orderId));
    }
}
