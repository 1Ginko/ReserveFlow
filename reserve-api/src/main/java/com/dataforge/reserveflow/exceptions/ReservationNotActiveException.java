package com.dataforge.reserveflow.exceptions;

import com.dataforge.reserveflow.reservations.model.ReservationStatus;

import java.util.UUID;

public class ReservationNotActiveException extends RuntimeException {

    public ReservationNotActiveException(UUID reservationId, ReservationStatus status) {
        super("Reservation is not active: " + reservationId + ", status=" + status);
    }
}