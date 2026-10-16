package com.dataforge.reserveflow.reservations.model;

import java.time.Instant;
import java.util.UUID;

public record Reservation(
        UUID reservationId,
        UUID productId,

        int quantity,
        ReservationStatus status,

        Instant expiresAt,
        long version,

        Instant createdAt,
        Instant updatedAt
) { }