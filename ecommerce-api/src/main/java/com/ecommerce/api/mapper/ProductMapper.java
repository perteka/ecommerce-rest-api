package com.ecommerce.api.mapper;

import com.ecommerce.api.dto.response.ProductResponse;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        Category category = product.getCategory();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getSku(),
                product.isActive(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                product.getCreatedAt()
        );
    }
}
