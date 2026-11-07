package com.ecommerce.api.service;

import com.ecommerce.api.repository.RefreshTokenRepository;
import com.ecommerce.api.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Scheduled(cron = "${security.jwt.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        int expired = refreshTokenRepository.deleteExpiredBefore(now);
        int revoked = refreshTokenRepository.deleteRevokedBefore(
                now.minus(jwtProperties.revokedTokenRetention())
        );

        if (expired > 0 || revoked > 0) {
            LOGGER.info("Refresh token cleanup removed {} expired and {} revoked tokens", expired, revoked);
        }
    }
}
