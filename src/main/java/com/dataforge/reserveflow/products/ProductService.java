package com.dataforge.reserveflow.products;

import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import com.dataforge.reserveflow.products.model.Product;

import java.util.UUID;

public interface ProductService {

    Product findById(UUID productId, boolean includeInventory);

    Product create(CreateProductCommand command);
}