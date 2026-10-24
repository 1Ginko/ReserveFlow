package com.dataforge.reserveflow.reservations.api.model;

import com.dataforge.reserveflow.reservations.model.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID productId,
        int quantity,
        ReservationStatus status,
        Instant expiresAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}