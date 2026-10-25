package com.dataforge.reserveflow.reservations.api;

import com.dataforge.reserveflow.exceptions.InsufficientStockException;
import com.dataforge.reserveflow.exceptions.ProductNotFoundException;
import com.dataforge.reserveflow.exceptions.ReservationConcurrentUpdateException;
import com.dataforge.reserveflow.exceptions.ReservationNotActiveException;
import com.dataforge.reserveflow.exceptions.ReservationNotFoundException;
import com.dataforge.reserveflow.reservations.ReservationService;
import com.dataforge.reserveflow.reservations.commands.CreateReservationCommand;
import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private Clock clock;

    @Test
    void createsReservation() throws Exception {
        UUID reservationId =
                UUID.fromString("b1a64797-831c-42eb-8d6b-16a6050ede49");

        UUID productId =
                UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");

        Instant createdAt = Instant.parse("2026-10-24T08:25:16Z");
        Instant expiresAt = Instant.parse("2026-10-24T08:40:16Z");

        CreateReservationCommand command =
                new CreateReservationCommand(productId, 3);

        Reservation reservation = new Reservation(
                reservationId,
                productId,
                3,
                ReservationStatus.ACTIVE,
                expiresAt,
                0,
                createdAt,
                createdAt
        );

        given(reservationService.create(command)).willReturn(reservation);

        mockMvc.perform(post("/api/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "d479265e-8dec-4cfc-b5e8-ab0141e44b3a",
                                  "quantity": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId")
                        .value(reservationId.toString()))
                .andExpect(jsonPath("$.productId")
                        .value(productId.toString()))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.updatedAt").value(createdAt.toString()));

        then(reservationService).should().create(command);
    }

    @Test
    void cancelsReservation() throws Exception {
        UUID reservationId =
                UUID.fromString("b1a64797-831c-42eb-8d6b-16a6050ede49");

        mockMvc.perform(post("/api/reservations/{reservationId}/cancel", reservationId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(reservationService).should().cancel(reservationId);
    }

    @Test
    void rejectsCancellationOfUnknownReservation() throws Exception {
        UUID reservationId =
                UUID.fromString("00000000-0000-0000-0000-000000000001");

        Instant now = Instant.parse("2026-10-25T09:25:00Z");
        given(clock.instant()).willReturn(now);
        willThrow(new ReservationNotFoundException(reservationId))
                .given(reservationService).cancel(reservationId);

        mockMvc.perform(post("/api/reservations/{reservationId}/cancel", reservationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Reservation not found: " + reservationId))
                .andExpect(jsonPath("$.path")
                        .value("/api/reservations/" + reservationId + "/cancel"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(reservationService).should().cancel(reservationId);
    }

    @Test
    void rejectsCancellationOfCancelledReservation() throws Exception {
        UUID reservationId =
                UUID.fromString("b1a64797-831c-42eb-8d6b-16a6050ede49");

        Instant now = Instant.parse("2026-10-25T09:27:14Z");
        given(clock.instant()).willReturn(now);
        willThrow(new ReservationNotActiveException(reservationId, ReservationStatus.CANCELLED))
                .given(reservationService).cancel(reservationId);

        mockMvc.perform(post("/api/reservations/{reservationId}/cancel", reservationId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Reservation is not active: " + reservationId + ", status=CANCELLED"))
                .andExpect(jsonPath("$.path")
                        .value("/api/reservations/" + reservationId + "/cancel"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(reservationService).should().cancel(reservationId);
    }

    @Test
    void rejectsConcurrentCancellation() throws Exception {
        UUID reservationId =
                UUID.fromString("b1a64797-831c-42eb-8d6b-16a6050ede49");

        Instant now = Instant.parse("2026-10-25T09:28:00Z");
        given(clock.instant()).willReturn(now);
        willThrow(new ReservationConcurrentUpdateException(reservationId))
                .given(reservationService).cancel(reservationId);

        mockMvc.perform(post("/api/reservations/{reservationId}/cancel", reservationId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Reservation was concurrently updated: " + reservationId))
                .andExpect(jsonPath("$.path")
                        .value("/api/reservations/" + reservationId + "/cancel"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(reservationService).should().cancel(reservationId);
    }

    @Test
    void rejectsInvalidQuantity() throws Exception {
        Instant now = Instant.parse("2026-10-24T08:33:00Z");
        given(clock.instant()).willReturn(now);

        mockMvc.perform(post("/api/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "d479265e-8dec-4cfc-b5e8-ab0141e44b3a",
                                  "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/reservations"))
                .andExpect(jsonPath("$.fieldErrors.quantity")
                        .value("Quantity must be positive"));

        verifyNoInteractions(reservationService);
    }

    @Test
    void rejectsUnknownProduct() throws Exception {
        UUID productId =
                UUID.fromString("00000000-0000-0000-0000-000000000001");

        Instant now = Instant.parse("2026-10-24T08:34:00Z");
        CreateReservationCommand command =
                new CreateReservationCommand(productId, 1);

        given(clock.instant()).willReturn(now);
        given(reservationService.create(command))
                .willThrow(new ProductNotFoundException(productId));

        mockMvc.perform(post("/api/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "00000000-0000-0000-0000-000000000001",
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Product not found: " + productId))
                .andExpect(jsonPath("$.path").value("/api/reservations"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(reservationService).should().create(command);
    }

    @Test
    void rejectsInsufficientStock() throws Exception {
        UUID productId =
                UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");

        Instant now = Instant.parse("2026-10-24T08:35:00Z");
        CreateReservationCommand command =
                new CreateReservationCommand(productId, 100);

        given(clock.instant()).willReturn(now);
        given(reservationService.create(command))
                .willThrow(new InsufficientStockException(productId, 100, 7));

        mockMvc.perform(post("/api/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "d479265e-8dec-4cfc-b5e8-ab0141e44b3a",
                                  "quantity": 100
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(now.toString()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Insufficient stock for product " + productId +
                        ": requested=100, available=7"
                ))
                .andExpect(jsonPath("$.path").value("/api/reservations"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        then(reservationService).should().create(command);
    }
}
