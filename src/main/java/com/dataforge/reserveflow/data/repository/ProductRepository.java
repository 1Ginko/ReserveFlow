package com.dataforge.reserveflow.data.repository;

import com.dataforge.reserveflow.data.entity.ProductEntity;
import com.dataforge.reserveflow.data.projection.ProductDetailsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    @Query(
            value = "SELECT * FROM products WHERE sku = :sku",
            nativeQuery = true
    )
    Optional<ProductEntity> findBySku(@Param("sku") String sku);

    @Query(
            value = """
                SELECT EXISTS (
                    SELECT 1
                    FROM products
                    WHERE sku = :sku
                )
                """,
            nativeQuery = true
    )
    boolean existsBySku(@Param("sku") String sku);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                UPDATE products
                SET product_name = :name,
                    product_description = :description,
                    price = :price,
                    currency_code = :currencyCode,
                    active = :active,
                    version = version + 1,
                    updated_at = :updatedAt
                WHERE product_id = :productId AND version = :expectedVersion
                """,
            nativeQuery = true
    )
    int updateProduct(
            @Param("productId") UUID productId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("price") BigDecimal price,
            @Param("currencyCode") String currencyCode,
            @Param("active") boolean active,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedAt") Instant updatedAt
    );

    @Query(
            value = """
                SELECT
                    p.product_id AS "productId",
                    p.sku AS "sku",
                    p.product_name AS "name",
                    p.product_description AS "description",
                    p.price AS "price",
                    p.currency_code AS "currencyCode",
                    p.active AS "active",
                    p.version AS "version",
                    p.created_at AS "createdAt",
                    p.updated_at AS "updatedAt",
                    i.on_hand_quantity AS "onHandQuantity",
                    i.reserved_quantity AS "reservedQuantity",
                    i.version AS "inventoryVersion",
                    i.updated_at AS "inventoryUpdatedAt"
                FROM products p
                JOIN product_inventory i ON i.product_id = p.product_id
                WHERE p.product_id = :productId
                """,
            nativeQuery = true
    )
    Optional<ProductDetailsProjection> findWithInventoryById(@Param("productId") UUID productId);
}
