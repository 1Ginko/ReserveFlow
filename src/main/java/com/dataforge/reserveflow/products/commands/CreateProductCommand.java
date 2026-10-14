package com.dataforge.reserveflow.products.commands;

import java.math.BigDecimal;

public record CreateProductCommand(
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currencyCode,
        int initialQuantity
) {}