package com.dataforge.reserveflow.reservations;

import com.dataforge.reserveflow.exceptions.IdempotencyKeyConflictException;
import com.dataforge.reserveflow.exceptions.InvalidReservationQuantityException;
import com.dataforge.reserveflow.reservations.commands.CreateReservationCommand;
import com.dataforge.reserveflow.reservations.model.Reservation;
import com.dataforge.reserveflow.reservations.model.ReservationCreationResult;
import com.dataforge.reserveflow.reservations.model.ReservationStatus;
import com.dataforge.reserveflow.reservations.storage.ReservationStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a");
    private static final UUID RESERVATION_ID = UUID.fromString("b1a64797-831c-42eb-8d6b-16a6050ede49");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("8fa9556c-2c2f-4b34-9af5-f9f020aa9a15");
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    @Mock
    private ReservationStorage reservationStorage;

    @Captor
    private ArgumentCaptor<Reservation> reservationCaptor;

    private ReservationServiceImpl reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationServiceImpl(
                reservationStorage,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsReservationWithKeyAndExpiration() {
        CreateReservationCommand command = new CreateReservationCommand(PRODUCT_ID, IDEMPOTENCY_KEY, 3);
        given(reservationStorage.create(any(Reservation.class), eq(IDEMPOTENCY_KEY)))
                .willAnswer(invocation -> new ReservationCreationResult(invocation.getArgument(0), true));

        ReservationCreationResult result = reservationService.create(command);

        then(reservationStorage).should().create(reservationCaptor.capture(), eq(IDEMPOTENCY_KEY));
        Reservation candidate = reservationCaptor.getValue();

        assertNotNull(candidate.reservationId());
        assertEquals(new Reservation(
                candidate.reservationId(),
                PRODUCT_ID,
                3,
                ReservationStatus.ACTIVE,
                NOW.plusSeconds(900),
                0,
                NOW,
                NOW
        ), candidate);
        assertTrue(result.created());
        assertEquals(candidate, result.reservation());
        then(reservationStorage).shouldHaveNoMoreInteractions();
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"ACTIVE", "EXPIRED"})
    void returnsExistingReservationWithoutRenewingIt(ReservationStatus status) {
        CreateReservationCommand command = new CreateReservationCommand(PRODUCT_ID, IDEMPOTENCY_KEY, 3);
        Reservation existing = existingReservation(status);
        ReservationCreationResult expectedResult = new ReservationCreationResult(existing, false);

        given(reservationStorage.create(any(Reservation.class), eq(IDEMPOTENCY_KEY)))
                .willReturn(expectedResult);

        ReservationCreationResult result = reservationService.create(command);

        assertEquals(expectedResult, result);
        then(reservationStorage).should().create(any(Reservation.class), eq(IDEMPOTENCY_KEY));
        then(reservationStorage).shouldHaveNoMoreInteractions();
    }

    @ParameterizedTest
    @CsvSource({
            "d479265e-8dec-4cfc-b5e8-ab0141e44b3a, 4",
            "fcc1186a-bae6-460a-adfd-a16b843ae3ec, 3"
    })
    void rejectsKeyUsedForDifferentParameters(UUID productId, int quantity) {
        CreateReservationCommand command = new CreateReservationCommand(productId, IDEMPOTENCY_KEY, quantity);
        Reservation existing = existingReservation(ReservationStatus.ACTIVE);

        given(reservationStorage.create(any(Reservation.class), eq(IDEMPOTENCY_KEY)))
                .willReturn(new ReservationCreationResult(existing, false));

        IdempotencyKeyConflictException exception = assertThrows(
                IdempotencyKeyConflictException.class,
                () -> reservationService.create(command)
        );

        assertEquals(
                "Idempotency key was already used with different parameters: " + IDEMPOTENCY_KEY,
                exception.getMessage()
        );
        then(reservationStorage).should().create(any(Reservation.class), eq(IDEMPOTENCY_KEY));
        then(reservationStorage).shouldHaveNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantityBeforeStorage(int quantity) {
        CreateReservationCommand command = new CreateReservationCommand(PRODUCT_ID, IDEMPOTENCY_KEY, quantity);

        InvalidReservationQuantityException exception = assertThrows(
                InvalidReservationQuantityException.class,
                () -> reservationService.create(command)
        );

        assertEquals("Reservation quantity must be positive: " + quantity, exception.getMessage());
        then(reservationStorage).shouldHaveNoInteractions();
    }

    @Test
    void rejectsMissingKeyBeforeStorage() {
        CreateReservationCommand command = new CreateReservationCommand(PRODUCT_ID, null, 3);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> reservationService.create(command)
        );

        assertEquals("Idempotency key must be specified", exception.getMessage());
        then(reservationStorage).shouldHaveNoInteractions();
    }

    private Reservation existingReservation(ReservationStatus status) {
        Instant createdAt = NOW.minusSeconds(3600);
        Instant expiresAt = createdAt.plusSeconds(900);
        boolean active = status == ReservationStatus.ACTIVE;

        return new Reservation(
                RESERVATION_ID,
                PRODUCT_ID,
                3,
                status,
                expiresAt,
                active ? 0 : 1,
                createdAt,
                active ? createdAt : expiresAt.plusSeconds(1)
        );
    }
}
