package com.dataforge.reserveflow.products.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID productId,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currencyCode,
        boolean active,
        int onHandQuantity,
        int reservedQuantity,
        int availableQuantity,
        long inventoryVersion,
        Instant createdAt,
        Instant updatedAt
) {}