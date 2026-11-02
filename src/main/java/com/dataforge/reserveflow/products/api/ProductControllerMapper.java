package com.dataforge.reserveflow.products.api;

import com.dataforge.reserveflow.products.api.model.CreateProductRequest;
import com.dataforge.reserveflow.products.api.model.InventoryResponse;
import com.dataforge.reserveflow.products.api.model.ProductResponse;
import com.dataforge.reserveflow.products.api.model.RestockProductRequest;
import com.dataforge.reserveflow.products.api.model.UpdateProductRequest;
import com.dataforge.reserveflow.products.commands.RestockProductCommand;
import com.dataforge.reserveflow.products.commands.UpdateProductCommand;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import com.dataforge.reserveflow.products.model.ProductInventory;

import java.util.UUID;

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

    public static RestockProductCommand toCommand(
            UUID productId,
            RestockProductRequest request
    ) {
        return new RestockProductCommand(
                productId,
                request.quantity(),
                request.version()
        );
    }

    public static UpdateProductCommand toCommand(
            UUID productId,
            UpdateProductRequest request
    ) {
        return new UpdateProductCommand(
                productId,
                request.name(),
                request.description(),
                request.price(),
                request.currencyCode(),
                request.active(),
                request.version()
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
                product.version(),
                inventory,
                product.createdAt(),
                product.updatedAt()
        );
    }

    public static InventoryResponse toInventoryResponse(ProductInventory inventory) {
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
