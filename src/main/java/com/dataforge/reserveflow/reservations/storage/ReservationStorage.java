package com.dataforge.reserveflow.reservations.storage;

import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationExpirationCandidate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationStorage {

    void create(Reservation reservation);

    void cancel(UUID reservationId, Instant cancelledAt);

    void confirm(UUID reservationId, Instant confirmedAt);

    List<ReservationExpirationCandidate> findExpiredReservations(Instant now, int batchSize);

    boolean expire(ReservationExpirationCandidate candidate, Instant expiredAt);
}