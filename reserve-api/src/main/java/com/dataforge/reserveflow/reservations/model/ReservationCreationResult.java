package com.dataforge.reserveflow.reservations.model;

public record ReservationCreationResult(
        Reservation reservation,
        boolean created
) {}