package com.dataforge.reserveflow.exceptions;

import java.util.UUID;

public class ProductConcurrentUpdateException extends RuntimeException {

    public ProductConcurrentUpdateException(UUID productId) {
        super("Product was concurrently updated: " + productId);
    }
}