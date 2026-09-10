package com.dhuelin.dev.watchguru.support;

import com.dhuelin.dev.watchguru.config.AuthProperties;

import java.time.Duration;
import java.util.List;

/**
 * Auth configuration for the web-slice tests.
 *
 * <p>One definition rather than a copy per test: these tests import the real
 * {@code SecurityConfig}, so a divergence here is a divergence in what they are
 * actually testing.
 */
public final class TestAuth {

    /** A real issuer URI, so the HTTPS check passes; the decoder is lazy and
     *  {@code jwt()} bypasses it, so no discovery document is ever fetched. */
    public static final String ISSUER = "https://accounts.google.com";

    public static final String AUDIENCE = "watch-guru-test";

    /** Long enough for HS256. Not a secret; it never leaves the test JVM. */
    public static final String SESSION_SECRET = "test-signing-secret-not-for-production-use";

    private TestAuth() {
    }

    public static AuthProperties properties() {
        return new AuthProperties(
                List.of(new AuthProperties.Issuer("google", ISSUER, true)),
                List.of(AUDIENCE),
                true,
                session());
    }

    public static AuthProperties.Session session() {
        return new AuthProperties.Session(
                SESSION_SECRET,
                "https://watch-guru.test",
                "watch-guru-api",
                Duration.ofMinutes(15),
                Duration.ofDays(30));
    }
}
