package com.ecommerce.api.service;

import com.ecommerce.api.dto.common.PageResponse;
import com.ecommerce.api.dto.request.CreateProductRequest;
import com.ecommerce.api.dto.request.ProductFilter;
import com.ecommerce.api.dto.request.UpdateProductRequest;
import com.ecommerce.api.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    PageResponse<ProductResponse> findAll(ProductFilter filter, Pageable pageable);

    ProductResponse findById(Long id);

    ProductResponse create(CreateProductRequest request);

    ProductResponse update(Long id, UpdateProductRequest request);

    void delete(Long id);
}
