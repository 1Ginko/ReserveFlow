package com.dataforge.reserveflow.data.repository;

import com.dataforge.reserveflow.data.entity.ProductEntity;
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
}