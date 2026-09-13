package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Connecting Trakt (#39).
 *
 * <p>Off unless a client id and secret are configured. Trakt applications are
 * free but have to be registered by a person, and an "enabled" flag without
 * credentials would offer a Connect button that fails after the user has
 * already authorised -- the worst place to discover a deployment is not
 * configured.
 *
 * @param clientId     from the Trakt application registration
 * @param clientSecret likewise. Never leaves this service: the apps do not see
 *                     it, which is why the OAuth code is exchanged here rather
 *                     than on the phone
 * @param redirectUri  where Trakt sends the user back. Must match the
 *                     registration exactly. Left empty it is built from
 *                     {@code watch-guru.plex.public-base-url}, which is the
 *                     same address the Plex webhook uses
 * @param pageSize     history items per request. Trakt's maximum is 1000; 100
 *                     keeps one page small enough that a failure loses little
 *                     and the cursor moves often
 * @param maxPages     pages one sync may read. A first sync of a decade of
 *                     history is tens of thousands of items, and reading it in
 *                     bounded runs means no single run holds a connection open
 *                     for minutes -- the cursor makes the next run continue
 *                     where this one stopped
 * @param stateTtl     how long an authorisation started in a browser stays
 *                     redeemable
 */
@ConfigurationProperties(prefix = "watch-guru.trakt")
public record TraktProperties(
        String clientId,
        String clientSecret,
        @DefaultValue("https://api.trakt.tv") String baseUrl,
        @DefaultValue("https://trakt.tv/oauth/authorize") String authorizeUrl,
        @DefaultValue("") String redirectUri,
        @DefaultValue("100") int pageSize,
        @DefaultValue("20") int maxPages,
        @DefaultValue("10m") Duration stateTtl,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("15s") Duration readTimeout
) {

    /** Whether this deployment has been given a Trakt application to act as. */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
