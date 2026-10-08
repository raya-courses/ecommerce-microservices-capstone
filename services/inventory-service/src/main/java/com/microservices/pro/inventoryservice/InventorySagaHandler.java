package com.microservices.pro.inventoryservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.pro.inventoryservice.events.InventoryReleasedEvent;
import com.microservices.pro.inventoryservice.events.InventoryReservationFailedEvent;
import com.microservices.pro.inventoryservice.events.InventoryReservedEvent;
import com.microservices.pro.inventoryservice.events.OrderPlacedEvent;
import com.microservices.pro.inventoryservice.events.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * InventorySagaHandler — Session 7 / Phase 5 Saga Choreography & Compensation.
 *
 * Listens on:
 *   - "order-events", groupId "inventory-service" — reserves stock
 *   - "payment-events", groupId "inventory-compensation" — releases stock on PaymentFailed
 */
@Service
public class InventorySagaHandler {

    private static final Logger log = LoggerFactory.getLogger(InventorySagaHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    public InventorySagaHandler() {}

    public InventorySagaHandler(InventoryService inventoryService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.inventoryService = inventoryService;
        this.kafkaTemplate    = kafkaTemplate;
    }

    /**
     * Step 2 of the Saga: reserve inventory when an order is placed.
     */
    @KafkaListener(topics = "order-events", groupId = "inventory-service")
    public void handleOrderEvents(String rawEvent) {
        try {
            JsonNode root = objectMapper.readTree(rawEvent);
            String eventType = root.path("eventType").asText("");

            boolean isOrderPlaced = "OrderPlacedEvent".equalsIgnoreCase(eventType)
                    || "OrderPlaced".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("productId") && root.has("quantity") && root.has("orderId"));

            if (!isOrderPlaced) {
                log.debug("[SAGA] Ignored non-OrderPlaced event on order-events: {}", rawEvent);
                return;
            }

            OrderPlacedEvent event = objectMapper.treeToValue(root, OrderPlacedEvent.class);
            handleOrderPlaced(event);
        } catch (Exception e) {
            log.error("[SAGA] Failed to process event on order-events: {}", rawEvent, e);
            throw new RuntimeException("Error processing order event", e);
        }
    }

    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("[SAGA] Handling OrderPlaced for order: {}", event.orderId());
        try {
            inventoryService.reserveStock(event.productId(), event.quantity(), event.orderId());
            kafkaTemplate.send("inventory-events", event.orderId(),
                    new InventoryReservedEvent(event.orderId(), event.productId(), event.quantity()));
            log.info("[SAGA] Inventory reserved for order: {}", event.orderId());
        } catch (InsufficientStockException e) {
            kafkaTemplate.send("inventory-events", event.orderId(),
                    new InventoryReservationFailedEvent(event.orderId(), e.getMessage()));
            log.warn("[SAGA] Inventory reservation FAILED for order: {}", event.orderId());
        }
    }

    /**
     * Compensation: release inventory when payment fails.
     */
    @KafkaListener(topics = "payment-events", groupId = "inventory-compensation")
    public void handlePaymentFailed(String rawEvent) {
        try {
            JsonNode root = objectMapper.readTree(rawEvent);
            String eventType = root.path("eventType").asText("");

            boolean isPaymentFailed = "PaymentFailedEvent".equalsIgnoreCase(eventType)
                    || "PaymentFailed".equalsIgnoreCase(eventType)
                    || (eventType.isEmpty() && root.has("reason") && root.has("orderId") && !root.has("transactionId"));

            if (!isPaymentFailed) {
                return; // ignore PaymentCompleted and any other event on this topic
            }

            PaymentFailedEvent event = objectMapper.treeToValue(root, PaymentFailedEvent.class);
            inventoryService.releaseStock(event.orderId());  // undo the reservation (idempotent)
            kafkaTemplate.send("inventory-events", event.orderId(),
                    new InventoryReleasedEvent(event.orderId()));
            log.info("[SAGA] COMPENSATION: Inventory released for order: {}", event.orderId());
        } catch (Exception e) {
            log.error("[SAGA] Failed to process payment failure compensation: {}", rawEvent, e);
            throw new RuntimeException("Error handling payment failure compensation", e);
        }
    }
}
