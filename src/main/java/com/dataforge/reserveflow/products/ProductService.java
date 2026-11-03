package com.dataforge.reserveflow.products;

import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import com.dataforge.reserveflow.products.commands.RestockProductCommand;
import com.dataforge.reserveflow.products.commands.UpdateProductCommand;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.model.ProductInventory;
import com.dataforge.reserveflow.products.model.ProductPage;
import com.dataforge.reserveflow.products.queries.ProductListQuery;

import java.util.UUID;

public interface ProductService {

    Product findById(UUID productId, boolean includeInventory);

    Product create(CreateProductCommand command);

    Product update(UpdateProductCommand command);

    ProductInventory restock(RestockProductCommand command);

    ProductPage findPage(ProductListQuery query);
}