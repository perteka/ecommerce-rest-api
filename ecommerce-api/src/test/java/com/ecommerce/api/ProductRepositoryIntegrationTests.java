package com.ecommerce.api;

import com.ecommerce.api.dto.common.PageResponse;
import com.ecommerce.api.dto.request.ProductFilter;
import com.ecommerce.api.dto.response.ProductResponse;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.RoleRepository;
import com.ecommerce.api.service.ProductService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductRepositoryIntegrationTests extends PostgresIntegrationTestBase {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private Flyway flyway;

    @Autowired
    private MockMvc mockMvc;

    private Category electronics;
    private Category clothing;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        electronics = categoryRepository.save(category("Electronics"));
        clothing = categoryRepository.save(category("Clothing"));

        productRepository.saveAll(List.of(
                product("Phone Case", "Protective phone case", "CASE-1", "25.50", 50, electronics, true),
                product("Phone X", "Smartphone", "PHONE-X", "1000.00", 5, electronics, true),
                product("Accessory", "Phone stand", "ACC-1", "15.00", 20, electronics, true),
                product("100% Cotton Shirt", "Cotton shirt", "SHIRT-1", "30.00", 0, clothing, true),
                product("Hidden Phone", "Inactive product", "HIDDEN-1", "12.00", 99, electronics, false),
                product("50_percent item", "Underscore sample", "SPECIAL-1", "18.00", 4, clothing, true)
        ));
    }

    @Test
    void combinesSearchCategoryPriceAndStockFiltersAgainstPostgres() {
        ProductFilter filter = new ProductFilter(
                "phone", electronics.getId(), new BigDecimal("10.00"), new BigDecimal("100.00"),
                1, 50
        );

        PageResponse<ProductResponse> result = productService.findAll(filter, PageRequest.of(0, 20));

        assertEquals(2, result.totalElements());
        assertEquals(List.of("ACC-1", "CASE-1"), result.content().stream()
                .map(ProductResponse::sku)
                .sorted()
                .toList());
        assertTrue(result.content().stream().allMatch(item -> "Electronics".equals(item.categoryName())));
    }

    @Test
    void treatsLikeWildcardsAsLiteralSearchCharacters() {
        PageResponse<ProductResponse> percentMatches = productService.findAll(
                new ProductFilter("%", null, null, null, null, null), PageRequest.of(0, 20));
        PageResponse<ProductResponse> underscoreMatches = productService.findAll(
                new ProductFilter("_", null, null, null, null, null), PageRequest.of(0, 20));

        assertEquals(List.of("SHIRT-1"), percentMatches.content().stream().map(ProductResponse::sku).toList());
        assertEquals(List.of("SPECIAL-1"), underscoreMatches.content().stream().map(ProductResponse::sku).toList());
    }

    @Test
    void appliesPaginationOrderingAndExcludesInactiveProducts() {
        PageResponse<ProductResponse> firstPage = productService.findAll(
                new ProductFilter(null, null, null, null, null, null),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "price")));
        PageResponse<ProductResponse> secondPage = productService.findAll(
                new ProductFilter(null, null, null, null, null, null),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "price")));

        assertEquals(5, firstPage.totalElements());
        assertEquals(3, firstPage.totalPages());
        assertTrue(firstPage.first());
        assertFalse(firstPage.last());
        assertEquals("ACC-1", firstPage.content().get(0).sku());
        assertEquals("SPECIAL-1", firstPage.content().get(1).sku());
        assertEquals("CASE-1", secondPage.content().get(0).sku());
        assertFalse(secondPage.content().stream().anyMatch(item -> "HIDDEN-1".equals(item.sku())));
    }

    @Test
    void exposesOpenApiDocumentWithJwtBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("E-Commerce REST API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/cart'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get.security").doesNotExist());
    }

    @Test
    void appliesMigrationsAndSeedsRequiredRoles() {
        assertTrue(Integer.parseInt(flyway.info().current().getVersion().getVersion()) >= 6);
        assertEquals(2, roleRepository.count());
    }

    @Test
    void rejectsUnauthenticatedRequestsWithStandardErrorBody() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/cart"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void rejectsNonAdminProductWritesWithStandardErrorBody() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/products"));
    }

    private Category category(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private Product product(String name, String description, String sku, String price, int stock,
                            Category category, boolean active) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setSku(sku);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setCategory(category);
        product.setActive(active);
        return product;
    }
}
