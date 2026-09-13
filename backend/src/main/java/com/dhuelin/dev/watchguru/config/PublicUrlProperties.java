package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where this service is reachable from outside.
 *
 * <p>Two features need it, which is why it is not either one's property: the
 * Plex webhook URL a user pastes into their server, and the OAuth redirect
 * Trakt sends a browser back to. Both are addresses somebody else's software
 * dials, so neither can be derived from the request in a deployment behind a
 * proxy that rewrites the host.
 *
 * @param publicBaseUrl e.g. {@code https://api.watch-guru.dev}. Left empty,
 *                      each caller falls back to the incoming request, which
 *                      is right in development and wrong behind such a proxy
 */
@ConfigurationProperties(prefix = "watch-guru")
public record PublicUrlProperties(
        @DefaultValue("") String publicBaseUrl
) {

    /** The base URL without a trailing slash, or null when none is configured. */
    public String trimmed() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        String value = publicBaseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
