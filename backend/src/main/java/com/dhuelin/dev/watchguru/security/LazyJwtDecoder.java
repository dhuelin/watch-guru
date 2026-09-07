package com.dhuelin.dev.watchguru.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.function.Supplier;

/**
 * A {@link JwtDecoder} that builds its delegate on first use.
 *
 * <p>Both {@code JwtDecoders.fromIssuerLocation} and
 * {@code NimbusJwtDecoder.withIssuerLocation(...).build()} fetch the provider's
 * OIDC discovery document <em>while building the decoder</em>, which means at
 * application startup. That has two consequences worth avoiding: the service
 * cannot boot while Apple or Google is unreachable -- including during someone
 * else's outage, and including in any environment with restricted egress -- and
 * every test that loads the full context performs real network I/O to two
 * external providers.
 *
 * <p>Deferring construction to the first token that actually needs verifying
 * moves that dependency to where it belongs. A service with no traffic does not
 * need to have reached Apple.
 *
 * <p>Failures are not cached: if discovery fails, the next request tries again.
 * Caching the failure would turn a momentary blip into an outage lasting until
 * redeploy.
 */
public class LazyJwtDecoder implements JwtDecoder {

    private final Supplier<JwtDecoder> factory;

    private volatile JwtDecoder delegate;

    public LazyJwtDecoder(Supplier<JwtDecoder> factory) {
        this.factory = factory;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        return delegate().decode(token);
    }

    private JwtDecoder delegate() {
        JwtDecoder existing = delegate;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (delegate == null) {
                // Assigned only on success, so a failed discovery leaves the
                // decoder unbuilt and the next caller retries.
                delegate = factory.get();
            }
            return delegate;
        }
    }
}
