package com.dataforge.reserveflow.exceptions;

import java.util.UUID;

public class InventoryConcurrentUpdateException extends RuntimeException {

    public InventoryConcurrentUpdateException(UUID productId) {
        super("Inventory was concurrently updated: " + productId);
    }
}