package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.common.PageResponse;
import com.ecommerce.api.dto.request.CreateProductRequest;
import com.ecommerce.api.dto.request.ProductFilter;
import com.ecommerce.api.dto.request.UpdateProductRequest;
import com.ecommerce.api.dto.response.ProductResponse;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.exception.DuplicateResourceException;
import com.ecommerce.api.exception.InvalidProductQueryException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.mapper.ProductMapper;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.service.ProductService;
import com.ecommerce.api.specification.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "name", "price", "stock", "createdAt", "updatedAt"
    );

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findAll(ProductFilter filter, Pageable pageable) {
        validateQuery(filter, pageable);

        Page<ProductResponse> page = productRepository.findAll(ProductSpecifications.matching(filter), pageable)
                .map(productMapper::toResponse);

        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return productMapper.toResponse(getActiveProduct(id));
    }

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = normalizeSku(request.sku());

        if (productRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("SKU is already in use");
        }

        Product product = new Product();
        product.setName(request.name().trim());
        product.setDescription(trimToNull(request.description()));
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setSku(sku);
        product.setActive(request.active() == null || request.active());
        product.setCategory(getCategory(request.categoryId()));

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = getProduct(id);

        product.setName(request.name().trim());
        product.setDescription(trimToNull(request.description()));
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setActive(request.active());
        product.setCategory(getCategory(request.categoryId()));

        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = getProduct(id);
        product.setActive(false);
    }

    private void validateQuery(ProductFilter filter, Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidProductQueryException(
                        "Unsupported sort property: " + order.getProperty()
                );
            }
        }

        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new InvalidProductQueryException("minPrice must be less than or equal to maxPrice");
        }

        if (filter.minStock() != null && filter.maxStock() != null
                && filter.minStock() > filter.maxStock()) {
            throw new InvalidProductQueryException("minStock must be less than or equal to maxStock");
        }
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private Product getActiveProduct(Long id) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
