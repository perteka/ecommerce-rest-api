package com.ecommerce.api.security;

import com.ecommerce.api.dto.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory, per-IP fixed-window rate limiter for authentication endpoints.
 * It protects against credential stuffing and registration abuse on a single instance.
 * Deployments with multiple instances should put a shared rate limiter (for example Redis)
 * in front of the application.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh"
    );

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        long windowMillis = properties.authWindow().toMillis();
        String key = request.getRequestURI() + "|" + clientIp(request);

        cleanupIfNeeded(now, windowMillis);

        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));
        if (!counter.tryAcquire(now, windowMillis, properties.authMaxRequests())) {
            long retryAfterSeconds = Math.max(1, counter.secondsUntilReset(now, windowMillis));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));

            ApiError error = ApiError.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    "Too many authentication attempts. Please try again later.",
                    request.getRequestURI()
            );
            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private void cleanupIfNeeded(long now, long windowMillis) {
        if (counters.size() < CLEANUP_THRESHOLD) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().isExpired(now, windowMillis));
    }

    private static final class WindowCounter {

        private long windowStart;
        private int requestCount;

        private WindowCounter(long windowStart) {
            this.windowStart = windowStart;
        }

        private synchronized boolean tryAcquire(long now, long windowMillis, int maxRequests) {
            if (now - windowStart >= windowMillis) {
                windowStart = now;
                requestCount = 0;
            }

            if (requestCount >= maxRequests) {
                return false;
            }

            requestCount++;
            return true;
        }

        private synchronized long secondsUntilReset(long now, long windowMillis) {
            return Math.max(0, (windowStart + windowMillis - now) / 1000);
        }

        private synchronized boolean isExpired(long now, long windowMillis) {
            return now - windowStart >= windowMillis;
        }
    }
}
