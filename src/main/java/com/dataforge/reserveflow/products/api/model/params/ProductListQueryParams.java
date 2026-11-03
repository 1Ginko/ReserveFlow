package com.dataforge.reserveflow.products.api.model.params;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ProductListQueryParams(
        @Min(value = 0, message = "Page must not be negative")
        Integer page,

        @Min(value = 1, message = "Size must be greater than zero")
        @Max(value = 100, message = "Size must not exceed 100")
        Integer size,

        Boolean active
) {
    public ProductListQueryParams {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}