package com.dhuelin.dev.watchguru.provider.resilience;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * Which upstream failures are worth retrying, and how long to wait.
 *
 * <p>Pure and side-effect free so the decisions can be tested directly rather
 * than inferred from timing.
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Duration maxRetryAfter;

    public RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff, Duration maxRetryAfter) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.maxRetryAfter = maxRetryAfter;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Whether another attempt could plausibly succeed.
     *
     * <p>Retrying a 404 or a 401 just spends the rate-limit budget arriving at
     * the same answer, so only rate limiting, server-side faults and transport
     * failures qualify. A 401 in particular means the API token is wrong, which
     * no amount of waiting fixes.
     */
    public boolean isRetryable(Throwable error) {
        if (error instanceof ResourceAccessException) {
            // Connect timeout, read timeout, connection reset: transport-level
            // and frequently transient.
            return true;
        }
        if (error instanceof RestClientResponseException response) {
            HttpStatusCode status = response.getStatusCode();
            return status.value() == 429 || status.is5xxServerError();
        }
        return false;
    }

    /**
     * How long to wait before attempt {@code attempt} (1-based for the first
     * retry), preferring the server's own instruction when it gave one.
     *
     * <p>Exponential with a ceiling. TMDB's 429 responses carry
     * {@code Retry-After}, and honouring it is the difference between backing
     * off and being rate limited harder; but it is clamped, because an upstream
     * asking for a ten-minute wait must not pin a request thread for ten
     * minutes.
     */
    public Duration backoffFor(int attempt, Throwable error) {
        Duration serverHint = retryAfter(error);
        if (serverHint != null) {
            return serverHint.compareTo(maxRetryAfter) > 0 ? maxRetryAfter : serverHint;
        }
        long millis = initialBackoff.toMillis() * (1L << Math.max(0, attempt - 1));
        return millis > maxBackoff.toMillis() ? maxBackoff : Duration.ofMillis(millis);
    }

    /**
     * The {@code Retry-After} header, in either of its permitted forms: a
     * delay in seconds, or an HTTP date.
     */
    static Duration retryAfter(Throwable error) {
        if (!(error instanceof RestClientResponseException response)) {
            return null;
        }
        HttpHeaders headers = response.getResponseHeaders();
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException notANumber) {
            // The other permitted form is an HTTP date.
            try {
                long millis = headers.getFirstDate(HttpHeaders.RETRY_AFTER);
                if (millis < 0) {
                    return null;
                }
                long delta = millis - System.currentTimeMillis();
                return delta <= 0 ? Duration.ZERO : Duration.ofMillis(delta);
            } catch (IllegalArgumentException | DateTimeParseException unparseable) {
                return null;
            }
        }
    }
}
