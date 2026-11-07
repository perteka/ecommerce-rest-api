package com.ecommerce.api.service.impl;

import com.ecommerce.api.entity.RefreshToken;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.exception.InvalidRefreshTokenException;
import com.ecommerce.api.repository.RefreshTokenRepository;
import com.ecommerce.api.security.JwtProperties;
import com.ecommerce.api.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public IssuedRefreshToken issue(User user) {
        Instant now = Instant.now();
        enforceSessionLimit(user, now);
        return createToken(user, UUID.randomUUID().toString(), now);
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedRefreshToken rotate(String rawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));

        Instant now = Instant.now();

        if (current.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException("Refresh token has already been used");
        }

        if (!current.getExpiresAt().isAfter(now)) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        Instant absoluteExpiry = current.getSessionStartedAt().plus(jwtProperties.absoluteRefreshExpiration());
        if (!absoluteExpiry.isAfter(now)) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException("Session has reached its maximum lifetime; please log in again");
        }

        User user = current.getUser();

        if (!user.isEnabled()) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException("User account is disabled");
        }

        current.setRevokedAt(now);

        return createToken(user, current.getFamilyId(), current.getSessionStartedAt());
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    private IssuedRefreshToken createToken(User user, String familyId, Instant sessionStartedAt) {
        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(jwtProperties.refreshExpiration());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setFamilyId(familyId);
        refreshToken.setSessionStartedAt(sessionStartedAt);
        refreshToken.setExpiresAt(expiresAt);

        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(rawToken, user, expiresAt);
    }

    private void enforceSessionLimit(User user, Instant now) {
        List<RefreshToken> activeTokens = refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThanOrderBySessionStartedAtAsc(
                        user.getId(), now);

        Set<String> activeFamilyIds = new LinkedHashSet<>();
        for (RefreshToken token : activeTokens) {
            activeFamilyIds.add(token.getFamilyId());
        }

        List<String> oldestFirst = List.copyOf(activeFamilyIds);
        int excess = oldestFirst.size() - jwtProperties.maxSessionsPerUser() + 1;

        for (int index = 0; index < excess; index++) {
            refreshTokenRepository.revokeFamily(oldestFirst.get(index), now);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
