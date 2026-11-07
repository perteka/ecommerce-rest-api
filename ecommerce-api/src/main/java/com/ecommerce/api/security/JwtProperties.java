package com.ecommerce.api.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotBlank String issuer,
        @NotNull Duration expiration,
        @NotNull Duration refreshExpiration,
        @NotNull Duration absoluteRefreshExpiration,
        @Min(1) int maxSessionsPerUser,
        @NotNull Duration revokedTokenRetention
) {
}
