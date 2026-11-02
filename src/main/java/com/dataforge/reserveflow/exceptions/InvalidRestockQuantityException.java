package com.dataforge.reserveflow.exceptions;

public class InvalidRestockQuantityException extends RuntimeException {

    public InvalidRestockQuantityException(int quantity) {
        super("Restock quantity must be positive: " + quantity);
    }
}