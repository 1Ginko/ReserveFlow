package com.dataforge.reserveflow.reservations.model;

import java.util.UUID;

public record ReservationExpirationCandidate(
        UUID reservationId,
        UUID productId,
        int quantity,
        long version
) {
}