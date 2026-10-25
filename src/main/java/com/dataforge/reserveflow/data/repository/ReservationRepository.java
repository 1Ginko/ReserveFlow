package com.dataforge.reserveflow.data.repository;

import com.dataforge.reserveflow.data.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                UPDATE reservations
                SET reservation_status = 'CANCELLED',
                    version = version + 1,
                    updated_at = :updatedAt
                WHERE reservation_id = :reservationId
                  AND reservation_status = 'ACTIVE'
                  AND version = :expectedVersion
                """,
            nativeQuery = true
    )
    int cancelActiveReservation(
            @Param("reservationId") UUID reservationId,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedAt") Instant updatedAt
    );
}
