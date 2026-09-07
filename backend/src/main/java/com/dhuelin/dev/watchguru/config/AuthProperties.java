package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Trusted OIDC issuers.
 *
 * <p>Each entry is an identity provider whose tokens this API will accept.
 * Adding one is a security decision, not configuration housekeeping, which is
 * why {@link Issuer#trustEmailVerification()} defaults to {@code false}: see
 * the note on that method.
 *
 * @param issuers      providers whose signed tokens are accepted
 * @param audiences    expected {@code aud} values; a token whose audience is
 *                     not one of these is rejected even if correctly signed,
 *                     which is what stops a token minted for a different
 *                     application being replayed against this one
 * @param requireHttps reject issuer URIs that are not HTTPS; only ever turned
 *                     off for a local test issuer
 */
@ConfigurationProperties(prefix = "watch-guru.auth")
public record AuthProperties(
        List<Issuer> issuers,
        List<String> audiences,
        @DefaultValue("true") boolean requireHttps) {

    public AuthProperties {
        issuers = issuers == null ? List.of() : List.copyOf(issuers);
        audiences = audiences == null ? List.of() : List.copyOf(audiences);
    }

    /**
     * @param name                   short label used in logs and metrics
     * @param uri                    OIDC issuer identifier, matched against the
     *                               token's {@code iss} claim and used to
     *                               discover the JWK set
     * @param trustEmailVerification whether this provider's
     *                               {@code email_verified} claim may be used to
     *                               link a sign-in to an existing account with
     *                               the same address
     */
    public record Issuer(String name, String uri, @DefaultValue("false") boolean trustEmailVerification) {

        /**
         * Whether a sign-in from this provider may adopt an existing account
         * that shares its email address.
         *
         * <p>This defaults to <strong>false</strong> on purpose. Linking
         * accounts by email address is a takeover primitive: an identity
         * provider that lets someone claim an address it has not actually
         * verified can be used to walk into any account registered with that
         * address. Apple and Google both verify the addresses they assert, so
         * both are configured with this on. Any provider added later starts
         * untrusted and someone has to decide, deliberately, to change that.
         */
        public boolean trustEmailVerification() {
            return trustEmailVerification;
        }
    }
}
