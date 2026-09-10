package com.dhuelin.dev.watchguru.security.session;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.security.TrustedIssuers;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.support.TestAuth;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The exchange itself: a provider token in, a session of ours out.
 *
 * <p>Signed with a key generated here rather than mocked, so the signature is
 * genuinely verified. The issuer's discovery document is not reachable from a
 * test, so the decoder for the test issuer is supplied directly -- everything
 * downstream of "this token verified" is the real code path, including user
 * provisioning and the audience check.
 */
@SpringBootTest
@Testcontainers
@Import({PostgresTestConfig.class, TokenExchangeTest.MockIssuerConfig.class})
class TokenExchangeTest {

    private static final String ISSUER = "https://issuer.test";
    private static final String AUDIENCE = TestAuth.AUDIENCE;

    private static RSAKey signingKey;

    @BeforeAll
    static void generateKey() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("test").generate();
    }

    /**
     * Replaces the issuer map with one whose decoder verifies against the key
     * generated above, since no test can fetch a real JWK set.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class MockIssuerConfig {

        @Bean
        @Primary
        TrustedIssuers trustedIssuers(AuthProperties properties) {
            return new TrustedIssuers(properties) {
                private final JwtDecoder decoder = buildDecoder();

                @Override
                public Jwt verify(String token) {
                    try {
                        Jwt jwt = decoder.decode(token);
                        if (!ISSUER.equals(String.valueOf(jwt.getIssuer()))
                                || jwt.getAudience() == null
                                || !jwt.getAudience().contains(AUDIENCE)) {
                            throw new UntrustedIssuerException("Rejected");
                        }
                        return jwt;
                    } catch (JwtException e) {
                        throw new UntrustedIssuerException("Rejected");
                    }
                }

                private JwtDecoder buildDecoder() {
                    try {
                        return NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
                    } catch (JOSEException e) {
                        throw new IllegalStateException(e);
                    }
                }
            };
        }
    }

    @Autowired private SessionService sessions;
    @Autowired private AppUserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private AccessTokenIssuer accessTokens;
    @Autowired private CurrentUserService currentUser;
    @Autowired private org.springframework.transaction.support.TransactionTemplate transactions;

    /**
     * Deletes as the application does. {@code deleteAppUserById} is a modifying
     * query, so it needs a transaction; in production that comes from
     * {@code CurrentUserService.deleteCurrentUser}. A {@code @Transactional}
     * test method would roll the delete back before the assertions ran, which
     * is why this is a template rather than an annotation.
     */
    private void deleteUser(Long id) {
        transactions.executeWithoutResult(status -> users.deleteAppUserById(id));
    }

    private static String providerToken(String subject, String email, Instant expiry) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience(AUDIENCE)
                    .subject(subject)
                    .claim("email", email)
                    .claim("email_verified", true)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(expiry))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("a valid provider token provisions an account and returns a session")
    void exchangeProvisionsAndIssues() {
        String subject = "sub-" + UUID.randomUUID();
        String email = subject + "@example.test";

        SessionService.Session session =
                sessions.exchange(providerToken(subject, email, Instant.now().plus(Duration.ofMinutes(30))));

        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();

        AppUser provisioned = users.findByAuthSubject(ISSUER + "|" + subject).orElseThrow();
        assertThat(provisioned.getEmail()).isEqualTo(email);

        // The refresh token is stored against that user, hashed.
        assertThat(refreshTokens.findByTokenHash(SessionService.hash(session.refreshToken())))
                .get()
                .satisfies(stored -> assertThat(stored.getUser().getId()).isEqualTo(provisioned.getId()));
    }

    @Test
    @DisplayName("exchanging twice returns two sessions for one account")
    void exchangeIsIdempotentAboutTheAccount() {
        String subject = "sub-" + UUID.randomUUID();
        String token = providerToken(subject, subject + "@example.test",
                Instant.now().plus(Duration.ofMinutes(30)));

        SessionService.Session first = sessions.exchange(token);
        SessionService.Session second = sessions.exchange(token);

        // Signing in on a second device must not create a second account, and
        // must not invalidate the first device's session.
        assertThat(users.findAll().stream()
                .filter(u -> (ISSUER + "|" + subject).equals(u.getAuthSubject()))
                .count()).isEqualTo(1);
        assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
        assertThat(sessions.refresh(first.refreshToken())).isNotNull();
    }

    @Test
    @DisplayName("an expired provider token is refused")
    void expiredProviderTokenIsRefused() {
        assertThatThrownBy(() -> sessions.exchange(providerToken(
                "sub-expired", "expired@example.test", Instant.now().minus(Duration.ofMinutes(1)))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token that is not a JWT at all is refused")
    void garbageIsRefused() {
        assertThatThrownBy(() -> sessions.exchange("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("the issued access token resolves back to the same account")
    void accessTokenResolvesToTheUser() {
        String subject = "sub-" + UUID.randomUUID();
        sessions.exchange(providerToken(subject, subject + "@example.test",
                Instant.now().plus(Duration.ofMinutes(30))));

        AppUser user = users.findByAuthSubject(ISSUER + "|" + subject).orElseThrow();
        Jwt ours = accessTokens.decoder().decode(accessTokens.issue(user, Instant.now()).token());

        // The critical part: our own token must resolve by id and must NOT be
        // treated as a provider token, which would namespace "<our issuer>|<id>"
        // against nothing and provision a duplicate user on every request.
        assertThat(currentUser.resolve(ours).getId()).isEqualTo(user.getId());
        assertThat(users.findAll().stream()
                .filter(u -> (ISSUER + "|" + subject).equals(u.getAuthSubject()))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("an access token this service did not sign is refused")
    void foreignAccessTokenIsRefused() {
        // A provider token presented where one of ours is expected: signed by a
        // real key, but not ours, and carrying the wrong issuer.
        String provider = providerToken("sub-x", "x@example.test",
                Instant.now().plus(Duration.ofMinutes(30)));

        assertThatThrownBy(() -> accessTokens.decoder().decode(provider))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a session token for a deleted account is refused")
    void sessionTokenForDeletedAccountIsRefused() {
        String subject = "sub-" + UUID.randomUUID();
        sessions.exchange(providerToken(subject, subject + "@example.test",
                Instant.now().plus(Duration.ofMinutes(30))));

        AppUser user = users.findByAuthSubject(ISSUER + "|" + subject).orElseThrow();
        String access = accessTokens.issue(user, Instant.now()).token();
        deleteUser(user.getId());

        // The access token is still inside its lifetime and still verifies.
        // What must not happen is it silently provisioning a replacement
        // account for a user who asked to be deleted.
        Jwt stillValid = accessTokens.decoder().decode(access);
        assertThatThrownBy(() -> currentUser.resolve(stillValid))
                .isInstanceOf(CurrentUserService.SessionUserGoneException.class);
    }

    @Test
    @DisplayName("deleting an account takes its refresh tokens with it")
    void deletingAnAccountRemovesItsSessions() {
        String subject = "sub-" + UUID.randomUUID();
        SessionService.Session session = sessions.exchange(providerToken(
                subject, subject + "@example.test", Instant.now().plus(Duration.ofMinutes(30))));

        AppUser user = users.findByAuthSubject(ISSUER + "|" + subject).orElseThrow();
        deleteUser(user.getId());

        // Relies on the ON DELETE CASCADE in V5: an orphaned refresh token
        // would be a live credential for an account that no longer exists.
        assertThat(refreshTokens.findByTokenHash(SessionService.hash(session.refreshToken()))).isEmpty();
        assertThatThrownBy(() -> sessions.refresh(session.refreshToken()))
                .isInstanceOf(SessionService.InvalidSessionException.class);
    }

    @Test
    @DisplayName("a correctly signed token for another application is refused")
    void wrongAudienceIsRefused() {
        // The exchange endpoint is a second route by which provider tokens
        // reach this service, so it needs the same audience check the filter
        // chain has. Without it, a token harvested by any unrelated app that
        // also offers Google sign-in would be exchanged here for a session as
        // its owner -- correctly signed, correct issuer, wrong application.
        assertThatThrownBy(() -> sessions.exchange(tokenFor("some-other-app")))
                .isInstanceOf(JwtException.class);
    }

    private static String tokenFor(String audience) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience(audience)
                    .subject("sub-other-app")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(30))))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
