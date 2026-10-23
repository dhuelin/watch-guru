package com.dhuelin.dev.watchguru.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:30Z");

    private final RateLimitFilter filter = new RateLimitFilter(Clock.fixed(NOW, ZoneOffset.UTC), true);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signedInAs(String subject) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256")
                .issuer("https://watch-guru.dev").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
    }

    private int send(String method, String uri, String remoteAddr, AtomicInteger passed) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> passed.incrementAndGet());
        return response.getStatus();
    }

    @Test
    @DisplayName("an import preview loop is cut off after five a minute, with a Retry-After")
    void previewIsBudgeted() throws Exception {
        signedInAs("u-1");
        AtomicInteger passed = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            assertThat(send("POST", "/api/v1/me/imports/preview", "10.0.0.1", passed)).isEqualTo(200);
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/me/imports/preview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> passed.incrementAndGet());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        assertThat(passed).hasValue(5);
    }

    @Test
    @DisplayName("one user's budget is not another's")
    void budgetIsPerUser() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        signedInAs("u-1");
        for (int i = 0; i < 6; i++) {
            send("POST", "/api/v1/me/imports/preview", "10.0.0.1", passed);
        }
        signedInAs("u-2");

        assertThat(send("POST", "/api/v1/me/imports/preview", "10.0.0.1", passed)).isEqualTo(200);
    }

    @Test
    @DisplayName("sign-in carries no token, so it is budgeted by address")
    void signInIsBudgetedByAddress() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        for (int i = 0; i < 20; i++) {
            send("POST", "/api/v1/auth/session", "203.0.113.9", passed);
        }

        assertThat(send("POST", "/api/v1/auth/session", "203.0.113.9", passed)).isEqualTo(429);
        assertThat(send("POST", "/api/v1/auth/session", "203.0.113.10", passed)).isEqualTo(200);
    }

    @Test
    @DisplayName("health checks are never counted")
    void actuatorIsNotLimited() {
        assertThat(RateLimitFilter.ruleFor("GET", "/actuator/health")).isNull();
        assertThat(RateLimitFilter.ruleFor("GET", "/api/v1/titles/search").name()).isEqualTo("search");
        assertThat(RateLimitFilter.ruleFor("GET", "/api/v1/me/watchlist").name()).isEqualTo("api");
    }
}
