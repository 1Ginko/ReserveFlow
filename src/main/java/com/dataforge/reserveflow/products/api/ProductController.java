package com.dataforge.reserveflow.products.api;

import com.dataforge.reserveflow.products.ProductService;
import com.dataforge.reserveflow.products.api.model.CreateProductRequest;
import com.dataforge.reserveflow.products.api.model.ProductResponse;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        CreateProductCommand command = ProductControllerMapper.toCommand(request);
        Product product = productService.create(command);
        return ProductControllerMapper.toResponse(product);
    }
}