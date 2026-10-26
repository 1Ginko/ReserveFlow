package com.dataforge.reserveflow.data.repository;

import com.dataforge.reserveflow.data.entity.ReservationEntity;
import com.dataforge.reserveflow.data.projection.ExpiredReservationProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    UPDATE reservations
                    SET reservation_status = 'CONFIRMED',
                        version = version + 1,
                        updated_at = :confirmedAt
                    WHERE reservation_id = :reservationId
                      AND reservation_status = 'ACTIVE'
                      AND version = :expectedVersion
                      AND expires_at > :confirmedAt
                    """,
            nativeQuery = true
    )
    int confirmActiveReservation(
            @Param("reservationId") UUID reservationId,
            @Param("expectedVersion") long expectedVersion,
            @Param("confirmedAt") Instant confirmedAt
    );

    @Query(
            value = """
                    SELECT reservation_id AS "reservationId",
                           product_id AS "productId",
                           quantity AS "quantity",
                           version AS "version"
                    FROM reservations
                    WHERE reservation_status = 'ACTIVE'
                      AND expires_at <= :now
                    ORDER BY expires_at, reservation_id
                    LIMIT :batchSize
                    """,
            nativeQuery = true
    )
    List<ExpiredReservationProjection> findExpiredReservations(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    UPDATE reservations
                    SET reservation_status = 'EXPIRED',
                        version = version + 1,
                        updated_at = :expiredAt
                    WHERE reservation_id = :reservationId
                      AND reservation_status = 'ACTIVE'
                      AND expires_at <= :expiredAt
                      AND version = :expectedVersion
                    """,
            nativeQuery = true
    )
    int expireActiveReservation(
            @Param("reservationId") UUID reservationId,
            @Param("expectedVersion") long expectedVersion,
            @Param("expiredAt") Instant expiredAt
    );
}
