package com.dataforge.reserveflow.reservations;

import com.dataforge.reserveflow.reservations.commands.CreateReservationCommand;
import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationCreationResult;

import java.util.UUID;

public interface ReservationService {

    Reservation findById(UUID reservationId);

    ReservationCreationResult create(CreateReservationCommand command);

    void cancel(UUID reservationId);

    void confirm(UUID reservationId);
}