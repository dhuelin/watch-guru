package com.dhuelin.dev.watchguru.streaming.trakt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * What is sealed in the credential store for one Trakt connection.
 *
 * <p>An absolute {@link #expiresAt} rather than the {@code expires_in} Trakt
 * sends: a duration only means something next to the moment it was issued, and
 * that moment is precisely what is lost once a row is written.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TraktTokens(String accessToken, String refreshToken, Instant expiresAt) {

    /** Renews a little early, so a sync does not start on a token about to die. */
    private static final long RENEW_BEFORE_SECONDS = 300;

    public static TraktTokens from(TraktResponses.Token token, Instant now) {
        return new TraktTokens(
                token.accessToken(),
                token.refreshToken(),
                now.plusSeconds(Math.max(token.expiresIn(), 0)));
    }

    public boolean needsRenewal(Instant now) {
        return expiresAt == null || expiresAt.minusSeconds(RENEW_BEFORE_SECONDS).isBefore(now);
    }
}
