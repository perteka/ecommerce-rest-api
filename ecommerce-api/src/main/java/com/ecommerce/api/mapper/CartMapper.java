package com.ecommerce.api.mapper;

import com.ecommerce.api.dto.response.CartItemResponse;
import com.ecommerce.api.dto.response.CartResponse;
import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Product;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CartMapper {

    public CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        int totalItems = cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        return new CartResponse(
                cart.getId(),
                items,
                totalItems,
                cart.getTotalPrice()
        );
    }

    private CartItemResponse toItemResponse(CartItem item) {
        Product product = item.getProduct();

        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }
}
