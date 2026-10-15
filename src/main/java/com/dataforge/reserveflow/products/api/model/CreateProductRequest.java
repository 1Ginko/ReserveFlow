package com.dataforge.reserveflow.products.api.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(

        @NotBlank(message = "SKU must not be blank")
        @Size(max = 64, message = "SKU must not exceed 64 characters")
        String sku,

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

        @NotNull(message = "Initial quantity must be specified")
        @PositiveOrZero(message = "Initial quantity must not be negative")
        Integer initialQuantity

) {}