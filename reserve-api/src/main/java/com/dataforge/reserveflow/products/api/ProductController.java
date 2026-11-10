package com.dataforge.reserveflow.products.api;

import com.dataforge.reserveflow.products.ProductService;
import com.dataforge.reserveflow.products.api.model.params.ProductListQueryParams;
import com.dataforge.reserveflow.products.api.model.request.CreateProductRequest;
import com.dataforge.reserveflow.products.api.model.response.InventoryResponse;
import com.dataforge.reserveflow.products.api.model.params.ProductQueryParams;
import com.dataforge.reserveflow.products.api.model.response.ProductPageResponse;
import com.dataforge.reserveflow.products.api.model.response.ProductResponse;
import com.dataforge.reserveflow.products.api.model.request.RestockProductRequest;
import com.dataforge.reserveflow.products.api.model.request.UpdateProductRequest;
import com.dataforge.reserveflow.products.commands.RestockProductCommand;
import com.dataforge.reserveflow.products.commands.UpdateProductCommand;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import com.dataforge.reserveflow.products.model.ProductInventory;
import com.dataforge.reserveflow.products.model.ProductPage;
import com.dataforge.reserveflow.products.queries.ProductListQuery;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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

    @GetMapping("/{productId}")
    public ProductResponse findById(
            @PathVariable UUID productId,
            @Valid @ModelAttribute ProductQueryParams params
    ) {
        boolean includeInventory = "inventory".equals(params.include());
        Product product = productService.findById(productId, includeInventory);
        return ProductControllerMapper.toResponse(product);
    }

    @PutMapping("/{productId}")
    public ProductResponse update(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        UpdateProductCommand command = ProductControllerMapper.toCommand(productId, request);
        Product product = productService.update(command);
        return ProductControllerMapper.toResponse(product);
    }

    @PostMapping("/{productId}/inventory/restock")
    public InventoryResponse restock(
            @PathVariable UUID productId,
            @Valid @RequestBody RestockProductRequest request
    ) {
        RestockProductCommand command = ProductControllerMapper.toCommand(productId, request);
        ProductInventory inventory = productService.restock(command);
        return ProductControllerMapper.toInventoryResponse(inventory);
    }

    @GetMapping
    public ProductPageResponse findPage(
            @Valid @ModelAttribute ProductListQueryParams params
    ) {
        ProductListQuery query = ProductControllerMapper.toQuery(params);
        ProductPage page = productService.findPage(query);

        return ProductControllerMapper.toResponse(page, query.active());
    }
}