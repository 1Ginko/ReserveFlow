package com.dataforge.reserveflow.products.commands;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateProductCommand(
        UUID productId,
        String name,
        String description,
        BigDecimal price,
        String currencyCode,
        boolean active,
        long expectedVersion
) {}