package com.dataforge.reserveflow.reservations.storage;

import com.dataforge.reserveflow.reservations.model.Reservation;

public interface ReservationStorage {

    void create(Reservation reservation);
}