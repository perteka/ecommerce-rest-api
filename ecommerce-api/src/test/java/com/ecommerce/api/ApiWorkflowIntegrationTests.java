package com.ecommerce.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.Role;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.enums.RoleName;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.RoleRepository;
import com.ecommerce.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiWorkflowIntegrationTests extends PostgresIntegrationTestBase {

    private static final String TEST_PASSWORD = "TestPassword-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersLogsInReadsProfileRotatesAndRevokesRefreshTokenFamily() throws Exception {
        String email = uniqueEmail();
        String registration = """
                {"email":"%s","password":"%s","firstName":"Ada","lastName":"Lovelace"}
                """.formatted(email, TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("USER"));

        User registeredUser = userRepository.findByEmail(email).orElseThrow();
        assertTrue(passwordEncoder.matches(TEST_PASSWORD, registeredUser.getPasswordHash()));
        assertEquals(1, registeredUser.getRoles().size());
        assertEquals(email, registeredUser.getEmail());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isConflict());

        MvcResult caseVariantResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration.replace(email, email.toUpperCase(Locale.ROOT))))
                .andReturn();
        assertEquals(409, caseVariantResult.getResponse().getStatus(),
                caseVariantResult.getResponse().getContentAsString());

        String badLogin = """
                {"email":"%s","password":"wrong-password"}
                """.formatted(email);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLogin))
                .andExpect(status().isUnauthorized());

        Tokens initialTokens = login(email, TEST_PASSWORD);
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(initialTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("USER"));

        Tokens rotatedTokens = refresh(initialTokens.refreshToken());
        assertNotEquals(initialTokens.refreshToken(), rotatedTokens.refreshToken());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(initialTokens.refreshToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(rotatedTokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanManageProductsAndCategoriesWhileCategoryInUseCannotBeDeleted() throws Exception {
        Tokens admin = createAndLoginAdmin();

        MvcResult categoryResult = mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"API category","description":"Created through HTTP"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("API category"))
                .andReturn();
        long categoryId = json(categoryResult).path("id").asLong();

        MvcResult productResult = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"API Product","description":"Initial","price":12.50,
                                 "stock":8,"sku":"api-product-%s","categoryId":%d,"active":true}
                                """.formatted(uniqueSuffix(), categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryName").value("API category"))
                .andReturn();
        long productId = json(productResult).path("id").asLong();

        mockMvc.perform(put("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Product","description":"Updated","price":15.00,
                                 "stock":6,"categoryId":%d,"active":true}
                                """.formatted(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Product"))
                .andExpect(jsonPath("$.price").value(15.00));

        mockMvc.perform(delete("/api/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isNotFound());

        long unusedCategoryId = categoryRepository.saveAndFlush(category("Unused " + uniqueSuffix())).getId();
        mockMvc.perform(put("/api/categories/{id}", unusedCategoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated category","description":"Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated category"));

        mockMvc.perform(delete("/api/categories/{id}", unusedCategoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void cartIsPrivateAndCheckoutCreatesOnlyAnOwnersOrder() throws Exception {
        String suffix = uniqueSuffix();
        Category category = categoryRepository.saveAndFlush(category("Cart " + suffix));
        Product product = productRepository.saveAndFlush(product(category, "Cart product", "CART-" + suffix, 6));
        String ownerEmail = uniqueEmail();
        Tokens owner = registerAndLogin(ownerEmail);
        Tokens otherUser = registerAndLogin(uniqueEmail());

        mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":2}
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPrice").value(20.00));

        MvcResult ownerCartResult = mockMvc.perform(get("/api/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();
        long itemId = json(ownerCartResult).path("items").get(0).path("id").asLong();

        mockMvc.perform(get("/api/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherUser.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(delete("/api/cart/items/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherUser.accessToken())))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/cart/items/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPrice").value(30.00));
        mockMvc.perform(put("/api/cart/items/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":2}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/cart/items/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":2}
                                """.formatted(product.getId())))
                .andExpect(status().isOk());

        MvcResult orderResult = mockMvc.perform(post("/api/orders/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shippingAddress":"1 Test Street"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(20.00))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();
        long orderId = json(orderResult).path("id").asLong();

        assertEquals(4, productRepository.findById(product.getId()).orElseThrow().getStock());
        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherUser.accessToken())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":1}
                                """.formatted(product.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(post("/api/orders/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shippingAddress":"1 Test Street"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutReturnsConflictAndKeepsCartWhenStockChangesAfterCartAddition() throws Exception {
        String suffix = uniqueSuffix();
        Category category = categoryRepository.saveAndFlush(category("Stock " + suffix));
        Product product = productRepository.saveAndFlush(product(category, "Stock product", "STOCK-" + suffix, 5));
        String ownerEmail = uniqueEmail();
        Tokens owner = registerAndLogin(ownerEmail);

        mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":3}
                                """.formatted(product.getId())))
                .andExpect(status().isOk());

        product.setStock(1);
        productRepository.saveAndFlush(product);

        mockMvc.perform(post("/api/orders/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shippingAddress":"1 Test Street"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertEquals(1, productRepository.findById(product.getId()).orElseThrow().getStock());
        mockMvc.perform(get("/api/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
        long ownerId = userRepository.findByEmail(ownerEmail).orElseThrow().getId();
        assertEquals(0, orderRepository.findByUserId(ownerId,
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void healthEndpointIsPublicAndReportsServiceUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void adminOrderLifecycleUsesAdminOnlyEndpointsAndValidTransitions() throws Exception {
        String suffix = uniqueSuffix();
        Category category = categoryRepository.saveAndFlush(category("Admin order " + suffix));
        Product product = productRepository.saveAndFlush(product(category, "Admin order product", "ADMIN-ORDER-" + suffix, 4));
        Tokens customer = registerAndLogin(uniqueEmail());

        mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":1}
                                """.formatted(product.getId())))
                .andExpect(status().isOk());

        MvcResult orderResult = mockMvc.perform(post("/api/orders/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shippingAddress":"1 Test Street"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = json(orderResult).path("id").asLong();

        mockMvc.perform(get("/api/admin/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer.accessToken())))
                .andExpect(status().isForbidden());

        Tokens admin = createAndLoginAdmin();

        mockMvc.perform(get("/api/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/admin/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DELIVERED"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(4, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    private Tokens registerAndLogin(String email) throws Exception {
        String request = """
                {"email":"%s","password":"%s","firstName":"Test","lastName":"User"}
                """.formatted(email, TEST_PASSWORD);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());
        return login(email, TEST_PASSWORD);
    }

    private Tokens createAndLoginAdmin() throws Exception {
        String email = uniqueEmail();
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setFirstName("Admin");
        user.setLastName("User");
        user.getRoles().add(adminRole);
        userRepository.saveAndFlush(user);
        return login(email, TEST_PASSWORD);
    }

    private Tokens login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = json(result);
        return new Tokens(response.path("accessToken").asText(), response.path("refreshToken").asText());
    }

    private Tokens refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = json(result);
        return new Tokens(response.path("accessToken").asText(), response.path("refreshToken").asText());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String refreshRequest(String refreshToken) {
        return """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private Category category(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private Product product(Category category, String name, String sku, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(stock);
        product.setCategory(category);
        product.setActive(true);
        return product;
    }

    private String uniqueEmail() {
        return ("api-" + uniqueSuffix() + "@example.com").toLowerCase(Locale.ROOT);
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
