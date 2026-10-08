package com.microservices.pro.inventoryservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * InventoryServiceTest — Unit tests for stock checks, reservations, compensations, and domain models.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @InjectMocks
    private InventoryService inventoryService;

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Test
    void checkStock_returnsAvailableTrue_whenStockSufficient() {
        when(stockItemRepository.findById("PROD-001"))
                .thenReturn(Optional.of(new StockItem("PROD-001", 100, 0)));

        StockCheckResponse response = inventoryService.checkStock("PROD-001", 10);

        assertThat(response.available()).isTrue();
        assertThat(response.remainingStock()).isEqualTo(100);
    }

    @Test
    void checkStock_returnsAvailableFalse_whenStockInsufficient() {
        when(stockItemRepository.findById("PROD-003"))
                .thenReturn(Optional.of(new StockItem("PROD-003", 0, 0)));

        StockCheckResponse response = inventoryService.checkStock("PROD-003", 1);

        assertThat(response.available()).isFalse();
    }

    @Test
    void checkStock_createsDefaultZeroItem_whenNotFound() {
        when(stockItemRepository.findById("PROD-999"))
                .thenReturn(Optional.empty());

        StockCheckResponse response = inventoryService.checkStock("PROD-999", 5);

        assertThat(response.available()).isFalse();
        assertThat(response.remainingStock()).isEqualTo(0);
    }

    @Test
    void getStock_returnsOptional() {
        when(stockItemRepository.findById("PROD-001"))
                .thenReturn(Optional.of(new StockItem("PROD-001", 50, 5)));

        Optional<StockItem> result = inventoryService.getStock("PROD-001");

        assertThat(result).isPresent();
        assertThat(result.get().getAvailableQuantity()).isEqualTo(50);
    }

    @Test
    void updateStock_updatesExistingItem() {
        StockItem existing = new StockItem("PROD-001", 50, 5);
        when(stockItemRepository.findById("PROD-001")).thenReturn(Optional.of(existing));
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockItem updated = inventoryService.updateStock("PROD-001", 100);

        assertThat(updated.getAvailableQuantity()).isEqualTo(100);
        verify(stockItemRepository).save(existing);
    }

    @Test
    void updateStock_createsNewItemIfMissing() {
        when(stockItemRepository.findById("PROD-NEW")).thenReturn(Optional.empty());
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockItem updated = inventoryService.updateStock("PROD-NEW", 30);

        assertThat(updated.getProductId()).isEqualTo("PROD-NEW");
        assertThat(updated.getAvailableQuantity()).isEqualTo(30);
    }

    @Test
    void reserveStock_skipsIfReservationAlreadyExists() {
        when(stockReservationRepository.findById("order-100"))
                .thenReturn(Optional.of(new StockReservation("order-100", "PROD-001", 2)));

        inventoryService.reserveStock("PROD-001", 2, "order-100");

        verify(stockItemRepository, never()).save(any());
        verify(stockReservationRepository, never()).save(any());
    }

    @Test
    void reserveStock_throwsInsufficientStockException_whenNotEnoughStock() {
        when(stockReservationRepository.findById("order-101")).thenReturn(Optional.empty());
        when(stockItemRepository.findById("PROD-001"))
                .thenReturn(Optional.of(new StockItem("PROD-001", 5, 4))); // 5-4 = 1 available

        assertThatThrownBy(() -> inventoryService.reserveStock("PROD-001", 2, "order-101"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Cannot reserve 2 of PROD-001 for order order-101");

        verify(stockReservationRepository, never()).save(any());
    }

    @Test
    void reserveStock_successfullyReserves_whenStockAvailable() {
        when(stockReservationRepository.findById("order-102")).thenReturn(Optional.empty());
        StockItem item = new StockItem("PROD-001", 20, 0);
        when(stockItemRepository.findById("PROD-001")).thenReturn(Optional.of(item));

        inventoryService.reserveStock("PROD-001", 5, "order-102");

        assertThat(item.getReservedQuantity()).isEqualTo(5);
        verify(stockItemRepository).save(item);
        verify(stockReservationRepository).save(any(StockReservation.class));
    }

    @Test
    void releaseStock_skipsIfNoReservationFound() {
        when(stockReservationRepository.findById("order-none")).thenReturn(Optional.empty());

        inventoryService.releaseStock("order-none");

        verify(stockReservationRepository, never()).delete(any());
        verify(stockItemRepository, never()).save(any());
    }

    @Test
    void releaseStock_successfullyReleasesAndDeletesReservation() {
        StockReservation reservation = new StockReservation("order-103", "PROD-001", 5);
        when(stockReservationRepository.findById("order-103")).thenReturn(Optional.of(reservation));
        StockItem item = new StockItem("PROD-001", 20, 5);
        when(stockItemRepository.findById("PROD-001")).thenReturn(Optional.of(item));

        inventoryService.releaseStock("order-103");

        verify(stockReservationRepository).delete(reservation);
        assertThat(item.getReservedQuantity()).isEqualTo(0);
        verify(stockItemRepository).save(item);
    }

    @Test
    void resetForTesting_savesNewStockItem() {
        inventoryService.resetForTesting("PROD-RESET", 50, 10);
        verify(stockItemRepository).save(any(StockItem.class));
    }

    @Test
    void hasStock_accountsForReservedQuantity() {
        StockItem item = new StockItem("X", 10, 3);

        assertThat(item.hasStock(5)).isTrue();   // 10-3=7 >= 5
        assertThat(item.hasStock(8)).isFalse();  // 10-3=7 < 8
    }

    @Test
    void stockItem_and_stockReservation_modelsCoverage() {
        StockItem emptyItem = new StockItem();
        emptyItem.setProductId("ITEM-1");
        emptyItem.setAvailableQuantity(15);
        emptyItem.setReservedQuantity(3);

        assertThat(emptyItem.getProductId()).isEqualTo("ITEM-1");
        assertThat(emptyItem.getAvailableQuantity()).isEqualTo(15);
        assertThat(emptyItem.getReservedQuantity()).isEqualTo(3);

        StockReservation emptyRes = new StockReservation();
        emptyRes.setOrderId("ord-res");
        emptyRes.setProductId("ITEM-1");
        emptyRes.setQuantity(3);

        assertThat(emptyRes.getOrderId()).isEqualTo("ord-res");
        assertThat(emptyRes.getProductId()).isEqualTo("ITEM-1");
        assertThat(emptyRes.getQuantity()).isEqualTo(3);
    }
}
