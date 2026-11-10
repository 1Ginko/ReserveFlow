package com.dataforge.reserveflow.data.projection;

import java.util.UUID;

public interface ExpiredReservationProjection {
    UUID getReservationId();
    UUID getProductId();
    int getQuantity();
    long getVersion();
}