package com.dhuelin.dev.watchguru.security;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The identity providers whose tokens this service accepts, and the decoders
 * that check them.
 *
 * <p>Extracted so that exactly one construction of these decoders exists.
 * Provider tokens now arrive by two routes -- the resource-server filter chain
 * and the token-exchange endpoint -- and two independently built validators
 * would eventually disagree about what a valid token is. The one that is weaker
 * becomes the way in.
 */
@Component
public class TrustedIssuers {

    private static final Logger log = LoggerFactory.getLogger(TrustedIssuers.class);

    private final Map<String, JwtDecoder> decoders;

    public TrustedIssuers(AuthProperties properties) {
        if (properties.issuers().isEmpty()) {
            // Failing to start is the correct response. An API that silently
            // serves unauthenticated traffic because its issuer list was empty
            // is exactly the state this exists to prevent, and a misconfigured
            // deployment must not be able to reach it.
            throw new IllegalStateException(
                    "No OIDC issuers configured. Set watch-guru.auth.issuers[0].uri. For local "
                            + "development point it at a mock OIDC issuer; there is deliberately "
                            + "no bypass switch.");
        }
        if (properties.audiences().isEmpty()) {
            // Fail closed, exactly as an empty issuer list does. Apple and
            // Google issue tokens to any registered client, so without an
            // audience check a token harvested by any unrelated app with Google
            // sign-in is accepted here as its owner.
            throw new IllegalStateException(
                    "No watch-guru.auth.audiences configured. Set WATCH_GURU_AUTH_AUDIENCES to the "
                            + "OAuth client ids of the apps. Without it any token from a trusted "
                            + "issuer is accepted, including one minted for a different "
                            + "application.");
        }

        Map<String, JwtDecoder> byIssuer = new LinkedHashMap<>();
        for (AuthProperties.Issuer issuer : properties.issuers()) {
            if (properties.requireHttps() && !issuer.uri().startsWith("https://")) {
                throw new IllegalStateException(
                        "Issuer " + issuer.name() + " is not HTTPS: " + issuer.uri());
            }
            byIssuer.put(issuer.uri(), decoderFor(issuer, properties.audiences()));
            log.info("Trusting OIDC issuer {} ({}); email verification trusted: {}",
                    issuer.name(), issuer.uri(), issuer.trustEmailVerification());
        }
        this.decoders = Map.copyOf(byIssuer);
    }

    /** The decoder for one issuer, or null when that issuer is not trusted. */
    public JwtDecoder decoderFor(String issuerUri) {
        return decoders.get(issuerUri);
    }

    public Map<String, JwtDecoder> decoders() {
        return decoders;
    }

    /**
     * Verifies a provider token supplied in a request body rather than the
     * Authorization header.
     *
     * <p>The issuer is read from the token's own unverified claims only to
     * choose a decoder. That is safe precisely because an issuer absent from
     * the map has no decoder and is rejected: an attacker naming their own
     * issuer selects nothing rather than pointing this server at a JWK set they
     * control. The chosen decoder then verifies the signature, and the issuer
     * claim again, against that provider's published keys.
     */
    public Jwt verify(String token) {
        String issuer = unverifiedIssuer(token);
        JwtDecoder decoder = issuer == null ? null : decoders.get(issuer);
        if (decoder == null) {
            throw new UntrustedIssuerException(
                    "Token issuer is not one this service accepts.");
        }
        return decoder.decode(token);
    }

    /**
     * Reads {@code iss} without verifying anything.
     *
     * <p>Nimbus's parser is used rather than splitting the string by hand so
     * that a malformed token fails here instead of somewhere less careful.
     */
    private static String unverifiedIssuer(String token) {
        try {
            var claims = com.nimbusds.jwt.JWTParser.parse(token).getJWTClaimsSet();
            return claims.getIssuer();
        } catch (java.text.ParseException e) {
            throw new UntrustedIssuerException("Token is not a well-formed JWT.");
        }
    }

    /**
     * Decoder for one issuer, with audience validation on top of the defaults.
     *
     * <p>Wrapped in a {@link LazyJwtDecoder}. Both {@code withIssuerLocation}
     * and {@code JwtDecoders.fromIssuerLocation} fetch the provider's discovery
     * document while <em>building</em> the decoder, so without this the
     * application cannot start unless Apple and Google are both reachable --
     * verified the hard way: startup fails outright behind restricted egress.
     *
     * <p>The default validators cover signature, expiry and issuer. Audience is
     * the one that stops a correctly signed token minted for a <em>different</em>
     * application being replayed here: both Apple and Google issue tokens to any
     * registered client, so "signed by Google" says nothing about who the token
     * was for.
     */
    private static JwtDecoder decoderFor(AuthProperties.Issuer issuer, List<String> audiences) {
        return new LazyJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer.uri()).build();

            OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuer.uri()),
                    new AudienceValidator(audiences));

            decoder.setJwtValidator(validator);
            return decoder;
        });
    }

    /** A token whose issuer this service does not trust. */
    public static class UntrustedIssuerException extends JwtException {
        public UntrustedIssuerException(String message) {
            super(message);
        }
    }
}
