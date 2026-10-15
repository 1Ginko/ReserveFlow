package com.dataforge.reserveflow.products.api;

import com.dataforge.reserveflow.products.api.model.CreateProductRequest;
import com.dataforge.reserveflow.products.api.model.ProductResponse;
import com.dataforge.reserveflow.products.catalog.model.Product;
import com.dataforge.reserveflow.products.commands.CreateProductCommand;

public final class ProductControllerMapper {

    private ProductControllerMapper() {}

    public static CreateProductCommand toCommand(
            CreateProductRequest request
    ) {
        return new CreateProductCommand(
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.currencyCode(),
                request.initialQuantity()
        );
    }

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.productId(),
                product.sku(),
                product.name(),
                product.description(),
                product.price(),
                product.currencyCode(),
                product.active(),
                product.onHandQuantity(),
                product.reservedQuantity(),
                product.availableQuantity(),
                product.inventoryVersion(),
                product.createdAt(),
                product.updatedAt()
        );
    }
}