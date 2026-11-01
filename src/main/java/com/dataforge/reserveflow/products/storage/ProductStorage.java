package com.dataforge.reserveflow.products.storage;

import com.dataforge.reserveflow.products.model.Product;

import java.util.Optional;
import java.util.UUID;

public interface ProductStorage {
    Optional<Product> findById(UUID productId);
    Optional<Product> findWithInventoryById(UUID productId);
    boolean existsBySku(String sku);
    void create(Product product);
}