package com.microservices.pro.orderservice;

import com.microservices.pro.orderservice.analytics.AnalyticsProcessedEventRepository;
import com.microservices.pro.orderservice.analytics.HourlyOrderMetricRepository;
import com.microservices.pro.orderservice.analytics.OrderAnalyticsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class OrderServiceApplicationTests {

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private OutboxRepository outboxRepository;

    @MockBean
    private OrderAnalyticsRepository orderAnalyticsRepository;

    @MockBean
    private AnalyticsProcessedEventRepository analyticsProcessedEventRepository;

    @MockBean
    private HourlyOrderMetricRepository hourlyOrderMetricRepository;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaStringTemplate;

    @MockBean
    private OrderServiceTokenClient orderServiceTokenClient;

    @Test
    void contextLoads() {
    }
}
