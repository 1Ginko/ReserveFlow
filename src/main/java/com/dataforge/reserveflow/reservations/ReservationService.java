package com.dataforge.reserveflow.reservations;

import com.dataforge.reserveflow.reservations.commands.CreateReservationCommand;
import com.dataforge.reserveflow.reservations.model.Reservation;

import java.util.UUID;

public interface ReservationService {

    Reservation findById(UUID reservationId);

    Reservation create(CreateReservationCommand command);

    void cancel(UUID reservationId);

    void confirm(UUID reservationId);
}