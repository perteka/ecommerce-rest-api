package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.AddCartItemRequest;
import com.ecommerce.api.dto.request.UpdateCartItemRequest;
import com.ecommerce.api.dto.response.CartResponse;

public interface CartService {

    CartResponse getCart(String email);

    CartResponse addItem(String email, AddCartItemRequest request);

    CartResponse updateItem(String email, Long itemId, UpdateCartItemRequest request);

    CartResponse removeItem(String email, Long itemId);

    CartResponse clear(String email);
}
