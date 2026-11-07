package com.ecommerce.api.dto.response;

import com.ecommerce.api.enums.RoleName;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Set<RoleName> roles,
        boolean enabled,
        Instant createdAt
) {
}
