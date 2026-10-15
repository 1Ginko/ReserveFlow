package com.dataforge.reserveflow.data.entity;

import com.dataforge.reserveflow.reservations.model.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = ReservationEntity.RESERVATION_TABLE)
public class ReservationEntity {

    @Id
    @Column(name = RESERVATION_ID, nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = PRODUCT_ID, nullable = false, updatable = false)
    private UUID productId;

    @Column(name = QUANTITY, nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = RESERVATION_STATUS, nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = EXPIRES_AT, nullable = false, updatable = false)
    private Instant expiresAt;

    @Version
    @Column(name = RESERVATION_VERSION, nullable = false)
    private long version;

    @Column(name = CREATED_AT, nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = UPDATED_AT, nullable = false)
    private Instant updatedAt;

    /** Required by JPA to create an entity when reading it from the database. */
    protected ReservationEntity() {}

    public ReservationEntity(
            UUID reservationId,
            UUID productId,
            int quantity,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.reservationId = reservationId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = ReservationStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    static final String RESERVATION_TABLE = "reservations";
    static final String RESERVATION_ID = "reservation_id";
    static final String PRODUCT_ID = "product_id";
    static final String QUANTITY = "quantity";
    static final String RESERVATION_STATUS = "reservation_status";
    static final String EXPIRES_AT = "expires_at";
    static final String RESERVATION_VERSION = "version";
    static final String CREATED_AT = "created_at";
    static final String UPDATED_AT = "updated_at";
}