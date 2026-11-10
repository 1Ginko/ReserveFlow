package com.dataforge.reserveflow.products.api.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(

        @NotBlank(message = "Product name must not be blank")
        @Size(max = 120, message = "Product name must not exceed 120 characters")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @NotNull(message = "Price must be specified")
        @DecimalMin(value = "0.00", message = "Price must not be negative")
        @Digits(
                integer = 17,
                fraction = 2,
                message = "Price must contain no more than 17 integer and 2 fractional digits"
        )
        BigDecimal price,

        @NotBlank(message = "Currency code must not be blank")
        @Pattern(
                regexp = "^[A-Za-z]{3}$",
                message = "Currency code must contain exactly 3 Latin letters"
        )
        String currencyCode,

        @NotNull(message = "Active flag must be specified")
        Boolean active,

        @NotNull(message = "Version must be specified")
        @PositiveOrZero(message = "Version must not be negative")
        Long version

) {}