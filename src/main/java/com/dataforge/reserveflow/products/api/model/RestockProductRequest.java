package com.dataforge.reserveflow.products.api.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record RestockProductRequest(
        @NotNull(message = "Quantity must be specified")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity,

        @NotNull(message = "Version must be specified")
        @PositiveOrZero(message = "Version must not be negative")
        Long version
) {}