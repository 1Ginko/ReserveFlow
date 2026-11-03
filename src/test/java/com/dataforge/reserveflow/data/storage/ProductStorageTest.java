package com.dataforge.reserveflow.data.storage;

import com.dataforge.reserveflow.data.entity.ProductEntity;
import com.dataforge.reserveflow.data.repository.ProductInventoryRepository;
import com.dataforge.reserveflow.data.repository.ProductRepository;
import com.dataforge.reserveflow.products.model.Product;
import com.dataforge.reserveflow.products.model.ProductPage;
import com.dataforge.reserveflow.products.queries.ProductListQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ProductStorageTest {

    private static final UUID PRODUCT_ID = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
    private static final Instant NOW = Instant.parse("2026-11-03T20:00:00Z");

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductInventoryRepository inventoryRepository;

    private ProductStorageImpl productStorage;

    @BeforeEach
    void setUp() {
        productStorage = new ProductStorageImpl(productRepository, inventoryRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "2, 40",
            "2147483647, 42949672940"
    })
    void findsProductPageWithRequestedOffset(int pageNumber, long expectedOffset) {
        ProductListQuery query = new ProductListQuery(pageNumber, 20, true);
        ProductEntity entity = new ProductEntity(
                PRODUCT_ID,
                "PHONE-001",
                "Smartphone",
                "Test product",
                new BigDecimal("799.99"),
                "USD",
                NOW
        );
        long totalElements = expectedOffset + 1;

        given(productRepository.findPage(true, 20, expectedOffset)).willReturn(List.of(entity));
        given(productRepository.countProducts(true)).willReturn(totalElements);

        ProductPage result = productStorage.findPage(query);

        Product expectedProduct = new Product(
                PRODUCT_ID,
                "PHONE-001",
                "Smartphone",
                "Test product",
                new BigDecimal("799.99"),
                "USD",
                true,
                0,
                null,
                NOW,
                NOW
        );
        assertEquals(new ProductPage(List.of(expectedProduct), pageNumber, 20, totalElements), result);
        then(productRepository).should().findPage(true, 20, expectedOffset);
        then(productRepository).should().countProducts(true);
        then(productRepository).shouldHaveNoMoreInteractions();
        then(inventoryRepository).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false, true})
    void usesSameActiveFilterForProductsAndCount(Boolean active) {
        ProductListQuery query = new ProductListQuery(0, 20, active);

        given(productRepository.findPage(active, 20, 0)).willReturn(List.of());
        given(productRepository.countProducts(active)).willReturn(0L);

        ProductPage result = productStorage.findPage(query);

        assertEquals(new ProductPage(List.of(), 0, 20, 0), result);
        then(productRepository).should().findPage(active, 20, 0);
        then(productRepository).should().countProducts(active);
        then(productRepository).shouldHaveNoMoreInteractions();
        then(inventoryRepository).shouldHaveNoInteractions();
    }
}
