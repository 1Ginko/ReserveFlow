package com.dataforge.reserveflow.reservations;

import com.dataforge.reserveflow.exceptions.InventoryConcurrentUpdateException;
import com.dataforge.reserveflow.reservations.model.ReservationExpirationCandidate;
import com.dataforge.reserveflow.reservations.storage.ReservationStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    @Mock
    private ReservationStorage reservationStorage;

    private ReservationExpirationService expirationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        expirationService = new ReservationExpirationService(reservationStorage, clock);
    }

    @Test
    void returnsZeroWhenNoExpiredReservations() {
        given(reservationStorage.findExpiredReservations(NOW, 100)).willReturn(List.of());

        int expiredCount = expirationService.expireBatch(100);

        assertEquals(0, expiredCount);
        then(reservationStorage).should().findExpiredReservations(NOW, 100);
        then(reservationStorage).shouldHaveNoMoreInteractions();
    }

    @Test
    void countsSuccessfullyExpiredReservations() {
        ReservationExpirationCandidate first = createCandidate("b1a64797-831c-42eb-8d6b-16a6050ede49");
        ReservationExpirationCandidate second = createCandidate("6cfe755a-a85b-4ba3-80cd-d05451686746");

        given(reservationStorage.findExpiredReservations(NOW, 2)).willReturn(List.of(first, second));
        given(reservationStorage.expire(first, NOW)).willReturn(true);
        given(reservationStorage.expire(second, NOW)).willReturn(true);

        int expiredCount = expirationService.expireBatch(2);

        assertEquals(2, expiredCount);
        then(reservationStorage).should().findExpiredReservations(NOW, 2);
        then(reservationStorage).should().expire(first, NOW);
        then(reservationStorage).should().expire(second, NOW);
    }

    @Test
    void skipsCandidatesThatAreNoLongerEligible() {
        ReservationExpirationCandidate skipped = createCandidate("b1a64797-831c-42eb-8d6b-16a6050ede49");
        ReservationExpirationCandidate expired = createCandidate("6cfe755a-a85b-4ba3-80cd-d05451686746");

        given(reservationStorage.findExpiredReservations(NOW, 100)).willReturn(List.of(skipped, expired));
        given(reservationStorage.expire(skipped, NOW)).willReturn(false);
        given(reservationStorage.expire(expired, NOW)).willReturn(true);

        int expiredCount = expirationService.expireBatch(100);

        assertEquals(1, expiredCount);
        then(reservationStorage).should().expire(skipped, NOW);
        then(reservationStorage).should().expire(expired, NOW);
    }

    @Test
    void continuesAfterCandidateFailure() {
        ReservationExpirationCandidate first = createCandidate("b1a64797-831c-42eb-8d6b-16a6050ede49");
        ReservationExpirationCandidate failed = createCandidate("6cfe755a-a85b-4ba3-80cd-d05451686746");
        ReservationExpirationCandidate last = createCandidate("a0ab8569-e64c-4ca6-9aae-d62751627781");

        given(reservationStorage.findExpiredReservations(NOW, 100)).willReturn(List.of(first, failed, last));
        given(reservationStorage.expire(first, NOW)).willReturn(true);
        given(reservationStorage.expire(failed, NOW))
                .willThrow(new InventoryConcurrentUpdateException(failed.productId()));
        given(reservationStorage.expire(last, NOW)).willReturn(true);

        int expiredCount = expirationService.expireBatch(100);

        assertEquals(2, expiredCount);
        then(reservationStorage).should().expire(first, NOW);
        then(reservationStorage).should().expire(failed, NOW);
        then(reservationStorage).should().expire(last, NOW);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveBatchSize(int batchSize) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> expirationService.expireBatch(batchSize)
        );

        assertEquals("Batch size must be positive", exception.getMessage());
        verifyNoInteractions(reservationStorage);
    }

    private ReservationExpirationCandidate createCandidate(String reservationId) {
        return new ReservationExpirationCandidate(
                UUID.fromString(reservationId),
                UUID.fromString("d479265e-8dec-4cfc-b5e8-ab0141e44b3a"),
                3,
                2
        );
    }
}
