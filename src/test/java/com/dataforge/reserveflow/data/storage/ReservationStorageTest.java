package com.dataforge.reserveflow.data.storage;

import com.dataforge.reserveflow.data.entity.ProductEntity;
import com.dataforge.reserveflow.data.entity.ProductInventoryEntity;
import com.dataforge.reserveflow.data.entity.ReservationEntity;
import com.dataforge.reserveflow.data.repository.ProductInventoryRepository;
import com.dataforge.reserveflow.data.repository.ProductRepository;
import com.dataforge.reserveflow.data.repository.ReservationRepository;
import com.dataforge.reserveflow.exceptions.InsufficientStockException;
import com.dataforge.reserveflow.exceptions.InventoryConcurrentUpdateException;
import com.dataforge.reserveflow.exceptions.ProductInactiveException;
import com.dataforge.reserveflow.exceptions.ProductNotFoundException;
import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationCreationResult;
import com.dataforge.reserveflow.reservations.model.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ReservationStorageTest {

    private static final UUID PRODUCT_ID = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
    private static final UUID RESERVATION_ID = UUID.fromString("6cfe755a-a85b-4ba3-80cd-d05451686746");
    private static final UUID EXISTING_ID = UUID.fromString("b1a64797-831c-42eb-8d6b-16a6050ede49");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("8fa9556c-2c2f-4b34-9af5-f9f020aa9a15");
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00.123456100Z");

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductInventoryRepository inventoryRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private ReservationStorageImpl reservationStorage;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservationStorage = new ReservationStorageImpl(
                productRepository,
                inventoryRepository,
                reservationRepository
        );
        reservation = new Reservation(
                RESERVATION_ID,
                PRODUCT_ID,
                3,
                ReservationStatus.ACTIVE,
                NOW.plusSeconds(900),
                0,
                NOW,
                NOW
        );
    }

    @Test
    void insertsBeforeUpdatingStockAndReturnsPersistedReservation() {
        givenInsertResult(1);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(activeProduct()));
        ProductInventoryEntity inventory = inventoryForUpdate();
        given(inventoryRepository.findById(PRODUCT_ID)).willReturn(Optional.of(inventory));
        given(inventoryRepository.updateReservedQuantity(PRODUCT_ID, 5, 4, NOW)).willReturn(1);

        Instant persistedAt = NOW.truncatedTo(ChronoUnit.MICROS);
        ReservationEntity persisted = persistedReservation(RESERVATION_ID, persistedAt);
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(persisted));

        ReservationCreationResult result = reservationStorage.create(reservation, IDEMPOTENCY_KEY);

        Reservation expected = new Reservation(
                RESERVATION_ID,
                PRODUCT_ID,
                3,
                ReservationStatus.ACTIVE,
                persistedAt.plusSeconds(900),
                0,
                persistedAt,
                persistedAt
        );
        assertEquals(new ReservationCreationResult(expected, true), result);

        InOrder order = inOrder(reservationRepository, inventoryRepository);
        order.verify(reservationRepository).insertIfAbsent(
                RESERVATION_ID, PRODUCT_ID, IDEMPOTENCY_KEY, 3, NOW.plusSeconds(900), NOW
        );
        order.verify(inventoryRepository).updateReservedQuantity(PRODUCT_ID, 5, 4, NOW);
        order.verify(reservationRepository).findById(RESERVATION_ID);

        then(productRepository).should().findById(PRODUCT_ID);
        then(inventoryRepository).should().findById(PRODUCT_ID);
        then(reservationRepository).shouldHaveNoMoreInteractions();
        then(inventoryRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void replaysBeforeValidatingProductOrUpdatingStock() {
        givenInsertResult(0);
        Instant createdAt = NOW.minusSeconds(3600).truncatedTo(ChronoUnit.MICROS);
        ReservationEntity existing = persistedReservation(EXISTING_ID, createdAt);
        given(reservationRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(Optional.of(existing));

        ReservationCreationResult result = reservationStorage.create(reservation, IDEMPOTENCY_KEY);

        Reservation expected = new Reservation(
                EXISTING_ID,
                PRODUCT_ID,
                3,
                ReservationStatus.ACTIVE,
                createdAt.plusSeconds(900),
                0,
                createdAt,
                createdAt
        );
        assertEquals(new ReservationCreationResult(expected, false), result);
        verifyInsert();
        then(reservationRepository).should().findByIdempotencyKey(IDEMPOTENCY_KEY);
        then(reservationRepository).shouldHaveNoMoreInteractions();
        then(productRepository).shouldHaveNoInteractions();
        then(inventoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void rejectsMissingProductWhenNothingWasInsertedAndKeyIsUnknown() {
        givenInsertResult(0);
        given(reservationRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(Optional.empty());

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> reservationStorage.create(reservation, IDEMPOTENCY_KEY)
        );

        assertEquals("Product not found: " + PRODUCT_ID, exception.getMessage());
        verifyInsert();
        then(reservationRepository).should().findByIdempotencyKey(IDEMPOTENCY_KEY);
        then(reservationRepository).shouldHaveNoMoreInteractions();
        then(productRepository).shouldHaveNoInteractions();
        then(inventoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void rejectsNewReservationForInactiveProductBeforeChangingStock() {
        givenInsertResult(1);
        ProductEntity product = mock(ProductEntity.class);
        given(product.isActive()).willReturn(false);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        assertThrows(
                ProductInactiveException.class,
                () -> reservationStorage.create(reservation, IDEMPOTENCY_KEY)
        );

        verifyInsert();
        then(reservationRepository).shouldHaveNoMoreInteractions();
        then(inventoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void rejectsInsufficientStockBeforeInventoryUpdate() {
        givenInsertResult(1);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(activeProduct()));
        ProductInventoryEntity inventory = mock(ProductInventoryEntity.class);
        given(inventory.getAvailableQuantity()).willReturn(2);
        given(inventoryRepository.findById(PRODUCT_ID)).willReturn(Optional.of(inventory));

        InsufficientStockException exception = assertThrows(
                InsufficientStockException.class,
                () -> reservationStorage.create(reservation, IDEMPOTENCY_KEY)
        );

        assertEquals(
                "Insufficient stock for product " + PRODUCT_ID + ": requested=3, available=2",
                exception.getMessage()
        );
        verifyInsert();
        then(reservationRepository).shouldHaveNoMoreInteractions();
        then(inventoryRepository).should().findById(PRODUCT_ID);
        then(inventoryRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void rejectsConcurrentStockChangeBeforeReturningCreatedResult() {
        givenInsertResult(1);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(activeProduct()));
        ProductInventoryEntity inventory = inventoryForUpdate();
        given(inventoryRepository.findById(PRODUCT_ID)).willReturn(Optional.of(inventory));
        given(inventoryRepository.updateReservedQuantity(PRODUCT_ID, 5, 4, NOW)).willReturn(0);

        assertThrows(
                InventoryConcurrentUpdateException.class,
                () -> reservationStorage.create(reservation, IDEMPOTENCY_KEY)
        );

        verifyInsert();
        then(reservationRepository).shouldHaveNoMoreInteractions();
        then(inventoryRepository).should().findById(PRODUCT_ID);
        then(inventoryRepository).should().updateReservedQuantity(PRODUCT_ID, 5, 4, NOW);
        then(inventoryRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void rejectsProductWithoutInventory() {
        givenInsertResult(1);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(activeProduct()));
        given(inventoryRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> reservationStorage.create(reservation, IDEMPOTENCY_KEY)
        );

        assertEquals("Inventory not found for product: " + PRODUCT_ID, exception.getMessage());
        verifyInsert();
        then(reservationRepository).shouldHaveNoMoreInteractions();
        then(inventoryRepository).should().findById(PRODUCT_ID);
        then(inventoryRepository).shouldHaveNoMoreInteractions();
    }

    private void givenInsertResult(int insertedRows) {
        given(reservationRepository.insertIfAbsent(
                RESERVATION_ID, PRODUCT_ID, IDEMPOTENCY_KEY, 3, NOW.plusSeconds(900), NOW
        )).willReturn(insertedRows);
    }

    private void verifyInsert() {
        then(reservationRepository).should().insertIfAbsent(
                RESERVATION_ID, PRODUCT_ID, IDEMPOTENCY_KEY, 3, NOW.plusSeconds(900), NOW
        );
    }

    private ProductEntity activeProduct() {
        return new ProductEntity(
                PRODUCT_ID, "PHONE-001", "Smartphone", "Test product",
                new BigDecimal("799.99"), "USD", NOW
        );
    }

    private ProductInventoryEntity inventoryForUpdate() {
        ProductInventoryEntity inventory = mock(ProductInventoryEntity.class);
        given(inventory.getAvailableQuantity()).willReturn(7);
        given(inventory.getReservedQuantity()).willReturn(2);
        given(inventory.getVersion()).willReturn(4L);
        return inventory;
    }

    private ReservationEntity persistedReservation(UUID reservationId, Instant createdAt) {
        return new ReservationEntity(
                reservationId, PRODUCT_ID, 3, createdAt.plusSeconds(900), createdAt
        );
    }
}
