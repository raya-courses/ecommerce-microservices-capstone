package com.microservices.pro.orderservice.analytics;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * HourlyOrderMetric — Pre-aggregated metrics per hour bucket and status in hourly_order_metrics table.
 */
@Entity
@Table(name = "hourly_order_metrics", uniqueConstraints = {
        @UniqueConstraint(name = "uq_hour_status", columnNames = {"hour_bucket", "status"})
})
public class HourlyOrderMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hour_bucket", nullable = false)
    private String hourBucket;

    @Column(nullable = false)
    private String status;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Column(nullable = false)
    private BigDecimal revenue;

    protected HourlyOrderMetric() {
        // JPA
    }

    public HourlyOrderMetric(String hourBucket, String status, long orderCount, BigDecimal revenue) {
        this.hourBucket = hourBucket;
        this.status = status;
        this.orderCount = orderCount;
        this.revenue = revenue;
    }

    public Long getId() {
        return id;
    }

    public String getHourBucket() {
        return hourBucket;
    }

    public void setHourBucket(String hourBucket) {
        this.hourBucket = hourBucket;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(long orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }
}
