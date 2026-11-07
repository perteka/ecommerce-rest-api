package com.ecommerce.api;

import com.ecommerce.api.dto.common.PageResponse;
import com.ecommerce.api.dto.request.ProductFilter;
import com.ecommerce.api.dto.response.ProductResponse;
import com.ecommerce.api.controller.ProductController;
import com.ecommerce.api.exception.InvalidProductQueryException;
import com.ecommerce.api.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class EcommerceApiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void productListBindsFiltersAndPaginationParameters() throws Exception {
        when(productService.findAll(any(ProductFilter.class), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 1, 2, 0, 0, false, true));

        mockMvc.perform(get("/api/products")
                        .param("q", " phone ")
                        .param("categoryId", "4")
                        .param("minPrice", "10.00")
                        .param("maxPrice", "50.00")
                        .param("minStock", "2")
                        .param("maxStock", "20")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2));

        var invocation = org.mockito.Mockito.mockingDetails(productService)
                .getInvocations().iterator().next();
        ProductFilter filter = (ProductFilter) invocation.getArgument(0);
        Pageable pageable = (Pageable) invocation.getArgument(1);

        assertEquals(" phone ", filter.q());
        assertEquals(4L, filter.categoryId());
        assertEquals(0, filter.minPrice().compareTo(new BigDecimal("10.00")));
        assertEquals(0, filter.maxPrice().compareTo(new BigDecimal("50.00")));
        assertEquals(2, filter.minStock());
        assertEquals(20, filter.maxStock());
        assertEquals(1, pageable.getPageNumber());
        assertEquals(2, pageable.getPageSize());
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("price").getDirection());
    }

    @Test
    void productListUsesConfiguredDefaultsAndCapsPageSize() throws Exception {
        when(productService.findAll(any(ProductFilter.class), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 100, 0, 0, true, true));

        mockMvc.perform(get("/api/products").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100));

        var invocation = org.mockito.Mockito.mockingDetails(productService)
                .getInvocations().iterator().next();
        Pageable pageable = (Pageable) invocation.getArgument(1);
        assertEquals(100, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void normalizesNegativePageToFirstPage() throws Exception {
        when(productService.findAll(any(ProductFilter.class), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/products").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));

        var invocation = org.mockito.Mockito.mockingDetails(productService)
                .getInvocations().iterator().next();
        assertEquals(0, ((Pageable) invocation.getArgument(1)).getPageNumber());
    }

    @Test
    void productListRejectsInvalidFilterRanges() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "100")
                        .param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priceRangeValid")
                        .value("minPrice must be less than or equal to maxPrice"));

        org.mockito.Mockito.verifyNoInteractions(productService);
    }

    @Test
    void mapsApplicationErrorsToTheSharedErrorResponse() throws Exception {
        when(productService.findAll(any(ProductFilter.class), any(Pageable.class)))
                .thenThrow(new InvalidProductQueryException("Unsupported sort property: description"));

        mockMvc.perform(get("/api/products").param("sort", "price,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Unsupported sort property: description"))
                .andExpect(jsonPath("$.path").value("/api/products"));
    }

    @Test
    void mapsInvalidQueryParameterTypesWithoutExposingFrameworkDetails() throws Exception {
        mockMvc.perform(get("/api/products").param("minPrice", "not-a-price"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minPrice").value("Value has an invalid format"));

        org.mockito.Mockito.verifyNoInteractions(productService);
    }

    @Test
    void mapsInvalidPathVariableTypesToFieldErrors() throws Exception {
        mockMvc.perform(get("/api/products/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("Value has an invalid format"));

        org.mockito.Mockito.verifyNoInteractions(productService);
    }

    @Test
    void mapsMalformedJsonAndUnknownRoutesToTheSharedErrorShape() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/products")
                        .contentType("application/json")
                        .content("{malformed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));

        mockMvc.perform(get("/api/unknown-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void hidesUnexpectedExceptionDetailsFromApiClients() throws Exception {
        when(productService.findAll(any(ProductFilter.class), any(Pageable.class)))
                .thenThrow(new IllegalStateException("sensitive internal detail"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not("sensitive internal detail")));
    }
}
