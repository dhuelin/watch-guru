package com.dhuelin.dev.watchguru.security.session;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.dhuelin.dev.watchguru.security.AudienceValidator;

/**
 * Mints and verifies the access tokens this service issues for itself.
 *
 * <p>HMAC rather than RSA. The only party that needs to verify these is this
 * service, so a public key buys nothing and a private key would have to be
 * managed, rotated and distributed. If a second service ever needs to verify
 * them, that is the moment to switch to RS256 and publish a JWK set, not
 * before.
 *
 * <p>The subject carried is the internal user id, not the provider's. Once the
 * exchange has happened, the provider's opaque subject has done its job and
 * nothing downstream should have to know which provider a user came from.
 */
@Component
public class AccessTokenIssuer {

    /**
     * HS256 needs a key at least as long as its output. A shorter secret is a
     * weak signing key, and Nimbus rejects it at signing time -- which would be
     * a runtime failure on the first sign-in rather than at startup, so it is
     * checked here.
     */
    private static final int MIN_SECRET_BYTES = 32;

    /** Marks our own tokens, so a provider token can never be mistaken for one. */
    static final String CLAIM_TOKEN_TYPE = "typ_wg";
    static final String TOKEN_TYPE_ACCESS = "access";

    private final AuthProperties.Session session;
    private final SecretKeySpec key;

    public AccessTokenIssuer(AuthProperties properties) {
        this.session = properties.session();

        String secret = session == null ? null : session.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "No watch-guru.auth.session.secret configured. Set WATCH_GURU_AUTH_SESSION_SECRET "
                            + "to at least " + MIN_SECRET_BYTES + " random bytes. There is no "
                            + "generated fallback on purpose: one would appear to work, then log "
                            + "every user out on each restart and reject its own tokens across "
                            + "instances behind a load balancer.");
        }
        byte[] material = secret.getBytes(StandardCharsets.UTF_8);
        if (material.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "watch-guru.auth.session.secret is " + material.length + " bytes; HS256 needs at "
                            + "least " + MIN_SECRET_BYTES + ".");
        }
        this.key = new SecretKeySpec(material, "HmacSHA256");
    }

    /** An access token for a user, valid for {@code accessTtl}. */
    public Issued issue(AppUser user, Instant now) {
        Instant expiresAt = now.plus(session.accessTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(session.issuer())
                .audience(session.audience())
                .subject(String.valueOf(user.getId()))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign an access token", e);
        }
        return new Issued(jwt.serialize(), expiresAt);
    }

    /**
     * Decoder for tokens minted here, registered in the resource-server's
     * issuer map alongside Apple's and Google's.
     *
     * <p>Audience is checked with the same validator used for provider tokens,
     * against this service's own audience rather than the apps' OAuth client
     * ids -- those belong to the providers and have nothing to do with a token
     * we signed.
     */
    public JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(session.issuer()),
                new AudienceValidator(java.util.List.of(session.audience()))));
        return decoder;
    }

    public String issuerUri() {
        return session.issuer();
    }

    public Duration accessTtl() {
        return session.accessTtl();
    }

    public Duration refreshTtl() {
        return session.refreshTtl();
    }

    /** The internal user id a validated access token stands for. */
    public static Long userIdOf(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record Issued(String token, Instant expiresAt) {
    }
}
