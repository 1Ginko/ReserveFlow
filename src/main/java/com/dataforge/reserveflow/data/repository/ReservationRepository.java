package com.dataforge.reserveflow.data.repository;

import com.dataforge.reserveflow.data.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> { }