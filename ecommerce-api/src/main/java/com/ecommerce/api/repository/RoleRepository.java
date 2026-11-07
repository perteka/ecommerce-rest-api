package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Role;
import com.ecommerce.api.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
