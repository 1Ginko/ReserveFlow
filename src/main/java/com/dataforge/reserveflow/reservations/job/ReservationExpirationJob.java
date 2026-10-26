package com.dataforge.reserveflow.reservations.job;

import com.dataforge.reserveflow.reservations.ReservationExpirationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.reservation-expiration.enabled",
        havingValue = "true"
)
public class ReservationExpirationJob {

    private final ReservationExpirationService expirationService;
    private final int batchSize;

    public ReservationExpirationJob(
            ReservationExpirationService expirationService,
            @Value("${app.reservation-expiration.batch-size}") int batchSize
    ) {
        this.expirationService = expirationService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.reservation-expiration.fixed-delay-ms}")
    public void expireReservations() {
        expirationService.expireBatch(batchSize);
    }
}