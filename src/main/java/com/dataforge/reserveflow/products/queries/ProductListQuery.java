package com.dataforge.reserveflow.products.queries;

public record ProductListQuery(
        int page,
        int size,
        Boolean active
) {}