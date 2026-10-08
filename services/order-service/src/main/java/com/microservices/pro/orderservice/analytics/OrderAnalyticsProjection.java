package com.microservices.pro.orderservice.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.pro.orderservice.events.OrderCancelledEvent;
import com.microservices.pro.orderservice.events.OrderConfirmedEvent;
import com.microservices.pro.orderservice.events.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * OrderAnalyticsProjection — Event-driven projection that consumes order domain events,
 * performs idempotent updates against the persistent analytics read model, and increments
 * Prometheus Micrometer metrics.
 */
@Component
public class OrderAnalyticsProjection {

    private static final Logger log = LoggerFactory.getLogger(OrderAnalyticsProjection.class);
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00:00'Z'").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private OrderAnalyticsRepository orderAnalyticsRepository;

    @Autowired(required = false)
    private AnalyticsProcessedEventRepository analyticsProcessedEventRepository;

    @Autowired(required = false)
    private HourlyOrderMetricRepository hourlyOrderMetricRepository;

    @Autowired(required = false)
    private OrderAnalyticsMetrics metrics;

    public OrderAnalyticsProjection() {
    }

    public OrderAnalyticsProjection(OrderAnalyticsRepository orderAnalyticsRepository,
                                    AnalyticsProcessedEventRepository analyticsProcessedEventRepository,
                                    HourlyOrderMetricRepository hourlyOrderMetricRepository,
                                    OrderAnalyticsMetrics metrics) {
        this.orderAnalyticsRepository = orderAnalyticsRepository;
        this.analyticsProcessedEventRepository = analyticsProcessedEventRepository;
        this.hourlyOrderMetricRepository = hourlyOrderMetricRepository;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "order-events", groupId = "order-analytics-group")
    @Transactional
    public void onOrderEvent(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            String eventType = root.path("eventType").asText("");

            if ("OrderPlacedEvent".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("productId") && root.has("amount") && !root.has("transactionId") && !root.has("reason"))) {
                OrderPlacedEvent event = objectMapper.treeToValue(root, OrderPlacedEvent.class);
                processOrderPlaced(event);
            } else if ("OrderConfirmedEvent".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("transactionId"))) {
                OrderConfirmedEvent event = objectMapper.treeToValue(root, OrderConfirmedEvent.class);
                processOrderConfirmed(event);
            } else if ("OrderCancelledEvent".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("reason") && !root.has("transactionId"))) {
                OrderCancelledEvent event = objectMapper.treeToValue(root, OrderCancelledEvent.class);
                processOrderCancelled(event);
            } else {
                log.debug("[ANALYTICS] Ignored unrecognized event: {}", rawJson);
            }
        } catch (Exception e) {
            log.error("[ANALYTICS] Failed to parse and process event on order-events: {}", rawJson, e);
            throw new RuntimeException("Error processing analytics event", e);
        }
    }

    @Transactional
    public void processOrderPlaced(OrderPlacedEvent event) {
        if (analyticsProcessedEventRepository == null || orderAnalyticsRepository == null) {
            return;
        }

        String eventKey = event.orderId() + ":OrderPlaced";
        if (analyticsProcessedEventRepository.existsById(eventKey)) {
            log.info("[ANALYTICS] Duplicate event suppressed: {}", eventKey);
            return;
        }

        Instant now = Instant.now();
        analyticsProcessedEventRepository.save(new AnalyticsProcessedEvent(eventKey, event.orderId(), "OrderPlaced", now));

        OrderAnalyticsItem item = orderAnalyticsRepository.findById(event.orderId())
                .orElse(new OrderAnalyticsItem(
                        event.orderId(),
                        event.productId(),
                        event.customerId(),
                        event.amount() != null ? event.amount() : BigDecimal.ZERO,
                        "PENDING",
                        now,
                        now
                ));
        item.setStatus("PENDING");
        item.setUpdatedAt(now);
        orderAnalyticsRepository.save(item);

        updateHourlyMetrics(now, "PENDING", 1, BigDecimal.ZERO);
        if (metrics != null) {
            metrics.recordOrderPlaced();
        }
        log.info("[ANALYTICS] Projected OrderPlaced for order {}", event.orderId());
    }

    @Transactional
    public void processOrderConfirmed(OrderConfirmedEvent event) {
        if (analyticsProcessedEventRepository == null || orderAnalyticsRepository == null) {
            return;
        }

        String eventKey = event.orderId() + ":OrderConfirmed";
        if (analyticsProcessedEventRepository.existsById(eventKey)) {
            log.info("[ANALYTICS] Duplicate event suppressed: {}", eventKey);
            return;
        }

        Instant now = Instant.now();
        analyticsProcessedEventRepository.save(new AnalyticsProcessedEvent(eventKey, event.orderId(), "OrderConfirmed", now));

        OrderAnalyticsItem item = orderAnalyticsRepository.findById(event.orderId())
                .orElse(new OrderAnalyticsItem(
                        event.orderId(),
                        null,
                        event.customerId(),
                        BigDecimal.ZERO,
                        "CONFIRMED",
                        now,
                        now
                ));
        item.setStatus("CONFIRMED");
        item.setUpdatedAt(now);
        orderAnalyticsRepository.save(item);

        BigDecimal revenue = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
        updateHourlyMetrics(now, "CONFIRMED", 1, revenue);
        if (metrics != null) {
            metrics.recordOrderConfirmed(revenue);
        }
        log.info("[ANALYTICS] Projected OrderConfirmed for order {}, revenue={}", event.orderId(), revenue);
    }

    @Transactional
    public void processOrderCancelled(OrderCancelledEvent event) {
        if (analyticsProcessedEventRepository == null || orderAnalyticsRepository == null) {
            return;
        }

        String eventKey = event.orderId() + ":OrderCancelled";
        if (analyticsProcessedEventRepository.existsById(eventKey)) {
            log.info("[ANALYTICS] Duplicate event suppressed: {}", eventKey);
            return;
        }

        Instant now = Instant.now();
        analyticsProcessedEventRepository.save(new AnalyticsProcessedEvent(eventKey, event.orderId(), "OrderCancelled", now));

        OrderAnalyticsItem item = orderAnalyticsRepository.findById(event.orderId())
                .orElse(new OrderAnalyticsItem(
                        event.orderId(),
                        null,
                        event.customerId(),
                        BigDecimal.ZERO,
                        "CANCELLED",
                        now,
                        now
                ));
        item.setStatus("CANCELLED");
        item.setUpdatedAt(now);
        orderAnalyticsRepository.save(item);

        updateHourlyMetrics(now, "CANCELLED", 1, BigDecimal.ZERO);
        if (metrics != null) {
            metrics.recordOrderCancelled(event.reason());
        }
        log.info("[ANALYTICS] Projected OrderCancelled for order {}", event.orderId());
    }

    private void updateHourlyMetrics(Instant timestamp, String status, long countIncrement, BigDecimal revenueIncrement) {
        if (hourlyOrderMetricRepository == null) {
            return;
        }
        String hourBucket = HOUR_FORMATTER.format(timestamp);
        HourlyOrderMetric metric = hourlyOrderMetricRepository.findByHourBucketAndStatus(hourBucket, status)
                .orElse(new HourlyOrderMetric(hourBucket, status, 0, BigDecimal.ZERO));

        metric.setOrderCount(metric.getOrderCount() + countIncrement);
        metric.setRevenue(metric.getRevenue().add(revenueIncrement != null ? revenueIncrement : BigDecimal.ZERO));
        hourlyOrderMetricRepository.save(metric);
    }
}
