package com.ecommerce.api.service;

import com.ecommerce.api.entity.User;

import java.time.Instant;

public interface RefreshTokenService {

    IssuedRefreshToken issue(User user);

    IssuedRefreshToken rotate(String rawToken);

    void revoke(String rawToken);

    record IssuedRefreshToken(String token, User user, Instant expiresAt) {
    }
}
