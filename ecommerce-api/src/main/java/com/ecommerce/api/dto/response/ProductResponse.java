package com.ecommerce.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String sku,
        boolean active,
        Long categoryId,
        String categoryName,
        Instant createdAt
) {
}
