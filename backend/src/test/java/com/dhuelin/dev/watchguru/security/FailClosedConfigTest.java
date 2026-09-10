package com.dhuelin.dev.watchguru.security;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.security.session.AccessTokenIssuer;
import com.dhuelin.dev.watchguru.support.TestAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Misconfiguration must stop the application, not degrade it quietly.
 *
 * <p>Each of these was, or could plausibly have been, a warning-and-carry-on,
 * and each has the same consequence: an API that looks healthy while accepting
 * tokens it should refuse, or issuing ones it cannot honour. The audience case
 * is the sharpest, because Apple and Google issue tokens to any registered
 * client — without an audience check, a token harvested by any unrelated app
 * offering "Sign in with Google" is accepted here as its owner.
 */
class FailClosedConfigTest {

    private static AuthProperties properties(List<AuthProperties.Issuer> issuers, List<String> audiences) {
        return new AuthProperties(issuers, audiences, true, TestAuth.session());
    }

    private static AuthProperties.Issuer google() {
        return new AuthProperties.Issuer("google", "https://accounts.google.com", true);
    }

    @Test
    @DisplayName("no issuers configured refuses to start")
    void emptyIssuersFailsStartup() {
        assertThatThrownBy(() -> new TrustedIssuers(properties(List.of(), List.of("app"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuers");
    }

    @Test
    @DisplayName("no audiences configured refuses to start")
    void emptyAudiencesFailsStartup() {
        // The regression this guards: an empty audience list means the one
        // check answering "was this token minted for us" is never installed.
        assertThatThrownBy(() -> new TrustedIssuers(properties(List.of(google()), List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audiences");
    }

    @Test
    @DisplayName("a non-HTTPS issuer refuses to start when HTTPS is required")
    void plaintextIssuerFailsStartup() {
        AuthProperties properties = new AuthProperties(
                List.of(new AuthProperties.Issuer("local", "http://localhost:9099", false)),
                List.of("app"),
                true,
                TestAuth.session());

        assertThatThrownBy(() -> new TrustedIssuers(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not HTTPS");
    }

    @Test
    @DisplayName("a complete configuration builds the decoders")
    void validConfigurationSucceeds() {
        // Decoders are lazy, so this builds without reaching Google.
        assertThat(new TrustedIssuers(properties(List.of(google()), List.of("watch-guru-ios"))).decoders())
                .containsKey("https://accounts.google.com");
    }

    @Test
    @DisplayName("no session secret refuses to start")
    void missingSessionSecretFailsStartup() {
        // A generated-on-boot fallback would look like it worked, then sign
        // every user out on each restart and reject its own tokens across
        // instances behind a load balancer.
        assertThatThrownBy(() -> new AccessTokenIssuer(withSecret(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");
    }

    @Test
    @DisplayName("a session secret too short for HS256 refuses to start")
    void shortSessionSecretFailsStartup() {
        // Otherwise this surfaces as a failure on the first sign-in, in
        // production, rather than at startup.
        assertThatThrownBy(() -> new AccessTokenIssuer(withSecret("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("email verification is not trusted unless a provider is configured for it")
    void emailVerificationDefaultsToUntrusted() {
        // Linking on an email address is an account-takeover primitive if a
        // provider hands out addresses it never checked, so the default matters.
        assertThat(new AuthProperties.Issuer("new-provider", "https://idp.example", false)
                .trustEmailVerification()).isFalse();
    }

    private static AuthProperties withSecret(String secret) {
        return new AuthProperties(
                List.of(google()),
                List.of("app"),
                true,
                new AuthProperties.Session(secret, "https://watch-guru.test", "watch-guru-api",
                        Duration.ofMinutes(15), Duration.ofDays(30)));
    }
}
