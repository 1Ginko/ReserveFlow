package com.dataforge.reserveflow.products.api.model.response;

import java.time.Instant;

public record InventoryResponse(
        int onHandQuantity,
        int reservedQuantity,
        int availableQuantity,
        long version,
        Instant updatedAt
) {}