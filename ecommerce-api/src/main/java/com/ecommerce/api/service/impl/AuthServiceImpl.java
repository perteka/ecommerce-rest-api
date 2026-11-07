package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RefreshTokenRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.dto.response.AuthResponse;
import com.ecommerce.api.dto.response.UserResponse;
import com.ecommerce.api.entity.Role;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.enums.RoleName;
import com.ecommerce.api.exception.DuplicateResourceException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.mapper.UserMapper;
import com.ecommerce.api.repository.RoleRepository;
import com.ecommerce.api.repository.UserRepository;
import com.ecommerce.api.security.CustomUserDetailsService;
import com.ecommerce.api.security.JwtService;
import com.ecommerce.api.service.AuthService;
import com.ecommerce.api.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CustomUserDetailsService userDetailsService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }

        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("Default role USER is missing"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone());
        user.addRole(userRole);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password())
        );

        UserDetails principal = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);
        String accessToken = jwtService.generateToken(principal);

        return new AuthResponse(
                accessToken,
                refreshToken.token(),
                "Bearer",
                jwtService.getExpirationSeconds(),
                secondsUntil(refreshToken.expiresAt())
        );
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(request.refreshToken());
        UserDetails principal = userDetailsService.loadUserByUsername(rotated.user().getEmail());
        String accessToken = jwtService.generateToken(principal);

        return new AuthResponse(
                accessToken,
                rotated.token(),
                "Bearer",
                jwtService.getExpirationSeconds(),
                secondsUntil(rotated.expiresAt())
        );
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return userMapper.toResponse(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private long secondsUntil(Instant expiresAt) {
        return Math.max(0, Duration.between(Instant.now(), expiresAt).toSeconds());
    }
}
