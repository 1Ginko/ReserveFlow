package com.dataforge.reserveflow.data.storage;

import com.dataforge.reserveflow.data.entity.ProductEntity;
import com.dataforge.reserveflow.data.entity.ProductInventoryEntity;
import com.dataforge.reserveflow.data.repository.ProductInventoryRepository;
import com.dataforge.reserveflow.data.repository.ProductRepository;
import com.dataforge.reserveflow.exceptions.InventoryCapacityExceededException;
import com.dataforge.reserveflow.exceptions.InventoryConcurrentUpdateException;
import com.dataforge.reserveflow.exceptions.ProductAlreadyExistsException;
import com.dataforge.reserveflow.exceptions.ProductConcurrentUpdateException;
import com.dataforge.reserveflow.exceptions.ProductNotFoundException;
import com.dataforge.reserveflow.products.commands.RestockProductCommand;
import com.dataforge.reserveflow.products.commands.UpdateProductCommand;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.model.ProductInventory;
import com.dataforge.reserveflow.products.storage.ProductStorage;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductStorageImpl implements ProductStorage {

    private final ProductRepository productRepository;
    private final ProductInventoryRepository inventoryRepository;

    public ProductStorageImpl(
            ProductRepository productRepository,
            ProductInventoryRepository inventoryRepository
    ) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public Optional<Product> findById(UUID productId) {
        return productRepository.findById(productId)
                .map(entity -> new Product(
                        entity.getProductId(),
                        entity.getSku(),
                        entity.getName(),
                        entity.getDescription(),
                        entity.getPrice(),
                        entity.getCurrencyCode(),
                        entity.isActive(),
                        entity.getVersion(),
                        null,
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()
                ));
    }

    @Override
    public Optional<Product> findWithInventoryById(UUID productId) {
        return productRepository.findWithInventoryById(productId)
                .map(projection -> {
                    ProductInventory inventory = new ProductInventory(
                            projection.getOnHandQuantity(),
                            projection.getReservedQuantity(),
                            projection.getInventoryVersion(),
                            projection.getInventoryUpdatedAt()
                    );

                    return new Product(
                            projection.getProductId(),
                            projection.getSku(),
                            projection.getName(),
                            projection.getDescription(),
                            projection.getPrice(),
                            projection.getCurrencyCode(),
                            projection.isActive(),
                            projection.getVersion(),
                            inventory,
                            projection.getCreatedAt(),
                            projection.getUpdatedAt()
                    );
                });
    }

    @Override
    public boolean existsBySku(String sku) {
        return productRepository.existsBySku(sku);
    }

    @Override
    @Transactional
    public void create(Product product) {
        ProductEntity productEntity = new ProductEntity(
                product.productId(),
                product.sku(),
                product.name(),
                product.description(),
                product.price(),
                product.currencyCode(),
                product.createdAt()
        );
        try {
            productRepository.saveAndFlush(productEntity);
        } catch (DataIntegrityViolationException exception) {
            if (isConstraintViolation(exception, UNIQUE_PRODUCT_SKU_CONSTRAINT)) {
                throw new ProductAlreadyExistsException(product.sku());
            }

            throw exception;
        }
        ProductInventoryEntity inventoryEntity = new ProductInventoryEntity(
                productEntity.getProductId(),
                product.inventory().onHandQuantity(),
                product.inventory().updatedAt()
        );
        inventoryRepository.save(inventoryEntity);
    }

    @Override
    @Transactional
    public Product update(UpdateProductCommand command, Instant updatedAt) {
        int updatedRows = productRepository.updateProduct(
                command.productId(),
                command.name(),
                command.description(),
                command.price(),
                command.currencyCode(),
                command.active(),
                command.expectedVersion(),
                updatedAt
        );

        Product product = findById(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        if (updatedRows == 0) {
            throw new ProductConcurrentUpdateException(command.productId());
        }

        return product;
    }

    @Override
    @Transactional
    public ProductInventory restock(RestockProductCommand command, Instant updatedAt) {
        int updatedRows = inventoryRepository.restock(
                command.productId(),
                command.quantity(),
                command.expectedVersion(),
                updatedAt
        );

        ProductInventoryEntity entity = inventoryRepository.findById(command.productId())
                .orElseThrow(() -> {
                    if (!productRepository.existsById(command.productId())) {
                        return new ProductNotFoundException(command.productId());
                    }

                    return new IllegalStateException("Inventory not found for product: " + command.productId());
                });


        if (updatedRows == 0) {
            if (entity.getVersion() != command.expectedVersion()) {
                throw new InventoryConcurrentUpdateException(command.productId());
            }

            throw new InventoryCapacityExceededException(command.productId());
        }

        return new ProductInventory(
                entity.getOnHandQuantity(),
                entity.getReservedQuantity(),
                entity.getVersion(),
                entity.getUpdatedAt()
        );
    }


    private boolean isConstraintViolation(Throwable exception, String constraintName) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintName.equals(constraintViolation.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
    }

    private static final String UNIQUE_PRODUCT_SKU_CONSTRAINT = "uq_products_sku";
}
