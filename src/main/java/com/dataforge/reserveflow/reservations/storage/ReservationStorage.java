package com.dataforge.reserveflow.reservations.storage;

import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationCreationResult;
import com.dataforge.reserveflow.reservations.model.ReservationExpirationCandidate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationStorage {

    ReservationCreationResult create(Reservation reservation, UUID idempotencyKey);

    void cancel(UUID reservationId, Instant cancelledAt);

    void confirm(UUID reservationId, Instant confirmedAt);

    List<ReservationExpirationCandidate> findExpiredReservations(Instant now, int batchSize);

    Optional<Reservation> findById(UUID reservationId);

    Optional<Reservation> findByIdempotencyKey(UUID idempotencyKey);

    boolean expire(ReservationExpirationCandidate candidate, Instant expiredAt);
}