package com.dataforge.reserveflow.reservations.commands;

import java.util.UUID;

public record CreateReservationCommand(
        UUID productId,
        UUID idempotencyKey,
        int quantity
) { }