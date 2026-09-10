package com.dhuelin.dev.watchguru.security;

import com.dhuelin.dev.watchguru.security.session.AccessTokenIssuer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.LinkedHashMap;
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

    /**
     * One authentication manager per trusted issuer, selected by the token's
     * {@code iss} claim.
     *
     * <p>Built from an explicit issuer-to-manager map rather than
     * {@code JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers(String...)}.
     * That convenience method constructs its own decoders internally, which
     * would quietly discard the audience validators and leave the API accepting
     * tokens minted for other applications.
     *
     * <p>An issuer absent from the map resolves to null, and Spring Security
     * rejects the request. That is what stops a token carrying an attacker's
     * own {@code iss} from pointing this server at a JWK set they control.
     *
     * <p>The providers' issuers are still here alongside this service's own.
     * Apps are expected to exchange a provider token for a session token at
     * {@code POST /api/v1/auth/session} and use that thereafter, but a provider
     * token presented directly is still honoured -- both apps shipped that way,
     * and breaking them from the server side is not an upgrade path.
     */
    @Bean
    JwtIssuerAuthenticationManagerResolver issuerResolver(TrustedIssuers trustedIssuers,
                                                          AccessTokenIssuer accessTokens) {
        Map<String, AuthenticationManager> managers = new LinkedHashMap<>();
        trustedIssuers.decoders().forEach((issuer, decoder) -> managers.put(issuer, managerFor(decoder)));
        managers.put(accessTokens.issuerUri(), managerFor(accessTokens.decoder()));

        AuthenticationManagerResolver<String> byIssuer = managers::get;
        return new JwtIssuerAuthenticationManagerResolver(byIssuer);
    }

    private static AuthenticationManager managerFor(JwtDecoder decoder) {
        return new ProviderManager(new JwtAuthenticationProvider(decoder));
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
                        // The token endpoints carry their credential in the
                        // body, not the Authorization header, so the filter
                        // chain has nothing to check. They are not unprotected:
                        // each verifies its own credential and returns 401
                        // otherwise. Requiring a bearer token to obtain a
                        // bearer token would be circular.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/session", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                            .permitAll()
                        // Everything else, including every other /api route, needs a token.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(issuerResolver))
                .build();
    }
}
