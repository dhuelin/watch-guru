package com.dhuelin.dev.watchguru.security;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Misconfiguration must stop the application, not degrade it quietly.
 *
 * <p>Both of these were once a warning-and-carry-on, and both had the same
 * consequence: an API that looks healthy while accepting tokens it should
 * refuse. The audience case is the sharper of the two, because Apple and Google
 * issue tokens to any registered client — without an audience check, a token
 * harvested by any unrelated app offering "Sign in with Google" is accepted
 * here as its owner.
 */
class SecurityConfigFailClosedTest {

    private final SecurityConfig config = new SecurityConfig();

    private static AuthProperties properties(List<AuthProperties.Issuer> issuers, List<String> audiences) {
        return new AuthProperties(issuers, audiences, true);
    }

    private static AuthProperties.Issuer google() {
        return new AuthProperties.Issuer("google", "https://accounts.google.com", true);
    }

    @Test
    @DisplayName("no issuers configured refuses to start")
    void emptyIssuersFailsStartup() {
        assertThatThrownBy(() -> config.issuerResolver(properties(List.of(), List.of("app"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuers");
    }

    @Test
    @DisplayName("no audiences configured refuses to start")
    void emptyAudiencesFailsStartup() {
        // The regression this guards: an empty audience list means the one
        // check answering "was this token minted for us" is never installed.
        assertThatThrownBy(() -> config.issuerResolver(properties(List.of(google()), List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audiences");
    }

    @Test
    @DisplayName("a non-HTTPS issuer refuses to start when HTTPS is required")
    void plaintextIssuerFailsStartup() {
        AuthProperties properties = new AuthProperties(
                List.of(new AuthProperties.Issuer("local", "http://localhost:9099", false)),
                List.of("app"),
                true);

        assertThatThrownBy(() -> config.issuerResolver(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not HTTPS");
    }

    @Test
    @DisplayName("a complete configuration builds a resolver")
    void validConfigurationSucceeds() {
        // Decoders are lazy, so this builds without reaching Google.
        assertThat(config.issuerResolver(properties(List.of(google()), List.of("watch-guru-ios"))))
                .isNotNull();
    }

    @Test
    @DisplayName("email verification is not trusted unless a provider is configured for it")
    void emailVerificationDefaultsToUntrusted() {
        // Linking on an email address is an account-takeover primitive if a
        // provider hands out addresses it never checked, so the default matters.
        assertThat(new AuthProperties.Issuer("new-provider", "https://idp.example", false)
                .trustEmailVerification()).isFalse();
    }
}
