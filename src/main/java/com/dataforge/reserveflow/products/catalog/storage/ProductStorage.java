package com.dataforge.reserveflow.products.catalog.storage;

import com.dataforge.reserveflow.products.catalog.model.Product;

public interface ProductStorage {
    boolean existsBySku(String sku);
    void create(Product product);
}