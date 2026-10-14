package com.dataforge.reserveflow.data.repository;

import com.dataforge.reserveflow.data.entity.ProductInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductInventoryRepository extends JpaRepository<ProductInventoryEntity, UUID> { }