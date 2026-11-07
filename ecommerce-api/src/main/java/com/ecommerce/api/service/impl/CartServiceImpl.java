package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.AddCartItemRequest;
import com.ecommerce.api.dto.request.UpdateCartItemRequest;
import com.ecommerce.api.dto.response.CartResponse;
import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.mapper.CartMapper;
import com.ecommerce.api.repository.CartItemRepository;
import com.ecommerce.api.repository.CartRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.UserRepository;
import com.ecommerce.api.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    @Override
    @Transactional
    public CartResponse getCart(String email) {
        return cartMapper.toResponse(getOrCreateCart(email));
    }

    @Override
    @Transactional
    public CartResponse addItem(String email, AddCartItemRequest request) {
        Cart cart = getOrCreateCart(email);
        Product product = getActiveProduct(request.productId());

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        long requestedQuantity = (existingItem == null ? 0L : existingItem.getQuantity())
                + request.quantity();

        assertStock(product, requestedQuantity);

        if (existingItem == null) {
            CartItem item = new CartItem();
            item.setProduct(product);
            item.setQuantity((int) requestedQuantity);
            item.setUnitPrice(product.getPrice());
            cart.addItem(item);
        } else {
            existingItem.setQuantity((int) requestedQuantity);
            existingItem.setUnitPrice(product.getPrice());
        }

        cartRepository.saveAndFlush(cart);

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(String email, Long itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCart(email);
        CartItem item = getCartItem(cart, itemId);
        Product product = item.getProduct();

        if (!product.isActive()) {
            throw new ResourceNotFoundException("Product is not available: " + product.getId());
        }

        assertStock(product, request.quantity());
        item.setQuantity(request.quantity());
        item.setUnitPrice(product.getPrice());
        cartRepository.saveAndFlush(cart);

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(String email, Long itemId) {
        Cart cart = getOrCreateCart(email);
        CartItem item = getCartItem(cart, itemId);
        cart.removeItem(item);
        cartRepository.saveAndFlush(cart);

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse clear(String email) {
        Cart cart = getOrCreateCart(email);
        cart.clearItems();
        cartRepository.saveAndFlush(cart);

        return cartMapper.toResponse(cart);
    }

    private Cart getOrCreateCart(String email) {
        User user = getUser(email);

        Cart cart = cartRepository.findByUserEmail(user.getEmail())
                .orElseGet(() -> {
                    Cart cartToCreate = new Cart();
                    cartToCreate.setUser(user);
                    return cartRepository.save(cartToCreate);
                });

        syncPrices(cart);
        return cart;
    }

    private void syncPrices(Cart cart) {
        for (CartItem item : cart.getItems()) {
            BigDecimal currentPrice = item.getProduct().getPrice();
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(currentPrice) != 0) {
                item.setUnitPrice(currentPrice);
            }
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Product getActiveProduct(Long productId) {
        return productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private CartItem getCartItem(Cart cart, Long itemId) {
        return cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
    }

    private void assertStock(Product product, long requestedQuantity) {
        if (requestedQuantity > product.getStock()) {
            throw new InsufficientStockException(
                    "Insufficient stock for '%s': requested %d, available %d"
                            .formatted(product.getName(), requestedQuantity, product.getStock())
            );
        }
    }
}
