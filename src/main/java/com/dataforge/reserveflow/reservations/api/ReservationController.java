package com.dataforge.reserveflow.reservations.api;

import com.dataforge.reserveflow.reservations.ReservationService;
import com.dataforge.reserveflow.reservations.api.model.request.CreateReservationRequest;
import com.dataforge.reserveflow.reservations.api.model.response.ReservationResponse;
import com.dataforge.reserveflow.reservations.commands.CreateReservationCommand;
import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationCreationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @GetMapping("/{reservationId}")
    public ReservationResponse findById(@PathVariable UUID reservationId) {
        Reservation reservation = reservationService.findById(reservationId);
        return ReservationControllerMapper.toResponse(reservation);
    }

    @PostMapping("/{reservationId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@PathVariable UUID reservationId) {
        reservationService.confirm(reservationId);
    }

    @PostMapping("/{reservationId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID reservationId) {
        reservationService.cancel(reservationId);
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreateReservationRequest request
    ) {
        CreateReservationCommand command = ReservationControllerMapper.toCommand(idempotencyKey, request);
        ReservationCreationResult result = reservationService.create(command);
        ReservationResponse response = ReservationControllerMapper.toResponse(result.reservation());

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }
}