package com.dataforge.reserveflow.exceptions;

public class ProductAlreadyExistsException extends RuntimeException {

    public ProductAlreadyExistsException(String sku) {
        super("Product with SKU already exists: " + sku);
    }
}