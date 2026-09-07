package com.dhuelin.dev.watchguru.provider.resilience;

import com.dhuelin.dev.watchguru.provider.MetadataProviderException;
import com.dhuelin.dev.watchguru.provider.UpstreamUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Runs an upstream call behind a retry policy and a circuit breaker, and
 * records what happened.
 *
 * <p>One place, so every provider call gets the same treatment rather than
 * whichever the author of that method remembered.
 */
public class ResilientCaller {

    private static final Logger log = LoggerFactory.getLogger(ResilientCaller.class);

    private final RetryPolicy retryPolicy;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meters;
    private final Sleeper sleeper;

    /** Indirection so retry timing can be asserted without real delays. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public ResilientCaller(RetryPolicy retryPolicy, CircuitBreaker circuitBreaker, MeterRegistry meters) {
        this(retryPolicy, circuitBreaker, meters, duration -> Thread.sleep(duration.toMillis()));
    }

    public ResilientCaller(RetryPolicy retryPolicy,
                           CircuitBreaker circuitBreaker,
                           MeterRegistry meters,
                           Sleeper sleeper) {
        this.retryPolicy = retryPolicy;
        this.circuitBreaker = circuitBreaker;
        this.meters = meters;
        this.sleeper = sleeper;
    }

    /**
     * @param operation short label used for metrics and logs
     * @param call      the upstream call
     */
    public <T> T call(String operation, Supplier<T> call) {
        if (!circuitBreaker.allowsRequest()) {
            count(operation, "circuit_open");
            throw new UpstreamUnavailableException(
                    "TMDB " + operation + " skipped: the circuit is open after repeated failures");
        }

        Timer.Sample sample = Timer.start(meters);
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                T result = call.get();
                circuitBreaker.recordSuccess();
                sample.stop(timer(operation, "success"));
                return result;
            } catch (RuntimeException e) {
                lastError = e;
                boolean lastAttempt = attempt == retryPolicy.maxAttempts();

                if (!retryPolicy.isRetryable(e)) {
                    // A 404 or a bad token is a real answer; recording it as a
                    // circuit failure would open the breaker on entirely
                    // healthy upstream behaviour.
                    sample.stop(timer(operation, "error"));
                    count(operation, "not_retryable");
                    throw wrap(operation, e);
                }
                if (lastAttempt) {
                    break;
                }

                Duration backoff = retryPolicy.backoffFor(attempt, e);
                count(operation, "retry");
                log.debug("TMDB {} attempt {} failed ({}), retrying in {}",
                        operation, attempt, e.getClass().getSimpleName(), backoff);
                try {
                    sleeper.sleep(backoff);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new MetadataProviderException("TMDB " + operation + " interrupted", interrupted);
                }
            }
        }

        circuitBreaker.recordFailure();
        sample.stop(timer(operation, "failure"));
        count(operation, "exhausted");
        throw wrap(operation, lastError);
    }

    private static MetadataProviderException wrap(String operation, Throwable error) {
        if (error instanceof MetadataProviderException already) {
            return already;
        }
        return new MetadataProviderException("TMDB " + operation + " request failed", error);
    }

    private Timer timer(String operation, String outcome) {
        return Timer.builder("watchguru.provider.call")
                .tag("provider", "tmdb")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meters);
    }

    private void count(String operation, String event) {
        meters.counter("watchguru.provider.events", "provider", "tmdb",
                "operation", operation, "event", event).increment();
    }
}
