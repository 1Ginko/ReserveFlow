package com.dataforge.reserveflow.exceptions;

import java.util.UUID;

public class ReservationConcurrentUpdateException extends RuntimeException {

    public ReservationConcurrentUpdateException(UUID reservationId) {
        super("Reservation was concurrently updated: " + reservationId);
    }
}