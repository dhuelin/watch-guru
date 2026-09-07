package com.dhuelin.dev.watchguru.provider.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(
            3, Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofSeconds(10));

    private static HttpClientErrorException tooManyRequests(HttpHeaders headers) {
        return HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, new byte[0], null);
    }

    @Test
    @DisplayName("rate limiting is retryable")
    void rateLimitIsRetryable() {
        assertThat(policy.isRetryable(tooManyRequests(new HttpHeaders()))).isTrue();
    }

    @Test
    @DisplayName("server errors are retryable")
    void serverErrorsAreRetryable() {
        assertThat(policy.isRetryable(HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway", new HttpHeaders(), new byte[0], null))).isTrue();
    }

    @Test
    @DisplayName("transport failures are retryable")
    void transportFailuresAreRetryable() {
        assertThat(policy.isRetryable(new ResourceAccessException("timeout", new IOException()))).isTrue();
    }

    @Test
    @DisplayName("a missing title is not retryable")
    void notFoundIsNotRetryable() {
        // Retrying a 404 spends the rate-limit budget arriving at the same answer.
        assertThat(policy.isRetryable(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], null))).isFalse();
    }

    @Test
    @DisplayName("a bad API token is not retryable")
    void unauthorisedIsNotRetryable() {
        // No amount of waiting fixes a wrong token.
        assertThat(policy.isRetryable(HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", new HttpHeaders(), new byte[0], null))).isFalse();
    }

    @Test
    @DisplayName("backoff doubles and then stops at the ceiling")
    void backoffIsExponentialAndCapped() {
        Throwable error = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway", new HttpHeaders(), new byte[0], null);

        assertThat(policy.backoffFor(1, error)).isEqualTo(Duration.ofMillis(250));
        assertThat(policy.backoffFor(2, error)).isEqualTo(Duration.ofMillis(500));
        assertThat(policy.backoffFor(3, error)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.backoffFor(10, error)).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    @DisplayName("a numeric Retry-After overrides the computed backoff")
    void retryAfterSecondsIsHonoured() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "3");

        // Honouring this is the difference between backing off and being
        // rate limited harder.
        assertThat(policy.backoffFor(1, tooManyRequests(headers))).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("an unreasonable Retry-After is clamped rather than obeyed")
    void retryAfterIsClamped() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "600");

        // Ten minutes is a real value TMDB can send. Obeying it would pin a
        // request thread for ten minutes.
        assertThat(policy.backoffFor(1, tooManyRequests(headers))).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Retry-After in HTTP-date form is understood")
    void retryAfterDateIsHonoured() {
        String httpDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(Instant.now().plusSeconds(5), ZoneOffset.UTC));
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, httpDate);

        Duration backoff = policy.backoffFor(1, tooManyRequests(headers));

        // The spec permits both forms; only reading the numeric one would mean
        // silently ignoring half of what providers actually send.
        assertThat(backoff).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(6));
    }

    @Test
    @DisplayName("a nonsense Retry-After falls back to the computed backoff")
    void unparseableRetryAfterFallsBack() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "soon-ish");

        assertThat(policy.backoffFor(1, tooManyRequests(headers))).isEqualTo(Duration.ofMillis(250));
    }
}
