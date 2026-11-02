package com.dataforge.reserveflow.exceptions;

import java.util.UUID;

public class InventoryCapacityExceededException extends RuntimeException {

    public InventoryCapacityExceededException(UUID productId) {
        super("On-hand quantity must not exceed " + Integer.MAX_VALUE + " for product: " + productId);
    }
}