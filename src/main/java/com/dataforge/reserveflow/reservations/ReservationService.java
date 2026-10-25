package com.dataforge.reserveflow.reservations;

import com.dataforge.reserveflow.exceptions.InvalidReservationQuantityException;
import com.dataforge.reserveflow.reservations.commands.CreateReservationCommand;
import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationStatus;
import com.dataforge.reserveflow.reservations.storage.ReservationStorage;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class ReservationService {
    private static final Duration RESERVATION_DURATION = Duration.ofMinutes(15);

    private final ReservationStorage reservationStorage;
    private final Clock clock;

    public ReservationService(ReservationStorage reservationStorage, Clock clock) {
        this.reservationStorage = reservationStorage;
        this.clock = clock;
    }

    public void cancel(UUID reservationId) {
        reservationStorage.cancel(reservationId, clock.instant());
    }

    public Reservation create(CreateReservationCommand command) {
        if (command.quantity() <= 0) throw new InvalidReservationQuantityException(command.quantity());

        Instant now = clock.instant();
        Reservation reservation = new Reservation(
                UUID.randomUUID(),
                command.productId(),
                command.quantity(),
                ReservationStatus.ACTIVE,
                now.plus(RESERVATION_DURATION),
                0,
                now,
                now
        );
        reservationStorage.create(reservation);
        return reservation;
    }
}
