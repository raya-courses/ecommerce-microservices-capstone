package com.microservices.pro.orderservice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * OrderRepository — Session 7.
 */
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByCustomerId(String customerId);
}
