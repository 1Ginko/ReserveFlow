package com.dataforge.reserveflow.exceptions;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(
            UUID productId,
            int requestedQuantity,
            int availableQuantity
    ) {
        super(
                "Insufficient stock for product " + productId +
                ": requested=" + requestedQuantity +
                ", available=" + availableQuantity
        );
    }
}
