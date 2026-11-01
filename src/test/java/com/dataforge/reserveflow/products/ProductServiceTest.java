package com.dataforge.reserveflow.products;

import com.dataforge.reserveflow.exceptions.ProductNotFoundException;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.model.ProductInventory;
import com.dataforge.reserveflow.products.storage.ProductStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @Mock
    private ProductStorage productStorage;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        productService = new ProductService(productStorage, clock);
    }

    @Test
    void findsProductWithoutInventory() {
        Product product = createProduct(null);
        given(productStorage.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        Product result = productService.findById(PRODUCT_ID, false);

        assertEquals(product, result);
        then(productStorage).should().findById(PRODUCT_ID);
        then(productStorage).shouldHaveNoMoreInteractions();
    }

    @Test
    void findsProductWithInventory() {
        ProductInventory inventory = new ProductInventory(10, 3, 4, NOW);
        Product product = createProduct(inventory);
        given(productStorage.findWithInventoryById(PRODUCT_ID)).willReturn(Optional.of(product));

        Product result = productService.findById(PRODUCT_ID, true);

        assertEquals(product, result);
        then(productStorage).should().findWithInventoryById(PRODUCT_ID);
        then(productStorage).shouldHaveNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsUnknownProduct(boolean includeInventory) {
        if (includeInventory) {
            given(productStorage.findWithInventoryById(PRODUCT_ID)).willReturn(Optional.empty());
        } else {
            given(productStorage.findById(PRODUCT_ID)).willReturn(Optional.empty());
        }

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> productService.findById(PRODUCT_ID, includeInventory)
        );

        assertEquals("Product not found: " + PRODUCT_ID, exception.getMessage());
        if (includeInventory) {
            then(productStorage).should().findWithInventoryById(PRODUCT_ID);
        } else {
            then(productStorage).should().findById(PRODUCT_ID);
        }
        then(productStorage).shouldHaveNoMoreInteractions();
    }

    private Product createProduct(ProductInventory inventory) {
        return new Product(
                PRODUCT_ID,
                "PHONE-001",
                "Smartphone",
                "Test product",
                new BigDecimal("799.99"),
                "USD",
                true,
                inventory,
                NOW,
                NOW
        );
    }
}
