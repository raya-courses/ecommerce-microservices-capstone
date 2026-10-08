package com.microservices.pro.inventoryservice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * InventoryController — Session 6 / Phase 4 Capstone alignment.
 *
 * Endpoints:
 *   GET /api/v1/inventory/check?productId=&quantity= (internal/service check)
 *   GET /api/v1/inventory/{productId}                (stock query)
 *   PUT /api/v1/inventory/{productId}                (ADMIN stock adjustment)
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/check")
    public ResponseEntity<StockCheckResponse> checkStock(
            @RequestParam String productId,
            @RequestParam int quantity) {
        StockCheckResponse response = inventoryService.checkStock(productId, quantity);
        if (!response.available()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<StockItem> getStock(@PathVariable String productId) {
        return inventoryService.getStock(productId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{productId}")
    public ResponseEntity<StockItem> updateStock(
            @PathVariable String productId,
            @RequestBody StockAdjustmentRequest request) {
        StockItem updated = inventoryService.updateStock(productId, request.quantity());
        return ResponseEntity.ok(updated);
    }
}
