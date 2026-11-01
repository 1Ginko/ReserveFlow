package com.dataforge.reserveflow.products;

import com.dataforge.reserveflow.exceptions.ProductAlreadyExistsException;
import com.dataforge.reserveflow.exceptions.ProductNotFoundException;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.model.ProductInventory;
import com.dataforge.reserveflow.products.storage.ProductStorage;
import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductStorage productStorage;
    private final Clock clock;

    public ProductService(
            ProductStorage productStorage,
            Clock clock
    ) {
        this.productStorage = productStorage;
        this.clock = clock;
    }

    public Product findById(UUID productId, boolean includeInventory) {
        Optional<Product> product = includeInventory
                ? productStorage.findWithInventoryById(productId)
                : productStorage.findById(productId);

        return product.orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public Product create(CreateProductCommand command) {
        String sku = command.sku()
                .trim()
                .toUpperCase(Locale.ROOT);
        String currencyCode = command.currencyCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (productStorage.existsBySku(sku)) {
            throw new ProductAlreadyExistsException(sku);
        }

        Instant now = clock.instant();
        ProductInventory inventory = new ProductInventory(
                command.initialQuantity(),
                0,
                0,
                now
        );
        Product product = new Product(
                UUID.randomUUID(),
                sku,
                command.name().trim(),
                command.description(),
                command.price(),
                currencyCode,
                true,
                inventory,
                now,
                now
        );
        productStorage.create(product);

        return product;
    }
}