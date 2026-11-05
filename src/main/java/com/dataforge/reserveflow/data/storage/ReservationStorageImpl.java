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
import com.dataforge.reserveflow.exceptions.ReservationConcurrentUpdateException;
import com.dataforge.reserveflow.exceptions.ReservationExpiredException;
import com.dataforge.reserveflow.exceptions.ReservationNotActiveException;
import com.dataforge.reserveflow.exceptions.ReservationNotFoundException;
import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationCreationResult;
import com.dataforge.reserveflow.reservations.model.ReservationExpirationCandidate;
import com.dataforge.reserveflow.reservations.model.ReservationStatus;
import com.dataforge.reserveflow.reservations.storage.ReservationStorage;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationStorageImpl implements ReservationStorage {

    private final ProductRepository productRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;

    public ReservationStorageImpl(
            ProductRepository productRepository,
            ProductInventoryRepository inventoryRepository,
            ReservationRepository reservationRepository
    ) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    public Optional<Reservation> findById(UUID reservationId) {
        return reservationRepository.findById(reservationId).map(this::toReservation);
    }

    @Override
    public Optional<Reservation> findByIdempotencyKey(UUID idempotencyKey) {
        return reservationRepository.findByIdempotencyKey(idempotencyKey).map(this::toReservation);
    }

    @Override
    @Transactional
    public boolean expire(ReservationExpirationCandidate expiredReservation, Instant expiredAt) {
        int expiredRows = reservationRepository.expireActiveReservation(
                expiredReservation.reservationId(),
                expiredReservation.version(),
                expiredAt
        );
        if (expiredRows == 0) return false;

        UUID productId = expiredReservation.productId();
        ProductInventoryEntity inventory = inventoryRepository
                .findById(productId)
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory not found for product: " + productId
                ));

        int newReservedQuantity = inventory.getReservedQuantity() - expiredReservation.quantity();
        int updatedRows = inventoryRepository.updateReservedQuantity(
                productId,
                newReservedQuantity,
                inventory.getVersion(),
                expiredAt
        );
        if (updatedRows == 0) {
            throw new InventoryConcurrentUpdateException(productId);
        }

        return true;
    }

    @Override
    public List<ReservationExpirationCandidate> findExpiredReservations(Instant now, int batchSize) {
        return reservationRepository.findExpiredReservations(now, batchSize)
                .stream()
                .map(projection -> new ReservationExpirationCandidate(
                        projection.getReservationId(),
                        projection.getProductId(),
                        projection.getQuantity(),
                        projection.getVersion()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void confirm(UUID reservationId, Instant confirmedAt) {
        ReservationEntity reservation = reservationRepository
                .findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ReservationNotActiveException(reservationId, reservation.getStatus());
        }

        if (!reservation.getExpiresAt().isAfter(confirmedAt)) {
            throw new ReservationExpiredException(reservationId);
        }

        int confirmedRows = reservationRepository.confirmActiveReservation(reservationId, reservation.getVersion(), confirmedAt);
        if (confirmedRows == 0) {
            throw new ReservationConcurrentUpdateException(reservationId);
        }

        UUID productId = reservation.getProductId();
        ProductInventoryEntity inventory = inventoryRepository
                .findById(productId)
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory not found for product: " + productId
                ));

        int newOnHandQuantity = inventory.getOnHandQuantity() - reservation.getQuantity();
        int newReservedQuantity = inventory.getReservedQuantity() - reservation.getQuantity();
        int updatedRows = inventoryRepository.updateQuantities(
                productId,
                newOnHandQuantity,
                newReservedQuantity,
                inventory.getVersion(),
                confirmedAt
        );
        if (updatedRows == 0) {
            throw new InventoryConcurrentUpdateException(productId);
        }
    }

    @Override
    @Transactional
    public void cancel(UUID reservationId, Instant cancelledAt) {
        ReservationEntity reservation = reservationRepository
                .findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ReservationNotActiveException(reservationId, reservation.getStatus());
        }

        int cancelledRows = reservationRepository.cancelActiveReservation(reservationId, reservation.getVersion(), cancelledAt);
        if (cancelledRows == 0) {
            throw new ReservationConcurrentUpdateException(reservationId);
        }

        UUID productId = reservation.getProductId();
        ProductInventoryEntity inventory = inventoryRepository
                .findById(productId)
                .orElseThrow(() -> new IllegalStateException("Inventory not found for product: " + productId));

        int newReservedQuantity = inventory.getReservedQuantity() - reservation.getQuantity();
        int updatedRows = inventoryRepository.updateReservedQuantity(
                productId,
                newReservedQuantity,
                inventory.getVersion(),
                cancelledAt
        );

        if (updatedRows == 0) {
            throw new InventoryConcurrentUpdateException(productId);
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationCreationResult create(Reservation reservation, UUID idempotencyKey) {
        // 1. Insert first so the unique key coordinates concurrent retries.
        // Any later failure rolls back both this insert and the inventory update.
        int insertedRows = reservationRepository.insertIfAbsent(
                reservation.reservationId(),
                reservation.productId(),
                idempotencyKey,
                reservation.quantity(),
                reservation.expiresAt(),
                reservation.createdAt()
        );

        // 2. Return an existing reservation before checking the current product state.
        // If no reservation has this key, the INSERT found no matching product.
        if (insertedRows == 0) {
            Reservation existing = findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new ProductNotFoundException(reservation.productId()));

            return new ReservationCreationResult(existing, false);
        }

        // 3. Validate the product only for a newly inserted reservation.
        ProductEntity product = productRepository
                .findById(reservation.productId())
                .orElseThrow(() -> new ProductNotFoundException(reservation.productId()));

        if (!product.isActive()) {
            throw new ProductInactiveException(reservation.productId());
        }

        // 4. Load inventory and check availability before calculating the reserved amount.
        ProductInventoryEntity productInventory = inventoryRepository
                .findById(reservation.productId())
                .orElseThrow(() -> {
                    String message = "Inventory not found for product: " + reservation.productId();
                    return new IllegalStateException(message);
                });

        int newReservedQuantity = getNewReservedQuantity(reservation, productInventory);

        // 5. Match the inventory version to reject updates based on stale stock.
        int updatedRows = inventoryRepository.updateReservedQuantity(
                reservation.productId(),
                newReservedQuantity,
                productInventory.getVersion(),
                reservation.updatedAt()
        );

        if (updatedRows == 0) {
            throw new InventoryConcurrentUpdateException(reservation.productId());
        }

        // 6. Read back the created reservation with the values stored in the database.
        Reservation created = findById(reservation.reservationId())
                .orElseThrow(() -> new IllegalStateException("Reservation not found after insert: " + reservation.reservationId()));
        return new ReservationCreationResult(created, true);
    }

    private static int getNewReservedQuantity(Reservation reservation, ProductInventoryEntity productInventory) {
        int requestedQuantity = reservation.quantity();
        int availableQuantity = productInventory.getAvailableQuantity();
        if (requestedQuantity > availableQuantity) {
            throw new InsufficientStockException(reservation.productId(), reservation.quantity(), availableQuantity);
        }

        int alreadyReservedQuantity = productInventory.getReservedQuantity();
        return alreadyReservedQuantity + requestedQuantity;
    }

    private Reservation toReservation(ReservationEntity entity) {
        return new Reservation(
                entity.getReservationId(),
                entity.getProductId(),
                entity.getQuantity(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
