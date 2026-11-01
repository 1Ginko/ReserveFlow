package com.dataforge.reserveflow.data.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ProductDetailsProjection {
    UUID getProductId();
    String getSku();
    String getName();
    String getDescription();
    BigDecimal getPrice();
    String getCurrencyCode();
    boolean isActive();
    Instant getCreatedAt();
    Instant getUpdatedAt();

    int getOnHandQuantity();
    int getReservedQuantity();
    long getInventoryVersion();
    Instant getInventoryUpdatedAt();
}