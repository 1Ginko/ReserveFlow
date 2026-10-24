package com.dataforge.reserveflow.reservations.api.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateReservationRequest(
        @NotNull(message = "Product ID must be specified")
        UUID productId,

        @NotNull(message = "Quantity must be specified")
        @Positive(message = "Quantity must be positive")
        Integer quantity
) {
}