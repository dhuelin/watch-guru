package com.dhuelin.dev.watchguru.provider.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The breaker's state machine, driven by a clock the test controls so the
 * transitions are asserted rather than waited for.
 */
class CircuitBreakerTest {

    /** A clock the test moves by hand. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MovableClock clock;
    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        breaker = new CircuitBreaker("test", 3, Duration.ofSeconds(30), clock);
    }

    @Test
    @DisplayName("starts closed and passes calls through")
    void startsClosed() {
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.allowsRequest()).isTrue();
    }

    @Test
    @DisplayName("opens on the configured number of consecutive failures")
    void opensAfterThreshold() {
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);

        breaker.recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.allowsRequest()).isFalse();
    }

    @Test
    @DisplayName("a success resets the failure count, so scattered failures never trip it")
    void successResetsTheCount() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();

        // Intermittent failures are normal; only a sustained run means the
        // upstream is actually down.
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("stays open for the full interval")
    void staysOpenForTheInterval() {
        trip();

        clock.advance(Duration.ofSeconds(29));

        assertThat(breaker.allowsRequest()).isFalse();
    }

    @Test
    @DisplayName("allows exactly one probe once the interval elapses")
    void allowsOneProbeAfterTheInterval() {
        trip();
        clock.advance(Duration.ofSeconds(30));

        assertThat(breaker.allowsRequest()).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        // A queue of waiting callers must not all be released at a service
        // that has not yet proved it recovered.
        assertThat(breaker.allowsRequest()).isFalse();
        assertThat(breaker.allowsRequest()).isFalse();
    }

    @Test
    @DisplayName("a successful probe closes the circuit")
    void successfulProbeCloses() {
        trip();
        clock.advance(Duration.ofSeconds(30));
        breaker.allowsRequest();

        breaker.recordSuccess();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.allowsRequest()).isTrue();
    }

    @Test
    @DisplayName("a failed probe re-opens for another full interval")
    void failedProbeReopens() {
        trip();
        clock.advance(Duration.ofSeconds(30));
        breaker.allowsRequest();

        breaker.recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.allowsRequest()).isFalse();

        // And the interval restarts from the failed probe, rather than the
        // circuit becoming a probe-every-call loop.
        clock.advance(Duration.ofSeconds(29));
        assertThat(breaker.allowsRequest()).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(breaker.allowsRequest()).isTrue();
    }

    @Test
    @DisplayName("one failed probe re-opens immediately, without needing the full threshold again")
    void probeFailureDoesNotNeedThresholdAgain() {
        trip();
        clock.advance(Duration.ofSeconds(30));
        breaker.allowsRequest();
        breaker.recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private void trip() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
