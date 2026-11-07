package com.ecommerce.api.mapper;

import com.ecommerce.api.dto.response.CategoryResponse;
import com.ecommerce.api.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }
}
