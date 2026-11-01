package com.dataforge.reserveflow.products.api;

import com.dataforge.reserveflow.products.api.model.CreateProductRequest;
import com.dataforge.reserveflow.products.api.model.InventoryResponse;
import com.dataforge.reserveflow.products.api.model.ProductResponse;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import com.dataforge.reserveflow.products.model.ProductInventory;

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
        InventoryResponse inventory = toInventoryResponse(product.inventory());
        return new ProductResponse(
                product.productId(),
                product.sku(),
                product.name(),
                product.description(),
                product.price(),
                product.currencyCode(),
                product.active(),
                inventory,
                product.createdAt(),
                product.updatedAt()
        );
    }

    private static InventoryResponse toInventoryResponse(ProductInventory inventory) {
        if (inventory == null) return null;

        return new InventoryResponse(
                inventory.onHandQuantity(),
                inventory.reservedQuantity(),
                inventory.availableQuantity(),
                inventory.version(),
                inventory.updatedAt()
        );
    }
}