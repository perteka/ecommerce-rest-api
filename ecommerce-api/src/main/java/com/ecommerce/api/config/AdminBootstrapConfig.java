package com.ecommerce.api.config;

import com.ecommerce.api.entity.Role;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.enums.RoleName;
import com.ecommerce.api.repository.RoleRepository;
import com.ecommerce.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class AdminBootstrapConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapConfig.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner adminBootstrapRunner(
            @Value("${app.bootstrap.admin-email:}") String email,
            @Value("${app.bootstrap.admin-password:}") String password) {
        return args -> {
            if (email.isBlank() || password.isBlank()) {
                return;
            }

            if (password.length() < 8) {
                throw new IllegalStateException("Bootstrap admin password must be at least 8 characters");
            }

            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            if (userRepository.existsByEmail(normalizedEmail)) {
                LOGGER.info("Admin bootstrap skipped: user {} already exists", normalizedEmail);
                return;
            }

            Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role is missing"));

            User user = new User();
            user.setEmail(normalizedEmail);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setFirstName("Admin");
            user.setLastName("User");
            user.addRole(adminRole);
            userRepository.save(user);

            LOGGER.info("Admin bootstrap user created: {}", normalizedEmail);
        };
    }
}
