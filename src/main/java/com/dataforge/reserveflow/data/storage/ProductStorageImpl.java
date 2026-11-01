package com.dataforge.reserveflow.data.storage;

import com.dataforge.reserveflow.data.entity.ProductEntity;
import com.dataforge.reserveflow.data.entity.ProductInventoryEntity;
import com.dataforge.reserveflow.data.repository.ProductInventoryRepository;
import com.dataforge.reserveflow.data.repository.ProductRepository;
import com.dataforge.reserveflow.exceptions.ProductAlreadyExistsException;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.model.ProductInventory;
import com.dataforge.reserveflow.products.storage.ProductStorage;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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