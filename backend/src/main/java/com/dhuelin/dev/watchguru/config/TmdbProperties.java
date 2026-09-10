package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * TMDB client configuration.
 *
 * @param baseUrl        API root, e.g. {@code https://api.themoviedb.org/3}
 * @param imageBaseUrl   CDN root for poster and backdrop paths
 * @param apiToken       TMDB "API Read Access Token" (v4 auth), sent as a bearer token
 * @param defaultLanguage language tag used when a request does not specify one
 * @param defaultRegion  ISO 3166-1 country used for availability lookups
 * @param retry          how upstream failures are retried
 * @param circuitBreaker when to stop calling an upstream that is already failing
 * @param search         search-specific caching and input limits
 */
@ConfigurationProperties(prefix = "watch-guru.tmdb")
public record TmdbProperties(
        String baseUrl,
        String imageBaseUrl,
        String apiToken,
        String defaultLanguage,
        String defaultRegion,
        Duration connectTimeout,
        Duration readTimeout,
        @DefaultValue Retry retry,
        @DefaultValue CircuitBreaker circuitBreaker,
        @DefaultValue Search search
) {
    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank();
    }

    /**
     * @param maxAttempts    total attempts including the first
     * @param initialBackoff first retry delay; doubles thereafter
     * @param maxBackoff     ceiling on the computed delay
     * @param maxRetryAfter  ceiling on an upstream-supplied Retry-After, so a
     *                       provider asking for a long wait cannot pin a
     *                       request thread for that long
     */
    public record Retry(
            @DefaultValue("3") int maxAttempts,
            @DefaultValue("250ms") Duration initialBackoff,
            @DefaultValue("4s") Duration maxBackoff,
            @DefaultValue("10s") Duration maxRetryAfter) {
    }

    /**
     * @param failureThreshold consecutive failures before calls are rejected
     * @param openDuration     how long to reject before probing again
     */
    public record CircuitBreaker(
            @DefaultValue("5") int failureThreshold,
            @DefaultValue("30s") Duration openDuration) {
    }

    /**
     * @param cacheTtl       how long a search result stays usable; short,
     *                       because this exists to collapse the keystroke storm
     *                       from search-as-you-type rather than to hold results
     * @param cacheMaxSize   bounded so a pathological query stream cannot
     *                       exhaust the heap
     * @param minQueryLength queries shorter than this never reach the provider
     */
    public record Search(
            @DefaultValue("60s") Duration cacheTtl,
            @DefaultValue("1000") int cacheMaxSize,
            @DefaultValue("2") int minQueryLength) {
    }
}
