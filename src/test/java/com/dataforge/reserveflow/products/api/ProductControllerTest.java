package com.dataforge.reserveflow.products.api;

import com.dataforge.reserveflow.exceptions.ProductAlreadyExistsException;
import com.dataforge.reserveflow.exceptions.ProductNotFoundException;
import com.dataforge.reserveflow.products.ProductService;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.model.ProductInventory;
import com.dataforge.reserveflow.products.commands.CreateProductCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
