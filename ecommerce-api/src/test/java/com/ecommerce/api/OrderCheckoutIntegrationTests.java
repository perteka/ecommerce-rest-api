package com.ecommerce.api;

import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.Role;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.enums.OrderStatus;
import com.ecommerce.api.enums.RoleName;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.exception.OrderStatusTransitionException;
import com.ecommerce.api.repository.CartRepository;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.RoleRepository;
import com.ecommerce.api.repository.UserRepository;
import com.ecommerce.api.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
class OrderCheckoutIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Test
    void successfulCheckoutCreatesOrderDeductsStockAndEmptiesCart() {
        Fixture fixture = createFixture(5, 2);
        try {
            OrderResponse response = orderService.checkout(
                    fixture.user().getEmail(), new CheckoutRequest("  1 Main Street  "));

            assertEquals(new BigDecimal("20.00"), response.totalAmount());
            assertEquals("1 Main Street", response.shippingAddress());
            assertEquals(1, response.items().size());
            assertEquals(3, productRepository.findById(fixture.product().getId()).orElseThrow().getStock());
            assertTrue(cartRepository.findByUserEmail(fixture.user().getEmail()).orElseThrow().getItems().isEmpty());
            assertEquals(1, orderRepository.findByUserId(
                    fixture.user().getId(), PageRequest.of(0, 10)).getTotalElements());
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void insufficientStockRollsBackEarlierStockChangesAndPreservesCart() {
        Fixture fixture = createFixture(5, 2);
        Product secondProduct = createProduct(fixture.category(), "Second", "SECOND", 1);
        Cart cart = cartRepository.findByUserEmail(fixture.user().getEmail()).orElseThrow();
        addCartItem(cart, secondProduct, 2);
        cartRepository.saveAndFlush(cart);

        try {
            assertThrows(InsufficientStockException.class, () -> orderService.checkout(
                    fixture.user().getEmail(), new CheckoutRequest("1 Main Street")));

            assertEquals(5, productRepository.findById(fixture.product().getId()).orElseThrow().getStock());
            assertEquals(1, productRepository.findById(secondProduct.getId()).orElseThrow().getStock());
            assertEquals(2, cartRepository.findByUserEmail(fixture.user().getEmail()).orElseThrow()
                    .getItems().size());
            assertEquals(0, orderRepository.findByUserId(
                    fixture.user().getId(), PageRequest.of(0, 10)).getTotalElements());
        } finally {
            deleteFixture(fixture, secondProduct);
        }
    }

    @Test
    void customerCancelRestoresStockAndBlocksFurtherCancellations() {
        Fixture fixture = createFixture(5, 2);
        try {
            OrderResponse order = orderService.checkout(
                    fixture.user().getEmail(), new CheckoutRequest("1 Main Street"));

            assertEquals(OrderStatus.PENDING, order.status());
            assertEquals(3, productRepository.findById(fixture.product().getId()).orElseThrow().getStock());

            OrderResponse cancelled = orderService.cancel(fixture.user().getEmail(), order.id());

            assertEquals(OrderStatus.CANCELLED, cancelled.status());
            assertEquals(5, productRepository.findById(fixture.product().getId()).orElseThrow().getStock());
            assertTrue(cartRepository.findByUserEmail(fixture.user().getEmail()).orElseThrow().getItems().isEmpty());

            assertThrows(OrderStatusTransitionException.class, () -> orderService.cancel(
                    fixture.user().getEmail(), order.id()));
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void adminCanAdvanceOrderStatusThenRejectCancellationFromDelivered() {
        Fixture fixture = createFixture(5, 2);
        try {
            OrderResponse order = orderService.checkout(
                    fixture.user().getEmail(), new CheckoutRequest("1 Main Street"));

            assertEquals(OrderStatus.CONFIRMED, orderService.updateStatus(
                    order.id(), OrderStatus.CONFIRMED, "admin@example.com").status());
            assertEquals(OrderStatus.SHIPPED, orderService.updateStatus(
                    order.id(), OrderStatus.SHIPPED, "admin@example.com").status());
            assertEquals(OrderStatus.DELIVERED, orderService.updateStatus(
                    order.id(), OrderStatus.DELIVERED, "admin@example.com").status());

            assertThrows(OrderStatusTransitionException.class, () -> orderService.updateStatus(
                    order.id(), OrderStatus.CANCELLED, "admin@example.com"));
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void deliveredOrdersCannotTransitionToOtherStatuses() {
        Fixture fixture = createFixture(5, 2);
        try {
            OrderResponse order = orderService.checkout(
                    fixture.user().getEmail(), new CheckoutRequest("1 Main Street"));

            orderService.updateStatus(order.id(), OrderStatus.CONFIRMED, "admin@example.com");
            orderService.updateStatus(order.id(), OrderStatus.SHIPPED, "admin@example.com");
            orderService.updateStatus(order.id(), OrderStatus.DELIVERED, "admin@example.com");

            assertThrows(OrderStatusTransitionException.class, () -> orderService.updateStatus(
                    order.id(), OrderStatus.CANCELLED, "admin@example.com"));
        } finally {
            deleteFixture(fixture);
        }
    }

    private Fixture createFixture(int stock, int quantity) {
        String id = UUID.randomUUID().toString().replace("-", "");

        Category category = new Category();
        category.setName("Checkout " + id);
        category = categoryRepository.saveAndFlush(category);

        Product product = createProduct(category, "Checkout Product", "CHECKOUT-" + id, stock);

        Role userRole = roleRepository.findByName(RoleName.USER).orElseThrow();
        User user = new User();
        user.setEmail("checkout-" + id + "@example.com");
        user.setPasswordHash("not-a-real-password-hash");
        user.setFirstName("Checkout");
        user.setLastName("Test");
        user.getRoles().add(userRole);
        user = userRepository.saveAndFlush(user);

        Cart cart = new Cart();
        cart.setUser(user);
        addCartItem(cart, product, quantity);
        cartRepository.saveAndFlush(cart);

        return new Fixture(category, product, user, cart);
    }

    private Product createProduct(Category category, String name, String sku, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(stock);
        product.setCategory(category);
        product.setActive(true);
        return productRepository.saveAndFlush(product);
    }

    private void addCartItem(Cart cart, Product product, int quantity) {
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        cart.addItem(item);
    }

    private void deleteFixture(Fixture fixture, Product... additionalProducts) {
        orderRepository.findByUserId(fixture.user().getId(), PageRequest.of(0, 100))
                .forEach(order -> orderRepository.deleteById(order.getId()));
        cartRepository.deleteById(fixture.cart().getId());
        for (Product product : additionalProducts) {
            productRepository.deleteById(product.getId());
        }
        productRepository.deleteById(fixture.product().getId());
        categoryRepository.deleteById(fixture.category().getId());
        userRepository.deleteById(fixture.user().getId());
    }

    private record Fixture(Category category, Product product, User user, Cart cart) {
    }
}
