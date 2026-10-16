package com.dataforge.reserveflow.products.storage;

import com.dataforge.reserveflow.products.model.Product;

public interface ProductStorage {
    boolean existsBySku(String sku);
    void create(Product product);
}