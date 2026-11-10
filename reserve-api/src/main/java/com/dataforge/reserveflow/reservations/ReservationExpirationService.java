package com.dataforge.reserveflow.reservations;

import com.dataforge.reserveflow.reservations.model.ReservationExpirationCandidate;
import com.dataforge.reserveflow.reservations.storage.ReservationStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ReservationExpirationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationExpirationService.class);

    private final ReservationStorage reservationStorage;
    private final Clock clock;

    public ReservationExpirationService(ReservationStorage reservationStorage, Clock clock) {
        this.reservationStorage = reservationStorage;
        this.clock = clock;
    }

    public int expireBatch(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }

        Instant now = clock.instant();
        List<ReservationExpirationCandidate> candidates = reservationStorage.findExpiredReservations(now, batchSize);

        int expiredCount = 0;
        for (ReservationExpirationCandidate candidate : candidates) {
            try {
                if (reservationStorage.expire(candidate, now)) {
                    expiredCount++;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to expire reservation {}", candidate.reservationId(), exception);
            }
        }

        LOGGER.info("Reservation expiration batch completed: candidates={}, expired={}", candidates.size(), expiredCount);
        return expiredCount;
    }
}
