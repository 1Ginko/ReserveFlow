package com.dataforge.reserveflow.products.api;

import com.dataforge.reserveflow.exceptions.InventoryCapacityExceededException;
import com.dataforge.reserveflow.exceptions.InventoryConcurrentUpdateException;
import com.dataforge.reserveflow.exceptions.ProductAlreadyExistsException;
import com.dataforge.reserveflow.exceptions.ProductConcurrentUpdateException;
import com.dataforge.reserveflow.exceptions.ProductNotFoundException;
import com.dataforge.reserveflow.products.ProductService;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.model.ProductInventory;
import com.dataforge.reserveflow.products.model.ProductPage;
import com.dataforge.reserveflow.products.queries.ProductListQuery;
import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import com.dataforge.reserveflow.products.commands.RestockProductCommand;
import com.dataforge.reserveflow.products.commands.UpdateProductCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private Clock clock;

    @Test
    void findsProductWithoutInventory() throws Exception {
        Product product = createProduct(null);
        UUID productId = product.productId();

        given(productService.findById(productId, false)).willReturn(product);

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.sku").value("PHONE-001"))
                .andExpect(jsonPath("$.name").value("Smartphone"))
                .andExpect(jsonPath("$.description").value("Test product"))
                .andExpect(jsonPath("$.price").value(799.99))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.createdAt").value(product.createdAt().toString()))
                .andExpect(jsonPath("$.updatedAt").value(product.updatedAt().toString()))
                .andExpect(jsonPath("$.inventory").doesNotHaveJsonPath());

        then(productService).should().findById(productId, false);
    }

    @Test
    void findsProductWithInventory() throws Exception {
        Instant inventoryUpdatedAt = Instant.parse("2026-11-01T10:00:00Z");
        ProductInventory inventory = new ProductInventory(10, 3, 4, inventoryUpdatedAt);
        Product product = createProduct(inventory);
        UUID productId = product.productId();

        given(productService.findById(productId, true)).willReturn(product);

        mockMvc.perform(get("/api/products/{productId}", productId)
                        .param("include", "inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.updatedAt").value(product.updatedAt().toString()))
                .andExpect(jsonPath("$.inventory.onHandQuantity").value(10))
                .andExpect(jsonPath("$.inventory.reservedQuantity").value(3))
                .andExpect(jsonPath("$.inventory.availableQuantity").value(7))
                .andExpect(jsonPath("$.inventory.version").value(4))
                .andExpect(jsonPath("$.inventory.updatedAt").value(inventoryUpdatedAt.toString()));

        then(productService).should().findById(productId, true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void returnsNotFoundForUnknownProduct(boolean includeInventory) throws Exception {
        UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Instant now = Instant.parse("2026-11-01T10:01:00Z");

        given(clock.instant()).willReturn(now);
        given(productService.findById(productId, includeInventory))
                .willThrow(new ProductNotFoundException(productId));

        MockHttpServletRequestBuilder request = get("/api/products/{productId}", productId);
        if (includeInventory) {
            request.param("include", "inventory");
        }

        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product not found: " + productId))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(productService).should().findById(productId, includeInventory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", ""})
    void rejectsInvalidInclude(String include) throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-11-01T10:02:00Z");
        given(clock.instant()).willReturn(now);

        mockMvc.perform(get("/api/products/{productId}", productId)
                        .param("include", include))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId))
                .andExpect(jsonPath("$.fieldErrors.include").value("Include must be 'inventory'"));

        verifyNoInteractions(productService);
    }

    @Test
    void createsProduct() throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-10-15T11:57:47Z");
        CreateProductCommand command = createCommand();

        Product product = new Product(
                productId,
                "PHONE-001",
                "Smartphone",
                "Test product",
                new BigDecimal("799.99"),
                "USD",
                true,
                0,
                new ProductInventory(10, 0, 0, now),
                now,
                now
        );

        given(productService.create(command)).willReturn(product);

        mockMvc.perform(post("/api/products")
                        .contentType(APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.sku").value("PHONE-001"))
                .andExpect(jsonPath("$.price").value(799.99))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.inventory.onHandQuantity").value(10))
                .andExpect(jsonPath("$.inventory.reservedQuantity").value(0))
                .andExpect(jsonPath("$.inventory.availableQuantity").value(10))
                .andExpect(jsonPath("$.inventory.version").value(0))
                .andExpect(jsonPath("$.inventory.updatedAt").value(now.toString()));

        then(productService).should().create(command);
    }

    @Test
    void rejectsDuplicateSku() throws Exception {
        Instant now = Instant.parse("2026-10-15T11:58:01Z");
        CreateProductCommand command = createCommand();

        given(clock.instant()).willReturn(now);
        given(productService.create(command))
                .willThrow(new ProductAlreadyExistsException("PHONE-001"));

        mockMvc.perform(post("/api/products")
                        .contentType(APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Product with SKU already exists: PHONE-001"))
                .andExpect(jsonPath("$.path").value("/api/products"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(productService).should().create(command);
    }

    @Test
    void rejectsInvalidProduct() throws Exception {
        Instant now = Instant.parse("2026-10-15T12:03:49Z");
        given(clock.instant()).willReturn(now);

        mockMvc.perform(post("/api/products")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "",
                                  "name": "",
                                  "price": -1,
                                  "currencyCode": "US",
                                  "initialQuantity": -5
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/products"))
                .andExpect(jsonPath("$.fieldErrors.sku").value("SKU must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.name")
                        .value("Product name must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.price")
                        .value("Price must not be negative"))
                .andExpect(jsonPath("$.fieldErrors.currencyCode")
                        .value("Currency code must contain exactly 3 Latin letters"))
                .andExpect(jsonPath("$.fieldErrors.initialQuantity")
                        .value("Initial quantity must not be negative"));

        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void updatesProduct(int expectedVersion) throws Exception {
        Product product = updatedProduct("Updated product", expectedVersion + 1);
        UUID productId = product.productId();
        UpdateProductCommand command = updateCommand(productId, "Updated product", expectedVersion);

        given(productService.update(command)).willReturn(product);

        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(APPLICATION_JSON)
                        .content(validUpdateRequestJson(expectedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.sku").value("PHONE-001"))
                .andExpect(jsonPath("$.name").value("Smartphone Pro"))
                .andExpect(jsonPath("$.description").value("Updated product"))
                .andExpect(jsonPath("$.price").value(899.99))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(expectedVersion + 1))
                .andExpect(jsonPath("$.createdAt").value(product.createdAt().toString()))
                .andExpect(jsonPath("$.updatedAt").value(product.updatedAt().toString()))
                .andExpect(jsonPath("$.inventory").doesNotHaveJsonPath());

        then(productService).should().update(command);
    }

    @Test
    void clearsDescriptionWhenOmittedFromUpdate() throws Exception {
        Product product = updatedProduct(null, 4);
        UUID productId = product.productId();
        UpdateProductCommand command = updateCommand(productId, null, 3);

        given(productService.update(command)).willReturn(product);

        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " Smartphone Pro ",
                                  "price": 899.99,
                                  "currencyCode": "usd",
                                  "active": false,
                                  "version": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.version").value(4));

        then(productService).should().update(command);
    }

    @Test
    void rejectsUpdateOfUnknownProduct() throws Exception {
        UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Instant now = Instant.parse("2026-11-01T17:06:12Z");
        UpdateProductCommand command = updateCommand(productId, "Updated product", 3);

        given(clock.instant()).willReturn(now);
        given(productService.update(command)).willThrow(new ProductNotFoundException(productId));

        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(APPLICATION_JSON)
                        .content(validUpdateRequestJson(3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product not found: " + productId))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(productService).should().update(command);
    }

    @Test
    void rejectsUpdateWithOutdatedVersion() throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-11-01T17:06:26Z");
        UpdateProductCommand command = updateCommand(productId, "Updated product", 3);

        given(clock.instant()).willReturn(now);
        given(productService.update(command))
                .willThrow(new ProductConcurrentUpdateException(productId));

        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(APPLICATION_JSON)
                        .content(validUpdateRequestJson(3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Product was concurrently updated: " + productId))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(productService).should().update(command);
    }

    @Test
    void rejectsInvalidProductUpdate() throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-11-01T17:07:00Z");
        given(clock.instant()).willReturn(now);

        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "price": -1,
                                  "currencyCode": "US",
                                  "active": false,
                                  "version": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId))
                .andExpect(jsonPath("$.fieldErrors.name").value("Product name must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.price").value("Price must not be negative"))
                .andExpect(jsonPath("$.fieldErrors.currencyCode")
                        .value("Currency code must contain exactly 3 Latin letters"))
                .andExpect(jsonPath("$.fieldErrors.version").value("Version must not be negative"));

        verifyNoInteractions(productService);
    }

    @Test
    void rejectsMissingUpdateFields() throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-11-01T17:08:00Z");
        given(clock.instant()).willReturn(now);

        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId))
                .andExpect(jsonPath("$.fieldErrors.name").value("Product name must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.price").value("Price must be specified"))
                .andExpect(jsonPath("$.fieldErrors.currencyCode").value("Currency code must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.active").value("Active flag must be specified"))
                .andExpect(jsonPath("$.fieldErrors.version").value("Version must be specified"));

        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 8})
    void restocksProduct(long expectedVersion) throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-11-02T20:03:45Z");
        RestockProductCommand command = new RestockProductCommand(productId, 5, expectedVersion);
        ProductInventory inventory = new ProductInventory(13, 3, expectedVersion + 1, now);

        given(productService.restock(command)).willReturn(inventory);

        mockMvc.perform(post("/api/products/{productId}/inventory/restock", productId)
                        .contentType(APPLICATION_JSON)
                        .content(restockRequestJson(5, expectedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(13))
                .andExpect(jsonPath("$.reservedQuantity").value(3))
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andExpect(jsonPath("$.version").value(expectedVersion + 1))
                .andExpect(jsonPath("$.updatedAt").value(now.toString()))
                .andExpect(jsonPath("$.inventory").doesNotHaveJsonPath());

        then(productService).should().restock(command);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveRestockQuantity(int quantity) throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-11-02T20:05:24Z");
        given(clock.instant()).willReturn(now);

        mockMvc.perform(post("/api/products/{productId}/inventory/restock", productId)
                        .contentType(APPLICATION_JSON)
                        .content(restockRequestJson(quantity, 8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId + "/inventory/restock"))
                .andExpect(jsonPath("$.fieldErrors.quantity").value("Quantity must be greater than zero"));

        verifyNoInteractions(productService);
    }

    @Test
    void rejectsMissingRestockFields() throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-11-02T20:05:24Z");
        given(clock.instant()).willReturn(now);

        mockMvc.perform(post("/api/products/{productId}/inventory/restock", productId)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId + "/inventory/restock"))
                .andExpect(jsonPath("$.fieldErrors.quantity").value("Quantity must be specified"))
                .andExpect(jsonPath("$.fieldErrors.version").value("Version must be specified"));

        verifyNoInteractions(productService);
    }

    @Test
    void rejectsNegativeRestockVersion() throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-11-02T20:05:24Z");
        given(clock.instant()).willReturn(now);

        mockMvc.perform(post("/api/products/{productId}/inventory/restock", productId)
                        .contentType(APPLICATION_JSON)
                        .content(restockRequestJson(5, -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId + "/inventory/restock"))
                .andExpect(jsonPath("$.fieldErrors.version").value("Version must not be negative"));

        verifyNoInteractions(productService);
    }

    @Test
    void rejectsRestockOfUnknownProduct() throws Exception {
        UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Instant now = Instant.parse("2026-11-02T20:05:24Z");
        RestockProductCommand command = new RestockProductCommand(productId, 5, 8);

        given(clock.instant()).willReturn(now);
        given(productService.restock(command)).willThrow(new ProductNotFoundException(productId));

        mockMvc.perform(post("/api/products/{productId}/inventory/restock", productId)
                        .contentType(APPLICATION_JSON)
                        .content(restockRequestJson(5, 8)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product not found: " + productId))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId + "/inventory/restock"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(productService).should().restock(command);
    }

    @Test
    void rejectsRestockWithOutdatedInventoryVersion() throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-11-02T20:05:24Z");
        RestockProductCommand command = new RestockProductCommand(productId, 5, 8);

        given(clock.instant()).willReturn(now);
        given(productService.restock(command))
                .willThrow(new InventoryConcurrentUpdateException(productId));

        mockMvc.perform(post("/api/products/{productId}/inventory/restock", productId)
                        .contentType(APPLICATION_JSON)
                        .content(restockRequestJson(5, 8)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Inventory was concurrently updated: " + productId))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId + "/inventory/restock"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(productService).should().restock(command);
    }

    @Test
    void rejectsRestockExceedingInventoryCapacity() throws Exception {
        UUID productId = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
        Instant now = Instant.parse("2026-11-02T20:05:24Z");
        RestockProductCommand command = new RestockProductCommand(productId, Integer.MAX_VALUE, 8);

        given(clock.instant()).willReturn(now);
        given(productService.restock(command))
                .willThrow(new InventoryCapacityExceededException(productId));

        mockMvc.perform(post("/api/products/{productId}/inventory/restock", productId)
                        .contentType(APPLICATION_JSON)
                        .content(restockRequestJson(Integer.MAX_VALUE, 8)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "On-hand quantity must not exceed " + Integer.MAX_VALUE + " for product: " + productId
                ))
                .andExpect(jsonPath("$.path").value("/api/products/" + productId + "/inventory/restock"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(productService).should().restock(command);
    }

    @Test
    void listsProductsWithDefaultPagination() throws Exception {
        Product firstProduct = secondProduct();
        Product secondProduct = createProduct(null);
        ProductListQuery query = new ProductListQuery(0, 20, null);
        ProductPage page = new ProductPage(List.of(firstProduct, secondProduct), 0, 20, 2);

        given(productService.findPage(query)).willReturn(page);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(firstProduct.productId().toString()))
                .andExpect(jsonPath("$.items[0].sku").value("PHONE-002"))
                .andExpect(jsonPath("$.items[0].price").value(499.99))
                .andExpect(jsonPath("$.items[0].version").value(0))
                .andExpect(jsonPath("$.items[0].inventory").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[1].productId").value(secondProduct.productId().toString()))
                .andExpect(jsonPath("$.items[1].sku").value("PHONE-001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.nextPage").value(nullValue()));

        then(productService).should().findPage(query);
    }

    @Test
    void linksToNextProductPage() throws Exception {
        Product product = secondProduct();
        ProductListQuery query = new ProductListQuery(0, 1, null);
        ProductPage page = new ProductPage(List.of(product), 0, 1, 2);

        given(productService.findPage(query)).willReturn(page);

        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("PHONE-002"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.nextPage").value("/api/products?page=1&size=1"));

        then(productService).should().findPage(query);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesActiveFilterInNextPageLink(boolean active) throws Exception {
        Product product = active ? createProduct(null) : updatedProduct("Updated product", 4);
        ProductListQuery query = new ProductListQuery(0, 1, active);
        ProductPage page = new ProductPage(List.of(product), 0, 1, 2);

        given(productService.findPage(query)).willReturn(page);

        mockMvc.perform(get("/api/products")
                        .param("size", "1")
                        .param("active", Boolean.toString(active)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].active").value(active))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.nextPage").value("/api/products?page=1&size=1&active=" + active));

        then(productService).should().findPage(query);
    }

    @Test
    void returnsLastProductPageWithoutNextLink() throws Exception {
        Product product = createProduct(null);
        ProductListQuery query = new ProductListQuery(1, 1, null);
        ProductPage page = new ProductPage(List.of(product), 1, 1, 2);

        given(productService.findPage(query)).willReturn(page);

        mockMvc.perform(get("/api/products")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("PHONE-001"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.nextPage").value(nullValue()));

        then(productService).should().findPage(query);
    }

    @Test
    void roundsUpProductPageCount() throws Exception {
        ProductListQuery query = new ProductListQuery(0, 2, null);
        ProductPage page = new ProductPage(List.of(secondProduct(), createProduct(null)), 0, 2, 3);

        given(productService.findPage(query)).willReturn(page);

        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.nextPage").value("/api/products?page=1&size=2"));

        then(productService).should().findPage(query);
    }

    @Test
    void returnsEmptyListWhenNoProductsMatch() throws Exception {
        ProductListQuery query = new ProductListQuery(0, 20, false);
        ProductPage page = new ProductPage(List.of(), 0, 20, 0);

        given(productService.findPage(query)).willReturn(page);

        mockMvc.perform(get("/api/products").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.nextPage").value(nullValue()));

        then(productService).should().findPage(query);
    }

    @Test
    void returnsEmptyPagePastLastProduct() throws Exception {
        ProductListQuery query = new ProductListQuery(3, 1, null);
        ProductPage page = new ProductPage(List.of(), 3, 1, 2);

        given(productService.findPage(query)).willReturn(page);

        mockMvc.perform(get("/api/products")
                        .param("page", "3")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.nextPage").value(nullValue()));

        then(productService).should().findPage(query);
    }

    @Test
    void usesDefaultSizeWhenOnlyPageIsSpecified() throws Exception {
        ProductListQuery query = new ProductListQuery(1, 20, null);
        ProductPage page = new ProductPage(List.of(), 1, 20, 0);

        given(productService.findPage(query)).willReturn(page);

        mockMvc.perform(get("/api/products").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.nextPage").value(nullValue()));

        then(productService).should().findPage(query);
    }

    @Test
    void acceptsMaximumProductPageSize() throws Exception {
        ProductListQuery query = new ProductListQuery(0, 100, null);
        ProductPage page = new ProductPage(List.of(), 0, 100, 0);

        given(productService.findPage(query)).willReturn(page);

        mockMvc.perform(get("/api/products").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));

        then(productService).should().findPage(query);
    }

    @ParameterizedTest
    @CsvSource({
            "page, -1, Page must not be negative",
            "size, 0, Size must be greater than zero",
            "size, -1, Size must be greater than zero",
            "size, 101, Size must not exceed 100"
    })
    void rejectsInvalidPagination(String parameter, String value, String message) throws Exception {
        Instant now = Instant.parse("2026-11-03T20:00:00Z");
        given(clock.instant()).willReturn(now);

        mockMvc.perform(get("/api/products").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/products"))
                .andExpect(jsonPath("$.fieldErrors." + parameter).value(message));

        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @CsvSource({
            "page, abc",
            "size, abc",
            "active, unknown"
    })
    void rejectsMalformedListParameters(String parameter, String value) throws Exception {
        Instant now = Instant.parse("2026-11-03T20:00:00Z");
        given(clock.instant()).willReturn(now);

        mockMvc.perform(get("/api/products").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/products"))
                .andExpect(jsonPath("$.fieldErrors." + parameter).isString());

        verifyNoInteractions(productService);
    }

    private Product secondProduct() {
        Instant createdAt = Instant.parse("2026-11-03T19:52:41Z");

        return new Product(
                UUID.fromString("fcc1186a-bae6-460a-adfd-a16b843ae3ec"),
                "PHONE-002",
                "Second smartphone",
                "Pagination test",
                new BigDecimal("499.99"),
                "USD",
                true,
                0,
                null,
                createdAt,
                createdAt
        );
    }

    private String restockRequestJson(int quantity, long version) {
        return """
                {
                  "quantity": %d,
                  "version": %d
                }
                """.formatted(quantity, version);
    }

    private Product updatedProduct(String description, long version) {
        return new Product(
                UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a"),
                "PHONE-001",
                "Smartphone Pro",
                description,
                new BigDecimal("899.99"),
                "USD",
                false,
                version,
                null,
                Instant.parse("2026-10-15T11:57:47Z"),
                Instant.parse("2026-11-01T17:06:12Z")
        );
    }

    private UpdateProductCommand updateCommand(UUID productId, String description, long expectedVersion) {
        return new UpdateProductCommand(
                productId,
                " Smartphone Pro ",
                description,
                new BigDecimal("899.99"),
                "usd",
                false,
                expectedVersion
        );
    }

    private String validUpdateRequestJson(long version) {
        return """
                {
                  "name": " Smartphone Pro ",
                  "description": "Updated product",
                  "price": 899.99,
                  "currencyCode": "usd",
                  "active": false,
                  "version": %d
                }
                """.formatted(version);
    }

    private Product createProduct(ProductInventory inventory) {
        Instant createdAt = Instant.parse("2026-10-15T11:57:47Z");

        return new Product(
                UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a"),
                "PHONE-001",
                "Smartphone",
                "Test product",
                new BigDecimal("799.99"),
                "USD",
                true,
                3,
                inventory,
                createdAt,
                createdAt
        );
    }

    private CreateProductCommand createCommand() {
        return new CreateProductCommand(
                "phone-001",
                "Smartphone",
                "Test product",
                new BigDecimal("799.99"),
                "usd",
                10
        );
    }

    private String validRequestJson() {
        return """
                {
                  "sku": "phone-001",
                  "name": "Smartphone",
                  "description": "Test product",
                  "price": 799.99,
                  "currencyCode": "usd",
                  "initialQuantity": 10
                }
                """;
    }
}
