package com.dataforge.reserveflow.reservations.api;

import com.dataforge.reserveflow.reservations.api.model.request.CreateReservationRequest;
import com.dataforge.reserveflow.reservations.api.model.response.ReservationResponse;
import com.dataforge.reserveflow.reservations.commands.CreateReservationCommand;
import com.dataforge.reserveflow.reservations.model.Reservation;

import java.util.UUID;

public final class ReservationControllerMapper {

    private ReservationControllerMapper() {}

    public static CreateReservationCommand toCommand(
            UUID idempotencyKey,
            CreateReservationRequest request
    ) {
        return new CreateReservationCommand(
                request.productId(),
                idempotencyKey,
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