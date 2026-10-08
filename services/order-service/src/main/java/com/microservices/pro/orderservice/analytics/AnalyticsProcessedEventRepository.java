package com.microservices.pro.orderservice.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsProcessedEventRepository extends JpaRepository<AnalyticsProcessedEvent, String> {
}
