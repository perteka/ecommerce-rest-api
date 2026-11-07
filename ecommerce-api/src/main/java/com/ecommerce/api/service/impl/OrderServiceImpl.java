package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.common.PageResponse;
import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.dto.response.OrderSummaryResponse;
import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.OrderItem;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.enums.OrderStatus;
import com.ecommerce.api.exception.EmptyCartException;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.exception.InvalidOrderQueryException;
import com.ecommerce.api.exception.OrderStatusTransitionException;
import com.ecommerce.api.exception.PriceChangedException;
import com.ecommerce.api.exception.ProductUnavailableException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.mapper.OrderMapper;
import com.ecommerce.api.repository.CartRepository;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.UserRepository;
import com.ecommerce.api.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "orderNumber", "status", "totalAmount", "createdAt", "updatedAt"
    );

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public OrderResponse checkout(String email, CheckoutRequest request) {
        User user = getUser(email);

        // Lock the cart row so two concurrent checkout requests cannot create two orders.
        cartRepository.findIdByUserEmailForUpdate(user.getEmail())
                .orElseThrow(() -> new EmptyCartException("Cart is empty"));

        Cart cart = cartRepository.findByUserEmail(user.getEmail())
                .orElseThrow(() -> new EmptyCartException("Cart is empty"));

        List<CartItem> items = cart.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();

        if (items.isEmpty()) {
            throw new EmptyCartException("Cart is empty");
        }

        Map<Long, Product> lockedProducts = lockAndValidate(items);

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(request.shippingAddress().trim());

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem item : items) {
            Product product = lockedProducts.get(item.getProduct().getId());
            BigDecimal unitPrice = product.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

            product.setStock(product.getStock() - item.getQuantity());

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(item.getQuantity());
            orderItem.setLineTotal(lineTotal);
            order.addItem(orderItem);

            totalAmount = totalAmount.add(lineTotal);
        }

        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.saveAndFlush(order);
        cart.clearItems();

        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> findOrders(String email, Pageable pageable) {
        validateSort(pageable);

        User user = getUser(email);

        Page<OrderSummaryResponse> page = orderRepository.findByUserId(user.getId(), pageable)
                .map(orderMapper::toSummary);

        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(String email, Long id) {
        User user = getUser(email);

        Order order = orderRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancel(String email, Long id) {
        User user = getUser(email);

        Order order = orderRepository.findByIdAndUserIdForUpdate(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new OrderStatusTransitionException(
                    "Order can only be cancelled while it is pending or confirmed"
            );
        }

        OrderStatus previous = order.getStatus();
        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);

        LOGGER.info("Order {} cancelled by customer {} (previous status {})",
                order.getOrderNumber(), email, previous);

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> findOrdersForAdmin(OrderStatus status, Pageable pageable) {
        validateSort(pageable);

        Page<Order> page = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);

        return PageResponse.from(page.map(orderMapper::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findByIdForAdmin(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus status, String actorEmail) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        OrderStatus previous = order.getStatus();

        if (previous == status) {
            return orderMapper.toResponse(order);
        }

        if (!ALLOWED_TRANSITIONS.getOrDefault(previous, Set.of()).contains(status)) {
            throw new OrderStatusTransitionException(
                    "Order status cannot change from %s to %s".formatted(previous, status)
            );
        }

        if (status == OrderStatus.CANCELLED) {
            restoreStock(order);
        }

        order.setStatus(status);

        LOGGER.info("Order {} status changed from {} to {} by admin {}",
                order.getOrderNumber(), previous, status, actorEmail);

        return orderMapper.toResponse(order);
    }

    private void validateSort(Pageable pageable) {
        for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidOrderQueryException("Unsupported sort property: " + order.getProperty());
            }
        }
    }

    private void restoreStock(Order order) {
        order.getItems().stream()
                .filter(item -> item.getProduct() != null)
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .forEach(item -> productRepository.findByIdForUpdate(item.getProduct().getId())
                        .ifPresent(product -> product.setStock(product.getStock() + item.getQuantity())));
    }

    private Map<Long, Product> lockAndValidate(List<CartItem> items) {
        Map<Long, Product> lockedProducts = new HashMap<>();

        for (CartItem item : items) {
            Long productId = item.getProduct().getId();

            Product product = lockedProducts.computeIfAbsent(productId, id ->
                    productRepository.findByIdForUpdate(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id))
            );

            if (!product.isActive()) {
                throw new ProductUnavailableException("Product is not available: " + product.getName());
            }

            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(product.getPrice()) != 0) {
                throw new PriceChangedException(
                        "Price changed for '%s': cart price %s, current price %s. Refresh the cart and try again."
                                .formatted(product.getName(), item.getUnitPrice(), product.getPrice())
                );
            }

            if (product.getStock() < item.getQuantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for '%s': requested %d, available %d"
                                .formatted(product.getName(), item.getQuantity(), product.getStock())
                );
            }
        }

        return lockedProducts;
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String generateOrderNumber() {
        String orderNumber;

        do {
            orderNumber = "ORD-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12)
                    .toUpperCase(Locale.ROOT);
        } while (orderRepository.existsByOrderNumber(orderNumber));

        return orderNumber;
    }
}
