package com.dataforge.reserveflow.reservations.api;

import com.dataforge.reserveflow.reservations.ReservationService;
import com.dataforge.reserveflow.reservations.api.model.CreateReservationRequest;
import com.dataforge.reserveflow.reservations.api.model.ReservationResponse;
import com.dataforge.reserveflow.reservations.commands.CreateReservationCommand;
import com.dataforge.reserveflow.reservations.model.Reservation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{reservationId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID reservationId) {
        reservationService.cancel(reservationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        CreateReservationCommand command = ReservationControllerMapper.toCommand(request);
        Reservation reservation = reservationService.create(command);
        return ReservationControllerMapper.toResponse(reservation);
    }
}