package com.dataforge.reserveflow.data.storage;

import com.dataforge.reserveflow.config.JwtTestConfiguration;
import com.dataforge.reserveflow.config.PostgresTestConfiguration;
import com.dataforge.reserveflow.data.entity.ProductEntity;
import com.dataforge.reserveflow.data.entity.ProductInventoryEntity;
import com.dataforge.reserveflow.data.entity.ReservationEntity;
import com.dataforge.reserveflow.data.repository.ProductInventoryRepository;
import com.dataforge.reserveflow.data.repository.ProductRepository;
import com.dataforge.reserveflow.data.repository.ReservationRepository;
import com.dataforge.reserveflow.exceptions.InsufficientStockException;
import com.dataforge.reserveflow.exceptions.InventoryConcurrentUpdateException;
import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationCreationResult;
import com.dataforge.reserveflow.reservations.model.ReservationStatus;
import com.dataforge.reserveflow.reservations.storage.ReservationStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.reservation-expiration.enabled=false")
@Import({PostgresTestConfiguration.class, JwtTestConfiguration.class})
class ReservationStorageIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("4eeaa832-8a05-4c00-b45c-5a1c18746a41");
    private static final UUID OTHER_USER_ID = UUID.fromString("82fe9011-656b-46ce-83ba-2d211a5b33c2");
    private static final UUID FIRST_RETRY_ID = UUID.fromString("34f97d89-c675-49ad-9ac3-05e40dc7becc");
    private static final UUID SECOND_RETRY_ID = UUID.fromString("99da0a0f-422a-4860-99ad-4b4276c97982");

    private static final UUID PRODUCT_ID = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
    private static final UUID RESERVATION_ID = UUID.fromString("6cfe755a-a85b-4ba3-80cd-d05451686746");
    private static final UUID SECOND_RESERVATION_ID = UUID.fromString("b1a64797-831c-42eb-8d6b-16a6050ede49");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("8fa9556c-2c2f-4b34-9af5-f9f020aa9a15");
    private static final UUID SECOND_IDEMPOTENCY_KEY = UUID.fromString("b83d6636-4d6d-4ee1-a9e9-c53ce8e619b6");
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00.123456Z");

    @Autowired
    private ReservationStorage reservationStorage;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductInventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAllInBatch();
        inventoryRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        productRepository.saveAndFlush(new ProductEntity(
                PRODUCT_ID,
                "PHONE-001",
                "Smartphone",
                "Test product",
                new BigDecimal("799.99"),
                "USD",
                NOW
        ));
        inventoryRepository.saveAndFlush(new ProductInventoryEntity(PRODUCT_ID, 10, NOW));
    }

    @Test
    void repeatedIdempotencyKeyCreatesOneReservationAndReservesStockOnce() {
        Reservation firstRequest = reservation(RESERVATION_ID, 3, NOW);
        Reservation repeatedRequest = reservation(SECOND_RESERVATION_ID, 3, NOW.plusSeconds(60));

        ReservationCreationResult firstResult = reservationStorage.create(firstRequest, IDEMPOTENCY_KEY);
        ReservationCreationResult repeatedResult = reservationStorage.create(repeatedRequest, IDEMPOTENCY_KEY);

        assertTrue(firstResult.created());
        assertFalse(repeatedResult.created());
        assertEquals(firstRequest, firstResult.reservation());
        assertEquals(firstResult.reservation(), repeatedResult.reservation());

        assertEquals(1L, reservationRepository.count());
        ReservationEntity stored = reservationRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY).orElseThrow();
        assertEquals(RESERVATION_ID, stored.getReservationId());
        assertEquals(USER_ID, stored.getUserId());

        ProductInventoryEntity inventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();
        assertEquals(10, inventory.getOnHandQuantity());
        assertEquals(3, inventory.getReservedQuantity());
        assertEquals(7, inventory.getAvailableQuantity());
        assertEquals(1L, inventory.getVersion());
    }

    @Test
    void insufficientStockRollsBackReservationAndReleasesIdempotencyKey() {
        Reservation rejectedRequest = reservation(RESERVATION_ID, 11, NOW);

        assertThrows(
                InsufficientStockException.class,
                () -> reservationStorage.create(rejectedRequest, IDEMPOTENCY_KEY)
        );

        assertEquals(0L, reservationRepository.count());
        assertTrue(reservationRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY).isEmpty());

        ProductInventoryEntity unchangedInventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();
        assertEquals(10, unchangedInventory.getOnHandQuantity());
        assertEquals(0, unchangedInventory.getReservedQuantity());
        assertEquals(0L, unchangedInventory.getVersion());

        Reservation retry = reservation(SECOND_RESERVATION_ID, 3, NOW.plusSeconds(60));
        ReservationCreationResult retryResult = reservationStorage.create(retry, IDEMPOTENCY_KEY);

        assertTrue(retryResult.created());
        assertEquals(retry, retryResult.reservation());
        assertEquals(1L, reservationRepository.count());
        ReservationEntity stored = reservationRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY).orElseThrow();
        assertEquals(SECOND_RESERVATION_ID, stored.getReservationId());

        ProductInventoryEntity updatedInventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();
        assertEquals(10, updatedInventory.getOnHandQuantity());
        assertEquals(3, updatedInventory.getReservedQuantity());
        assertEquals(1L, updatedInventory.getVersion());
    }

    @Test
    void concurrentRequestsWithSameKeyWaitForCommitAndReserveStockOnce() throws Exception {
        Reservation firstRequest = reservation(RESERVATION_ID, 3, NOW);
        Reservation secondRequest = reservation(SECOND_RESERVATION_ID, 3, NOW.plusSeconds(60));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        CompletableFuture<Integer> firstTransactionPid = new CompletableFuture<>();
        CompletableFuture<Void> allowCommit = new CompletableFuture<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                CompletableFuture<ReservationCreationResult> first = CompletableFuture.supplyAsync(
                        () -> transaction.execute(status -> {
                            ReservationCreationResult result = reservationStorage.create(firstRequest, IDEMPOTENCY_KEY);
                            firstTransactionPid.complete(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            // Hold the inserted key until the competing transaction is blocked by it.
                            allowCommit.join();
                            return result;
                        }),
                        executor
                );
                first.whenComplete((result, failure) -> {
                    if (failure != null) {
                        firstTransactionPid.completeExceptionally(failure);
                    }
                });

                int blockingPid = firstTransactionPid.get(10, TimeUnit.SECONDS);
                Future<ReservationCreationResult> second = executor.submit(
                        () -> reservationStorage.create(secondRequest, IDEMPOTENCY_KEY)
                );

                await().alias("second reservation transaction waits for the first commit")
                        .atMost(10, TimeUnit.SECONDS)
                        .until(() -> isAnotherTransactionBlockedBy(blockingPid));

                allowCommit.complete(null);
                ReservationCreationResult firstResult = first.get(10, TimeUnit.SECONDS);
                ReservationCreationResult secondResult = second.get(10, TimeUnit.SECONDS);

                assertTrue(firstResult.created());
                assertFalse(secondResult.created());
                assertEquals(firstRequest, firstResult.reservation());
                assertEquals(firstResult.reservation(), secondResult.reservation());

                assertEquals(1L, reservationRepository.count());
                ReservationEntity stored = reservationRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY).orElseThrow();
                assertEquals(RESERVATION_ID, stored.getReservationId());

                ProductInventoryEntity inventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();
                assertEquals(10, inventory.getOnHandQuantity());
                assertEquals(3, inventory.getReservedQuantity());
                assertEquals(7, inventory.getAvailableQuantity());
                assertEquals(1L, inventory.getVersion());
            } finally {
                allowCommit.complete(null);
            }
        }
    }

    @Test
    void concurrentReservationsCannotReserveMoreThanAvailableStock() throws Exception {
        Reservation firstRequest = reservation(RESERVATION_ID, 7, NOW);
        Reservation secondRequest = reservation(SECOND_RESERVATION_ID, 7, NOW.plusSeconds(60));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        CompletableFuture<Integer> firstTransactionPid = new CompletableFuture<>();
        CompletableFuture<Void> allowCommit = new CompletableFuture<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                CompletableFuture<ReservationCreationResult> first = CompletableFuture.supplyAsync(
                        () -> transaction.execute(status -> {
                            ReservationCreationResult result = reservationStorage.create(firstRequest, IDEMPOTENCY_KEY);
                            firstTransactionPid.complete(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            // Keep the stock change uncommitted so the second request reads the old version.
                            allowCommit.join();
                            return result;
                        }),
                        executor
                );
                first.whenComplete((result, failure) -> {
                    if (failure != null) {
                        firstTransactionPid.completeExceptionally(failure);
                    }
                });

                int blockingPid = firstTransactionPid.get(10, TimeUnit.SECONDS);
                Future<ReservationCreationResult> second = executor.submit(
                        () -> reservationStorage.create(secondRequest, SECOND_IDEMPOTENCY_KEY)
                );

                await().alias("second reservation waits to update inventory using the old version")
                        .atMost(10, TimeUnit.SECONDS)
                        .until(() -> isAnotherTransactionBlockedBy(blockingPid));

                allowCommit.complete(null);
                ReservationCreationResult firstResult = first.get(10, TimeUnit.SECONDS);
                assertTrue(firstResult.created());
                assertEquals(firstRequest, firstResult.reservation());

                ExecutionException failure = assertThrows(
                        ExecutionException.class,
                        () -> second.get(10, TimeUnit.SECONDS)
                );
                assertInstanceOf(InventoryConcurrentUpdateException.class, failure.getCause());

                assertEquals(1L, reservationRepository.count());
                ReservationEntity stored = reservationRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY).orElseThrow();
                assertEquals(RESERVATION_ID, stored.getReservationId());
                assertTrue(reservationRepository.findByUserIdAndIdempotencyKey(USER_ID, SECOND_IDEMPOTENCY_KEY).isEmpty());

                ProductInventoryEntity inventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();
                assertEquals(10, inventory.getOnHandQuantity());
                assertEquals(7, inventory.getReservedQuantity());
                assertEquals(3, inventory.getAvailableQuantity());
                assertEquals(1L, inventory.getVersion());
            } finally {
                allowCommit.complete(null);
            }
        }
    }

    @Test
    void sameKeyForDifferentUsersCreatesIndependentReservationsAndReplaysOnlyTheirOwn() {
        Reservation firstRequest = reservation(RESERVATION_ID, USER_ID, 3, NOW);
        Reservation secondRequest = reservation(SECOND_RESERVATION_ID, OTHER_USER_ID, 4, NOW);

        ReservationCreationResult firstResult = reservationStorage.create(firstRequest, IDEMPOTENCY_KEY);
        assertTrue(reservationRepository
                .findByUserIdAndIdempotencyKey(OTHER_USER_ID, IDEMPOTENCY_KEY).isEmpty());
        ReservationCreationResult secondResult = reservationStorage.create(secondRequest, IDEMPOTENCY_KEY);

        assertTrue(firstResult.created());
        assertTrue(secondResult.created());
        assertEquals(firstRequest, firstResult.reservation());
        assertEquals(secondRequest, secondResult.reservation());
        assertEquals(USER_ID, reservationRepository
                .findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY).orElseThrow().getUserId());
        assertEquals(OTHER_USER_ID, reservationRepository
                .findByUserIdAndIdempotencyKey(OTHER_USER_ID, IDEMPOTENCY_KEY).orElseThrow().getUserId());

        ReservationCreationResult firstRetry = reservationStorage.create(
                reservation(FIRST_RETRY_ID, USER_ID, 3, NOW.plusSeconds(60)), IDEMPOTENCY_KEY
        );
        ReservationCreationResult secondRetry = reservationStorage.create(
                reservation(SECOND_RETRY_ID, OTHER_USER_ID, 4, NOW.plusSeconds(60)), IDEMPOTENCY_KEY
        );

        assertFalse(firstRetry.created());
        assertFalse(secondRetry.created());
        assertEquals(firstResult.reservation(), firstRetry.reservation());
        assertEquals(secondResult.reservation(), secondRetry.reservation());
        assertEquals(2L, reservationRepository.count());

        ProductInventoryEntity inventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();
        assertEquals(10, inventory.getOnHandQuantity());
        assertEquals(7, inventory.getReservedQuantity());
        assertEquals(3, inventory.getAvailableQuantity());
        assertEquals(2L, inventory.getVersion());
    }

    @Test
    void legacyReservationWithoutOwnerDoesNotClaimAnotherUsersKey() {
        jdbcTemplate.update("""
                INSERT INTO reservations (
                    reservation_id, product_id, idempotency_key, quantity,
                    reservation_status, expires_at, version, created_at, updated_at
                )
                VALUES (?, ?, ?, 1, 'CANCELLED', ?, 1, ?, ?)
                """,
                RESERVATION_ID, PRODUCT_ID, IDEMPOTENCY_KEY,
                NOW.plusSeconds(900).atOffset(ZoneOffset.UTC),
                NOW.atOffset(ZoneOffset.UTC), NOW.atOffset(ZoneOffset.UTC)
        );
        assertNull(reservationStorage.findById(RESERVATION_ID).orElseThrow().userId());
        assertTrue(reservationRepository
                .findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY).isEmpty());

        Reservation request = reservation(SECOND_RESERVATION_ID, USER_ID, 3, NOW);
        ReservationCreationResult result = reservationStorage.create(request, IDEMPOTENCY_KEY);

        assertTrue(result.created());
        assertEquals(request, result.reservation());
        assertEquals(2L, reservationRepository.count());
        assertNull(reservationRepository.findById(RESERVATION_ID).orElseThrow().getUserId());
        assertEquals(SECOND_RESERVATION_ID, reservationRepository
                .findByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY).orElseThrow().getReservationId());
        assertEquals(3, inventoryRepository.findById(PRODUCT_ID).orElseThrow().getReservedQuantity());
    }

    private boolean isAnotherTransactionBlockedBy(int blockingPid) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND ? = ANY(pg_blocking_pids(pid))
                )
                """,
                Boolean.class,
                blockingPid
        ));
    }

    private Reservation reservation(UUID reservationId, int quantity, Instant createdAt) {
        return reservation(reservationId, USER_ID, quantity, createdAt);
    }

    private Reservation reservation(UUID reservationId, UUID userId, int quantity, Instant createdAt) {
        return new Reservation(
                reservationId,
                PRODUCT_ID,
                userId,
                quantity,
                ReservationStatus.ACTIVE,
                createdAt.plusSeconds(900),
                0,
                createdAt,
                createdAt
        );
    }
}
