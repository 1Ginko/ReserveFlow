package com.dataforge.reserveflow.exceptions;

public class InvalidReservationQuantityException extends RuntimeException {

    public InvalidReservationQuantityException(int quantity) {
        super("Reservation quantity must be positive: " + quantity);
    }
}