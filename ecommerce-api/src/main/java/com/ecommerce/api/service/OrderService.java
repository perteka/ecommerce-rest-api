package com.ecommerce.api.service;

import com.ecommerce.api.dto.common.PageResponse;
import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.dto.response.OrderSummaryResponse;
import com.ecommerce.api.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse checkout(String email, CheckoutRequest request);

    PageResponse<OrderSummaryResponse> findOrders(String email, Pageable pageable);

    OrderResponse findById(String email, Long id);

    OrderResponse cancel(String email, Long id);

    PageResponse<OrderSummaryResponse> findOrdersForAdmin(OrderStatus status, Pageable pageable);

    OrderResponse findByIdForAdmin(Long id);

    OrderResponse updateStatus(Long id, OrderStatus status, String actorEmail);
}
