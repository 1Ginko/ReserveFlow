package com.dataforge.reserveflow.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = ProductInventoryEntity.PRODUCT_INVENTORY_TABLE)
public class ProductInventoryEntity {

    @Id
    @Column(name = PRODUCT_ID, nullable = false, updatable = false)
    private UUID productId;

    @Column(name = ON_HAND_QUANTITY, nullable = false)
    private int onHandQuantity;

    @Column(name = RESERVED_QUANTITY, nullable = false)
    private int reservedQuantity;

    @Version
    @Column(name = INVENTORY_VERSION, nullable = false)
    private long version;

    @Column(name = UPDATED_AT, nullable = false)
    private Instant updatedAt;

    /** Required by JPA to create an entity when reading it from the database. */
    protected ProductInventoryEntity() {}

    public ProductInventoryEntity(
            UUID productUUID,
            int onHandQuantity,
            Instant updatedAt
    ) {
        this.productId = productUUID;
        this.onHandQuantity = onHandQuantity;
        this.reservedQuantity = 0;
        this.updatedAt = updatedAt;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getOnHandQuantity() {
        return onHandQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public int getAvailableQuantity() {
        return onHandQuantity - reservedQuantity;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    static final String PRODUCT_INVENTORY_TABLE = "product_inventory";
    static final String PRODUCT_ID = "product_id";
    static final String ON_HAND_QUANTITY = "on_hand_quantity";
    static final String RESERVED_QUANTITY = "reserved_quantity";
    static final String INVENTORY_VERSION = "version";
    static final String UPDATED_AT = "updated_at";
}