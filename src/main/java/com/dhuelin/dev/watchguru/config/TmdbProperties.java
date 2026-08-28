package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * TMDB client configuration.
 *
 * @param baseUrl        API root, e.g. {@code https://api.themoviedb.org/3}
 * @param imageBaseUrl   CDN root for poster and backdrop paths
 * @param apiToken       TMDB "API Read Access Token" (v4 auth), sent as a bearer token
 * @param defaultLanguage language tag used when a request does not specify one
 * @param defaultRegion  ISO 3166-1 country used for availability lookups
 */
@ConfigurationProperties(prefix = "watch-guru.tmdb")
public record TmdbProperties(
        String baseUrl,
        String imageBaseUrl,
        String apiToken,
        String defaultLanguage,
        String defaultRegion,
        Duration connectTimeout,
        Duration readTimeout
) {
    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank();
    }
}
