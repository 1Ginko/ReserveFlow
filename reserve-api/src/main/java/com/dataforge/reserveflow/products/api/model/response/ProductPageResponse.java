package com.dataforge.reserveflow.products.api.model.response;

import java.util.List;

public record ProductPageResponse(
        List<ProductResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages,
        String nextPage
) {}