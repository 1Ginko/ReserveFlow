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
import com.dataforge.reserveflow.reservations.model.ReservationStatus;
import com.dataforge.reserveflow.reservations.storage.ReservationStorage;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    @Transactional
    public void create(Reservation reservation) {
        ProductEntity product = productRepository
                .findById(reservation.productId())
                .orElseThrow(() -> new ProductNotFoundException(reservation.productId()));

        if (!product.isActive()) {
            throw new ProductInactiveException(reservation.productId());
        }

        ProductInventoryEntity productInventory = inventoryRepository
                .findById(reservation.productId())
                .orElseThrow(() -> {
                    String message = "Inventory not found for product: " + reservation.productId();
                    return new IllegalStateException(message);
                });

        int newReservedQuantity = getNewReservedQuantity(reservation, productInventory);
        int updatedRows = inventoryRepository.updateReservedQuantity(
                reservation.productId(),
                newReservedQuantity,
                productInventory.getVersion(),
                reservation.updatedAt()
        );

        if (updatedRows == 0) {
            throw new InventoryConcurrentUpdateException(reservation.productId());
        }

        ReservationEntity reservationEntity = new ReservationEntity(
                reservation.reservationId(),
                reservation.productId(),
                reservation.quantity(),
                reservation.expiresAt(),
                reservation.createdAt()
        );
        reservationRepository.save(reservationEntity);
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
}