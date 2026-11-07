package com.ecommerce.api.dto.response;

import com.ecommerce.api.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt
) {
}
