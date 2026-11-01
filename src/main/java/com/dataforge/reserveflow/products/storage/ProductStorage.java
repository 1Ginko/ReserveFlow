package com.dataforge.reserveflow.products.storage;

import com.dataforge.reserveflow.products.commands.UpdateProductCommand;
import com.dataforge.reserveflow.products.model.Product;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProductStorage {
    void create(Product product);
    Product update(UpdateProductCommand command, Instant updatedAt);
    Optional<Product> findById(UUID productId);
    Optional<Product> findWithInventoryById(UUID productId);
    boolean existsBySku(String sku);
}