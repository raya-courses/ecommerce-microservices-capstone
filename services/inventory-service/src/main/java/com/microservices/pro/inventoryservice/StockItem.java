package com.microservices.pro.inventoryservice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * StockItem entity backed by PostgreSQL stock_items table.
 */
@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    public StockItem() {}

    public StockItem(String productId, int availableQuantity, int reservedQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
    }

    public boolean hasStock(int requested) {
        return availableQuantity - reservedQuantity >= requested;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public String productId() {
        return productId;
    }

    public int availableQuantity() {
        return availableQuantity;
    }

    public int reservedQuantity() {
        return reservedQuantity;
    }
}
