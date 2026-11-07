package com.ecommerce.api;

import com.ecommerce.api.security.AuthRateLimitFilter;
import com.ecommerce.api.security.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimitFilterTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void blocksRequestsAfterTheLimitWithinTheWindow() throws Exception {
        AuthRateLimitFilter filter = filter(true, 2, Duration.ofMinutes(1));

        assertEquals(200, perform(filter, "login", "198.51.100.10").getStatus());
        assertEquals(200, perform(filter, "login", "198.51.100.10").getStatus());

        MockHttpServletResponse blocked = perform(filter, "login", "198.51.100.10");

        assertEquals(429, blocked.getStatus());
        assertTrue(Long.parseLong(blocked.getHeader(HttpHeaders.RETRY_AFTER)) > 0);
        assertTrue(blocked.getContentAsString().contains("Too many authentication attempts"));
    }

    @Test
    void tracksDifferentClientsAndEndpointsIndependently() throws Exception {
        AuthRateLimitFilter filter = filter(true, 1, Duration.ofMinutes(1));

        assertEquals(200, perform(filter, "login", "198.51.100.20").getStatus());
        assertEquals(429, perform(filter, "login", "198.51.100.20").getStatus());
        assertEquals(200, perform(filter, "login", "198.51.100.21").getStatus());
        assertEquals(200, perform(filter, "register", "198.51.100.20").getStatus());
    }

    @Test
    void disabledFilterAllowsEveryRequest() throws Exception {
        AuthRateLimitFilter filter = filter(false, 1, Duration.ofMinutes(1));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(200, perform(filter, "login", "198.51.100.30").getStatus());
        }
    }

    @Test
    void doesNotLimitNonAuthenticationRequests() throws Exception {
        AuthRateLimitFilter filter = filter(true, 1, Duration.ofMinutes(1));

        assertEquals(200, perform(filter, "products", "198.51.100.40").getStatus());
        assertEquals(200, perform(filter, "products", "198.51.100.40").getStatus());
    }

    private AuthRateLimitFilter filter(boolean enabled, int maxRequests, Duration window) {
        return new AuthRateLimitFilter(new RateLimitProperties(enabled, maxRequests, window), objectMapper);
    }

    private MockHttpServletResponse perform(AuthRateLimitFilter filter, String path, String clientIp)
            throws Exception {
        String uri = switch (path) {
            case "login" -> "/api/auth/login";
            case "register" -> "/api/auth/register";
            default -> "/api/products";
        };

        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(clientIp);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        return response;
    }
}
