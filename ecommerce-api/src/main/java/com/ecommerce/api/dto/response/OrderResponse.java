package com.ecommerce.api.dto.response;

import com.ecommerce.api.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        String shippingAddress,
        Instant createdAt,
        List<OrderItemResponse> items
) {
}
