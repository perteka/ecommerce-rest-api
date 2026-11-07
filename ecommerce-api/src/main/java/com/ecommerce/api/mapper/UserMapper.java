package com.ecommerce.api.mapper;

import com.ecommerce.api.dto.response.UserResponse;
import com.ecommerce.api.entity.Role;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.enums.RoleName;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        Set<RoleName> roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                roles,
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
