package com.ecommerce.api.specification;

import com.ecommerce.api.dto.request.ProductFilter;
import com.ecommerce.api.entity.Product;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> matching(ProductFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.isTrue(root.get("active")));

            if (filter.categoryId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("category").get("id"),
                        filter.categoryId()
                ));
            }

            if (filter.minPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("price"),
                        filter.minPrice()
                ));
            }

            if (filter.maxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("price"),
                        filter.maxPrice()
                ));
            }

            if (filter.minStock() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("stock"),
                        filter.minStock()
                ));
            }

            if (filter.maxStock() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("stock"),
                        filter.maxStock()
                ));
            }

            String search = filter.normalizedQuery();

            if (search != null) {
                String pattern = "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";

                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(lower(criteriaBuilder, root, "name"), pattern, '\\'),
                        criteriaBuilder.like(lower(criteriaBuilder, root, "description"), pattern, '\\'),
                        criteriaBuilder.like(lower(criteriaBuilder, root, "sku"), pattern, '\\')
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Expression<String> lower(CriteriaBuilder criteriaBuilder,
                                            Root<Product> root,
                                            String field) {
        return criteriaBuilder.lower(root.get(field).as(String.class));
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
