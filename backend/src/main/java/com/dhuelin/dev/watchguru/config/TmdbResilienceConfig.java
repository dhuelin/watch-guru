package com.dhuelin.dev.watchguru.config;

import com.dhuelin.dev.watchguru.provider.resilience.CircuitBreaker;
import com.dhuelin.dev.watchguru.provider.resilience.ResilientCaller;
import com.dhuelin.dev.watchguru.provider.resilience.RetryPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Retry and circuit-breaker wiring for the TMDB client. */
@Configuration
public class TmdbResilienceConfig {

    @Bean
    RetryPolicy tmdbRetryPolicy(TmdbProperties properties) {
        TmdbProperties.Retry retry = properties.retry();
        return new RetryPolicy(
                retry.maxAttempts(), retry.initialBackoff(), retry.maxBackoff(), retry.maxRetryAfter());
    }

    @Bean
    CircuitBreaker tmdbCircuitBreaker(TmdbProperties properties) {
        TmdbProperties.CircuitBreaker breaker = properties.circuitBreaker();
        return new CircuitBreaker("tmdb", breaker.failureThreshold(), breaker.openDuration(), Clock.systemUTC());
    }

    @Bean
    ResilientCaller tmdbResilientCaller(RetryPolicy tmdbRetryPolicy,
                                        CircuitBreaker tmdbCircuitBreaker,
                                        MeterRegistry meterRegistry) {
        return new ResilientCaller(tmdbRetryPolicy, tmdbCircuitBreaker, meterRegistry);
    }
}
