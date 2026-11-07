package com.ecommerce.api.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductFilter(
        @Size(max = 100) String q,
        @Positive Long categoryId,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal minPrice,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal maxPrice,
        @PositiveOrZero Integer minStock,
        @PositiveOrZero Integer maxStock
) {

    @AssertTrue(message = "minPrice must be less than or equal to maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    @AssertTrue(message = "minStock must be less than or equal to maxStock")
    public boolean isStockRangeValid() {
        return minStock == null || maxStock == null || minStock <= maxStock;
    }

    public String normalizedQuery() {
        return q == null || q.isBlank() ? null : q.trim();
    }
}
