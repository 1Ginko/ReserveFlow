package com.dataforge.reserveflow.products.api.model;

import jakarta.validation.constraints.Pattern;

public record ProductQueryParams(
        @Pattern(
                regexp = "inventory",
                message = "Include must be 'inventory'"
        )
        String include
) {}