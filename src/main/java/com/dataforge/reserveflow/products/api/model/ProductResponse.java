package com.dataforge.reserveflow.products.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        long version,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        InventoryResponse inventory,

        Instant createdAt,
        Instant updatedAt
) {}
