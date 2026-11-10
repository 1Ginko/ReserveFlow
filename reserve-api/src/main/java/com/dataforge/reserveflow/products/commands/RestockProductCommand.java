package com.dataforge.reserveflow.products.commands;

import java.util.UUID;

public record RestockProductCommand(
        UUID productId,
        int quantity,
        long expectedVersion
) {}