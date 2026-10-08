package com.microservices.pro.inventoryservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * InventoryService backed by Spring Data JPA and PostgreSQL.
 */
@Service
public class InventoryService {

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository stockReservationRepository;

    @Autowired
    public InventoryService(StockItemRepository stockItemRepository,
                            StockReservationRepository stockReservationRepository) {
        this.stockItemRepository = stockItemRepository;
        this.stockReservationRepository = stockReservationRepository;
    }

    @Transactional(readOnly = true)
    public StockCheckResponse checkStock(String productId, int requestedQty) {
        StockItem item = stockItemRepository.findById(productId)
                .orElse(new StockItem(productId, 0, 0));
        boolean available = item.hasStock(requestedQty);
        return new StockCheckResponse(productId, requestedQty,
                available, item.getAvailableQuantity() - item.getReservedQuantity());
    }

    @Transactional(readOnly = true)
    public Optional<StockItem> getStock(String productId) {
        return stockItemRepository.findById(productId);
    }

    @Transactional
    public StockItem updateStock(String productId, int quantity) {
        StockItem item = stockItemRepository.findById(productId)
                .orElse(new StockItem(productId, 0, 0));
        item.setAvailableQuantity(quantity);
        return stockItemRepository.save(item);
    }

    @Transactional
    public void reserveStock(String productId, int quantity, String orderId) {
        // Idempotency: if reservation already exists for this orderId, do not decrement/reserve twice
        if (stockReservationRepository.findById(orderId).isPresent()) {
            return;
        }

        StockItem item = stockItemRepository.findById(productId)
                .orElse(new StockItem(productId, 0, 0));
        if (!item.hasStock(quantity)) {
            throw new InsufficientStockException(
                    "Cannot reserve " + quantity + " of " + productId + " for order " + orderId);
        }
        item.setReservedQuantity(item.getReservedQuantity() + quantity);
        stockItemRepository.save(item);
        stockReservationRepository.save(new StockReservation(orderId, productId, quantity));
    }

    @Transactional
    public void releaseStock(String orderId) {
        Optional<StockReservation> resOpt = stockReservationRepository.findById(orderId);
        if (resOpt.isEmpty()) {
            return; // idempotent no-op
        }
        StockReservation reservation = resOpt.get();
        stockReservationRepository.delete(reservation);

        stockItemRepository.findById(reservation.getProductId()).ifPresent(item -> {
            int newReserved = Math.max(0, item.getReservedQuantity() - reservation.getQuantity());
            item.setReservedQuantity(newReserved);
            stockItemRepository.save(item);
        });
    }

    @Transactional
    public void resetForTesting(String productId, int available, int reserved) {
        stockItemRepository.save(new StockItem(productId, available, reserved));
    }
}
