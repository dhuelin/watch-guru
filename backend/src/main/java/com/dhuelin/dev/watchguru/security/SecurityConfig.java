package com.dhuelin.dev.watchguru.security;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource-server security.
 *
 * <p>Replaces the previous arrangement, in which every endpoint took a
 * {@code userId} path variable and believed it. The identity now comes from a
 * signed token and nothing else.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * One authentication manager per trusted issuer, selected by the token's
     * {@code iss} claim.
     *
     * <p>Built from an explicit issuer-to-manager map rather than
     * {@code JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers(String...)}.
     * That convenience method constructs its own decoders internally, which
     * would quietly discard the audience validator configured below and leave
     * the API accepting tokens minted for other applications.
     *
     * <p>An issuer absent from the map resolves to null, and Spring Security
     * rejects the request. That is what stops a token carrying an attacker's
     * own {@code iss} from pointing this server at a JWK set they control.
     */
    @Bean
    JwtIssuerAuthenticationManagerResolver issuerResolver(AuthProperties properties) {
        if (properties.issuers().isEmpty()) {
            // Failing to start is the correct response. An API that silently
            // serves unauthenticated traffic because its issuer list was empty
            // is exactly the state this change exists to remove, and a
            // misconfigured deployment must not be able to reach it.
            throw new IllegalStateException(
                    "No OIDC issuers configured. Set watch-guru.auth.issuers[0].uri. For local "
                            + "development point it at a mock OIDC issuer; there is deliberately "
                            + "no bypass switch.");
        }
        if (properties.audiences().isEmpty()) {
            log.warn("No watch-guru.auth.audiences configured: any token from a trusted issuer "
                    + "will be accepted, including one minted for a different application. "
                    + "Set WATCH_GURU_AUTH_AUDIENCES before exposing this API.");
        }

        Map<String, AuthenticationManager> managers = new LinkedHashMap<>();
        for (AuthProperties.Issuer issuer : properties.issuers()) {
            if (properties.requireHttps() && !issuer.uri().startsWith("https://")) {
                throw new IllegalStateException(
                        "Issuer " + issuer.name() + " is not HTTPS: " + issuer.uri());
            }
            managers.put(issuer.uri(),
                    new ProviderManager(new JwtAuthenticationProvider(decoderFor(issuer, properties.audiences()))));
            log.info("Trusting OIDC issuer {} ({}); email verification trusted: {}",
                    issuer.name(), issuer.uri(), issuer.trustEmailVerification());
        }

        AuthenticationManagerResolver<String> byIssuer = managers::get;
        return new JwtIssuerAuthenticationManagerResolver(byIssuer);
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
    private JwtDecoder decoderFor(AuthProperties.Issuer issuer, List<String> audiences) {
        return new LazyJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer.uri()).build();

            OAuth2TokenValidator<Jwt> validator = audiences.isEmpty()
                    ? JwtValidators.createDefaultWithIssuer(issuer.uri())
                    : new DelegatingOAuth2TokenValidator<>(
                            JwtValidators.createDefaultWithIssuer(issuer.uri()),
                            new AudienceValidator(audiences));

            decoder.setJwtValidator(validator);
            return decoder;
        });
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,
                                    JwtIssuerAuthenticationManagerResolver issuerResolver) throws Exception {
        return http
                // No browser clients and no cookies: two native apps sending a
                // bearer token. CSRF protects cookie-borne credentials, which
                // this API does not have, and the stateless session policy is
                // what makes that true rather than merely intended.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // The spec describes the API; it is not a way into it.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Everything else, including every /api route, needs a token.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(issuerResolver))
                .build();
    }
}
