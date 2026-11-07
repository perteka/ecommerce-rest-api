package com.ecommerce.api;

import com.ecommerce.api.entity.RefreshToken;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.exception.InvalidRefreshTokenException;
import com.ecommerce.api.repository.RefreshTokenRepository;
import com.ecommerce.api.security.JwtProperties;
import com.ecommerce.api.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceImplTests {

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);

    @Test
    void rotateRejectsSessionsOlderThanTheAbsoluteLifetime() {
        RefreshTokenServiceImpl service = new RefreshTokenServiceImpl(
                repository,
                properties(Duration.ofDays(1), 5)
        );

        RefreshToken token = token(
                Instant.now().minus(Duration.ofDays(2)),
                Instant.now().plus(Duration.ofDays(1))
        );
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw-token"));
        verify(repository).revokeFamily(eq("family-1"), any());
    }

    @Test
    void rotateRejectsExpiredTokens() {
        RefreshTokenServiceImpl service = new RefreshTokenServiceImpl(
                repository,
                properties(Duration.ofDays(30), 5)
        );

        RefreshToken token = token(Instant.now().minus(Duration.ofDays(1)), Instant.now().minusSeconds(1));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw-token"));
        verify(repository).revokeFamily(eq("family-1"), any());
    }

    @Test
    void issueRevokesTheOldestSessionWhenTheCapIsReached() {
        RefreshTokenServiceImpl service = new RefreshTokenServiceImpl(
                repository,
                properties(Duration.ofDays(30), 2)
        );
        RefreshToken oldestToken = token(Instant.now().minus(Duration.ofDays(2)), Instant.now().plus(Duration.ofDays(7)));
        oldestToken.setFamilyId("oldest-family");
        RefreshToken newerToken = token(Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(7)));
        newerToken.setFamilyId("newer-family");
        when(repository.findByUserIdAndRevokedAtIsNullAndExpiresAtGreaterThanOrderBySessionStartedAtAsc(any(), any()))
                .thenReturn(List.of(oldestToken, newerToken));

        service.issue(new User());

        verify(repository).revokeFamily(eq("oldest-family"), any());
        verify(repository, never()).revokeFamily(eq("newer-family"), any());
    }

    private RefreshToken token(Instant sessionStartedAt, Instant expiresAt) {
        User user = new User();
        user.setEnabled(true);
        user.setEmail("user@example.com");

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setFamilyId("family-1");
        token.setSessionStartedAt(sessionStartedAt);
        token.setExpiresAt(expiresAt);

        return token;
    }

    private JwtProperties properties(Duration absoluteLifetime, int maxSessions) {
        return new JwtProperties(
                "0123456789abcdef0123456789abcdef",
                "test-issuer",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                absoluteLifetime,
                maxSessions,
                Duration.ofDays(7)
        );
    }
}
