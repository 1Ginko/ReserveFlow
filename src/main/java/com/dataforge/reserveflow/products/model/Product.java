package com.dataforge.reserveflow.products.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Product(
        UUID productId,

        String sku,
        String name,
        String description,

        BigDecimal price,
        String currencyCode,

        boolean active,

        ProductInventory inventory,

        Instant createdAt,
        Instant updatedAt
) {}