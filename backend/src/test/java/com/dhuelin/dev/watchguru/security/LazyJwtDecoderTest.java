package com.dhuelin.dev.watchguru.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * That decoder construction is genuinely deferred.
 *
 * <p>This exists because the obvious spelling is wrong in a way that is
 * invisible in development. Both {@code JwtDecoders.fromIssuerLocation} and
 * {@code NimbusJwtDecoder.withIssuerLocation(...).build()} fetch the provider's
 * discovery document while building the decoder, so an application using them
 * directly cannot start unless Apple and Google are reachable -- which looks
 * fine on a laptop and fails in a restricted network or during a provider
 * outage.
 *
 * <p>The integration suite would only catch a regression in an environment that
 * cannot reach those providers. These assertions catch it anywhere.
 */
class LazyJwtDecoderTest {

    private static Jwt anyToken() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    @DisplayName("constructing the decoder does not build the delegate")
    void constructionDoesNotBuildTheDelegate() {
        AtomicInteger built = new AtomicInteger();

        new LazyJwtDecoder(() -> {
            built.incrementAndGet();
            return token -> anyToken();
        });

        // Nothing is fetched until a token actually needs verifying. A service
        // with no traffic never needs to have reached Apple.
        assertThat(built).hasValue(0);
    }

    @Test
    @DisplayName("the delegate is built once, on first decode, and then reused")
    void delegateIsBuiltOnceOnFirstUse() {
        AtomicInteger built = new AtomicInteger();
        LazyJwtDecoder decoder = new LazyJwtDecoder(() -> {
            built.incrementAndGet();
            return token -> anyToken();
        });

        decoder.decode("first");
        assertThat(built).hasValue(1);

        decoder.decode("second");
        decoder.decode("third");
        assertThat(built).hasValue(1);
    }

    @Test
    @DisplayName("a failed build is not cached, so a blip does not become an outage")
    void failureIsNotCached() {
        AtomicInteger attempts = new AtomicInteger();
        JwtDecoder working = token -> anyToken();

        LazyJwtDecoder decoder = new LazyJwtDecoder(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("discovery unavailable");
            }
            return working;
        });

        assertThatThrownBy(() -> decoder.decode("first"))
                .isInstanceOf(IllegalStateException.class);

        // Caching the failure would mean one unlucky moment at startup keeps
        // the service down until someone redeploys it.
        assertThat(decoder.decode("second")).isNotNull();
        assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("the delegate's result is passed straight through")
    void delegatesDecoding() {
        Jwt expected = anyToken();
        LazyJwtDecoder decoder = new LazyJwtDecoder(() -> token -> expected);

        assertThat(decoder.decode("token")).isSameAs(expected);
    }
}
