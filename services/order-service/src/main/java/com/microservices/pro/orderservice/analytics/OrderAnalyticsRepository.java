package com.microservices.pro.orderservice.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface OrderAnalyticsRepository extends JpaRepository<OrderAnalyticsItem, String> {

    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM OrderAnalyticsItem o WHERE o.status = 'CONFIRMED'")
    BigDecimal calculateTotalConfirmedRevenue();
}
