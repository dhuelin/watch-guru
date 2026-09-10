package com.dhuelin.dev.watchguru.provider.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stops hammering an upstream that is already failing.
 *
 * <p>Without one, a TMDB outage turns every search into a full retry sequence
 * that ends in a timeout, and request threads pile up waiting on a service that
 * is not going to answer. Failing immediately is both faster for the caller and
 * kinder to the upstream trying to recover.
 *
 * <p>Three states. CLOSED passes calls through. After {@code failureThreshold}
 * consecutive failures it moves to OPEN and rejects everything for
 * {@code openDuration}. It then allows a single probe (HALF_OPEN): success
 * closes it, failure re-opens it for another full interval.
 *
 * <p>The clock is injected so the state machine can be tested without sleeping.
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openedAt = new AtomicReference<>();

    /** Guards the single probe so a burst of callers does not all get through. */
    private final AtomicReference<Boolean> probeInFlight = new AtomicReference<>(false);

    public CircuitBreaker(String name, int failureThreshold, Duration openDuration, Clock clock) {
        this.name = name;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public State state() {
        return state.get();
    }

    /**
     * Whether a call may proceed now.
     *
     * <p>Also performs the OPEN to HALF_OPEN transition when the interval has
     * elapsed, since there is no background timer.
     */
    public boolean allowsRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN) {
            Instant since = openedAt.get();
            if (since != null && Duration.between(since, clock.instant()).compareTo(openDuration) >= 0
                    && state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                probeInFlight.set(false);
                log.info("Circuit {} half-open: allowing one probe", name);
            } else {
                return false;
            }
        }
        // HALF_OPEN: exactly one caller gets through.
        return probeInFlight.compareAndSet(false, true);
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        probeInFlight.set(false);
        if (state.getAndSet(State.CLOSED) != State.CLOSED) {
            log.info("Circuit {} closed: upstream recovered", name);
        }
    }

    public void recordFailure() {
        probeInFlight.set(false);
        if (state.get() == State.HALF_OPEN) {
            // The probe failed; the upstream is still unwell. Serve another
            // full interval rather than probing again immediately.
            trip();
            return;
        }
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            trip();
        }
    }

    private void trip() {
        openedAt.set(clock.instant());
        if (state.getAndSet(State.OPEN) != State.OPEN) {
            log.warn("Circuit {} opened after {} consecutive failures; rejecting calls for {}",
                    name, consecutiveFailures.get(), openDuration);
        }
    }
}
