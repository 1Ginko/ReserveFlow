package com.dataforge.reserveflow.data.repository;

import com.dataforge.reserveflow.data.entity.ProductEntity;
import com.dataforge.reserveflow.data.projection.ProductDetailsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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