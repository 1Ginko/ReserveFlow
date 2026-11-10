package com.dataforge.reserveflow.products.model;

import java.time.Instant;

public record ProductInventory(
        int onHandQuantity,
        int reservedQuantity,
        long version,
        Instant updatedAt
) {

    public int availableQuantity() {
        return onHandQuantity - reservedQuantity;
    }
}