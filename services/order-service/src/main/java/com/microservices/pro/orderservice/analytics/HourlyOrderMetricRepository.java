package com.microservices.pro.orderservice.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HourlyOrderMetricRepository extends JpaRepository<HourlyOrderMetric, Long> {

    Optional<HourlyOrderMetric> findByHourBucketAndStatus(String hourBucket, String status);

    List<HourlyOrderMetric> findAllByOrderByHourBucketAsc();
}
