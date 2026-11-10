package com.dataforge.reserveflow.products.model;

import java.util.List;

public record ProductPage(
        List<Product> items,
        int page,
        int size,
        long totalElements
) {
    public ProductPage {
        items = List.copyOf(items);
    }
}