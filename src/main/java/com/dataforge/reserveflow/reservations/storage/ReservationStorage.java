package com.dataforge.reserveflow.reservations.storage;

import com.dataforge.reserveflow.reservations.model.Reservation;

import java.time.Instant;
import java.util.UUID;

public interface ReservationStorage {

    void create(Reservation reservation);

    void cancel(UUID reservationId, Instant cancelledAt);

    void confirm(UUID reservationId, Instant confirmedAt);
}