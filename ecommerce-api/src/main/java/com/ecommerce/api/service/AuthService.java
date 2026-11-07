package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RefreshTokenRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.dto.response.AuthResponse;
import com.ecommerce.api.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    UserResponse getCurrentUser(String email);
}
