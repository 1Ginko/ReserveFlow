package com.dataforge.reserveflow.data.repository;

import com.dataforge.reserveflow.data.entity.ProductInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProductInventoryRepository extends JpaRepository<ProductInventoryEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                UPDATE product_inventory
                SET reserved_quantity = :newReservedQuantity,
                    version = version + 1,
                    updated_at = :updatedAt
                WHERE product_id = :productId
                  AND version = :expectedVersion
                """,
            nativeQuery = true
    )
    int updateReservedQuantity(
            @Param("productId") UUID productId,
            @Param("newReservedQuantity") int newReservedQuantity,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedAt") Instant updatedAt
    );
}