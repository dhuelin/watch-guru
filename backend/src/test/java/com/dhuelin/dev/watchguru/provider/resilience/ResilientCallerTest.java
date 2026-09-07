package com.dhuelin.dev.watchguru.provider.resilience;

import com.dhuelin.dev.watchguru.provider.MetadataProviderException;
import com.dhuelin.dev.watchguru.provider.UpstreamUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientCallerTest {

    private final List<Duration> slept = new ArrayList<>();
    private MeterRegistry meters;
    private CircuitBreaker breaker;
    private ResilientCaller caller;

    @BeforeEach
    void setUp() {
        slept.clear();
        meters = new SimpleMeterRegistry();
        breaker = new CircuitBreaker("tmdb", 3, Duration.ofSeconds(30),
                Clock.fixed(java.time.Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        caller = new ResilientCaller(
                new RetryPolicy(3, Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofSeconds(10)),
                breaker,
                meters,
                slept::add);
    }

    private static HttpServerErrorException serverError() {
        return HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway", new HttpHeaders(), new byte[0], null);
    }

    @Test
    @DisplayName("a call that works is returned unchanged and not slept on")
    void successPassesThrough() {
        assertThat(caller.call("search", () -> "results")).isEqualTo("results");
        assertThat(slept).isEmpty();
    }

    @Test
    @DisplayName("a transient failure is retried and the second attempt is returned")
    void retriesUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();

        String result = caller.call("search", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw serverError();
            }
            return "results";
        });

        assertThat(result).isEqualTo("results");
        assertThat(attempts).hasValue(2);
        assertThat(slept).containsExactly(Duration.ofMillis(250));
    }

    @Test
    @DisplayName("retries stop at the configured maximum")
    void stopsAtMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> caller.call("search", () -> {
            attempts.incrementAndGet();
            throw serverError();
        })).isInstanceOf(MetadataProviderException.class);

        assertThat(attempts).hasValue(3);
        assertThat(slept).containsExactly(Duration.ofMillis(250), Duration.ofMillis(500));
    }

    @Test
    @DisplayName("a rate-limited call honours Retry-After instead of the computed backoff")
    void honoursRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "2");
        AtomicInteger attempts = new AtomicInteger();

        caller.call("search", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, new byte[0], null);
            }
            return "results";
        });

        assertThat(slept).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("a 404 fails immediately without retrying or tripping the breaker")
    void notFoundIsNotRetriedAndDoesNotTripTheBreaker() {
        AtomicInteger attempts = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> caller.call("detail", () -> {
                attempts.incrementAndGet();
                throw HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], null);
            })).isInstanceOf(MetadataProviderException.class);
        }

        assertThat(attempts).hasValue(5);
        assertThat(slept).isEmpty();
        // Missing titles are normal upstream behaviour. Counting them as
        // failures would open the circuit on a perfectly healthy provider.
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("exhausted retries count as one circuit failure, not one per attempt")
    void exhaustedRetriesCountOnce() {
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> caller.call("search", () -> {
                throw serverError();
            })).isInstanceOf(MetadataProviderException.class);
        }

        // Two exhausted calls, six attempts. If each attempt counted, the
        // breaker would already be open.
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("once the circuit opens, calls fail fast without touching the upstream")
    void openCircuitFailsFast() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> caller.call("search", () -> {
                throw serverError();
            })).isInstanceOf(MetadataProviderException.class);
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        AtomicInteger attemptsAfterOpen = new AtomicInteger();
        assertThatThrownBy(() -> caller.call("search", () -> {
            attemptsAfterOpen.incrementAndGet();
            return "results";
        })).isInstanceOf(UpstreamUnavailableException.class);

        // The point of the breaker: the upstream is not called at all.
        assertThat(attemptsAfterOpen).hasValue(0);
    }

    @Test
    @DisplayName("outcomes are recorded so upstream health is visible")
    void recordsMetrics() {
        caller.call("search", () -> "results");

        assertThat(meters.find("watchguru.provider.call")
                .tag("operation", "search").tag("outcome", "success").timer())
                .isNotNull()
                .satisfies(timer -> assertThat(timer.count()).isEqualTo(1));
    }
}
