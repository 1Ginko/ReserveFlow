package com.dataforge.reserveflow.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = ProductEntity.PRODUCT_TABLE)
public class ProductEntity {

    @Id
    @Column(name = PRODUCT_ID, nullable = false, updatable = false)
    private UUID productId;

    @Column(name = PRODUCT_SKU, nullable = false, length = 64)
    private String sku;

    @Column(name = PRODUCT_NAME, nullable = false, length = 120)
    private String name;

    @Column(name = PRODUCT_DESCRIPTION, length = 1000)
    private String description;

    @Column(name = PRODUCT_PRICE, nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = PRODUCT_CURRENCY_CODE, nullable = false, length = 3)
    private String currencyCode;

    @Column(name = PRODUCT_ACTIVE, nullable = false)
    private boolean active;

    @Column(name = PRODUCT_CREATED_AT, nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = PRODUCT_UPDATED_AT, nullable = false)
    private Instant updatedAt;

    /** Required by JPA to create an entity when reading it from the database. */
    protected ProductEntity() {}

    public ProductEntity(
            UUID productId,
            String sku,
            String name,
            String description,
            BigDecimal price,
            String currencyCode,
            Instant createdAt
    ) {
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currencyCode = currencyCode;
        this.active = true;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    static final String PRODUCT_TABLE = "products";
    static final String PRODUCT_ID = "product_id";
    static final String PRODUCT_SKU = "sku";
    static final String PRODUCT_NAME = "product_name";
    static final String PRODUCT_DESCRIPTION = "product_description";
    static final String PRODUCT_PRICE = "price";
    static final String PRODUCT_CURRENCY_CODE = "currency_code";
    static final String PRODUCT_ACTIVE = "active";
    static final String PRODUCT_CREATED_AT = "created_at";
    static final String PRODUCT_UPDATED_AT = "updated_at";
}