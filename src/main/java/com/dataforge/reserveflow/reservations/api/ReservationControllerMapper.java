package com.dataforge.reserveflow.reservations.api;

import com.dataforge.reserveflow.reservations.api.model.CreateReservationRequest;
import com.dataforge.reserveflow.reservations.api.model.ReservationResponse;
import com.dataforge.reserveflow.reservations.commands.CreateReservationCommand;
import com.dataforge.reserveflow.reservations.model.Reservation;

public final class ReservationControllerMapper {

    private ReservationControllerMapper() {}

    public static CreateReservationCommand toCommand(
            CreateReservationRequest request
    ) {
        return new CreateReservationCommand(
                request.productId(),
                request.quantity()
        );
    }

    public static ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.reservationId(),
                reservation.productId(),
                reservation.quantity(),
                reservation.status(),
                reservation.expiresAt(),
                reservation.version(),
                reservation.createdAt(),
                reservation.updatedAt()
        );
    }
}