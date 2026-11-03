package com.dataforge.reserveflow.products.api;

import com.dataforge.reserveflow.products.api.model.params.ProductListQueryParams;
import com.dataforge.reserveflow.products.api.model.request.CreateProductRequest;
import com.dataforge.reserveflow.products.api.model.response.InventoryResponse;
import com.dataforge.reserveflow.products.api.model.response.ProductPageResponse;
import com.dataforge.reserveflow.products.api.model.response.ProductResponse;
import com.dataforge.reserveflow.products.api.model.request.RestockProductRequest;
import com.dataforge.reserveflow.products.api.model.request.UpdateProductRequest;
import com.dataforge.reserveflow.products.commands.RestockProductCommand;
import com.dataforge.reserveflow.products.commands.UpdateProductCommand;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import com.dataforge.reserveflow.products.model.ProductInventory;
import com.dataforge.reserveflow.products.model.ProductPage;
import com.dataforge.reserveflow.products.queries.ProductListQuery;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
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

    public static ProductListQuery toQuery(ProductListQueryParams params) {
        return new ProductListQuery(
                params.page(),
                params.size(),
                params.active()
        );
    }

    public static ProductPageResponse toResponse(ProductPage page, Boolean active) {
        List<ProductResponse> items = page.items().stream()
                .map(ProductControllerMapper::toResponse)
                .toList();

        long totalPages = Math.ceilDiv(page.totalElements(), page.size());
        long nextPageNumber = (long) page.page() + 1;
        String nextPage = null;

        if (nextPageNumber < totalPages) {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromPath("/api/products")
                    .queryParam("page", nextPageNumber)
                    .queryParam("size", page.size());

            if (active != null) {
                builder.queryParam("active", active);
            }

            nextPage = builder.build().encode().toUriString();
        }

        return new ProductPageResponse(
                items,
                page.page(),
                page.size(),
                page.totalElements(),
                totalPages,
                nextPage
        );
    }
}
