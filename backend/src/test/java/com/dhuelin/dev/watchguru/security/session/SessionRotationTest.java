package com.dhuelin.dev.watchguru.security.session;

import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Refresh-token rotation, and what happens when a token is used twice.
 *
 * <p>The reuse case is the one that matters. A refresh token is a long-lived
 * credential sitting in an app's storage; the design assumption is not that it
 * can never be stolen, but that using a stolen one is detectable and costs the
 * attacker the session. These tests are what make that true rather than
 * intended.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class SessionRotationTest {

    @Autowired private SessionService sessions;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private AppUserRepository users;

    private AppUser user;

    @BeforeEach
    void setUp() {
        refreshTokens.deleteAll();
        // A fresh identity per test: the email and auth subject are both unique
        // columns, and these tests share one database rather than rolling back.
        user = users.save(newUser("rotation-" + UUID.randomUUID() + "@example.test"));
    }

    private static AppUser newUser(String email) {
        AppUser user = new AppUser(email, "Rotation Test");
        user.setAuthSubject("https://accounts.google.com|" + email);
        user.setAuthIssuer("https://accounts.google.com");
        user.setEmailVerified(true);
        return user;
    }

    @Test
    @DisplayName("a refresh returns a new pair and burns the old token")
    void refreshRotates() {
        SessionService.Session first = sessions.start(user, Instant.now());
        SessionService.Session second = sessions.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotBlank();

        // The old one is spent, not merely superseded.
        assertThatThrownBy(() -> sessions.refresh(first.refreshToken()))
                .isInstanceOf(SessionService.InvalidSessionException.class);
    }

    @Test
    @DisplayName("reusing a spent refresh token revokes the whole family")
    void reuseRevokesTheFamily() {
        SessionService.Session first = sessions.start(user, Instant.now());
        SessionService.Session second = sessions.refresh(first.refreshToken());

        // An attacker replays the token they stole before the legitimate client
        // rotated it.
        assertThatThrownBy(() -> sessions.refresh(first.refreshToken()))
                .isInstanceOf(SessionService.InvalidSessionException.class);

        // The legitimate client's current token dies with it. That is the
        // intended trade: both parties are signed out, because there is no way
        // to tell which of them is the thief.
        assertThatThrownBy(() -> sessions.refresh(second.refreshToken()))
                .isInstanceOf(SessionService.InvalidSessionException.class);

        List<RefreshToken> family = refreshTokens.findAll();
        assertThat(family).isNotEmpty();
        assertThat(family).allSatisfy(token ->
                assertThat(token.getRevokedAt()).isNotNull());
        assertThat(family).anySatisfy(token ->
                assertThat(token.getRevokedReason()).isEqualTo("reuse_detected"));
    }

    @Test
    @DisplayName("an unknown refresh token is rejected without touching anything")
    void unknownTokenIsRejected() {
        SessionService.Session live = sessions.start(user, Instant.now());

        assertThatThrownBy(() -> sessions.refresh("not-a-token-anyone-issued"))
                .isInstanceOf(SessionService.InvalidSessionException.class);

        // A guess must not cost the real session anything, or guessing becomes
        // a denial-of-service against a user whose token id an attacker cannot
        // even produce.
        assertThat(sessions.refresh(live.refreshToken())).isNotNull();
    }

    @Test
    @DisplayName("an expired refresh token is rejected")
    void expiredTokenIsRejected() {
        SessionService.Session session = sessions.start(user, Instant.now());

        RefreshToken stored = refreshTokens.findByTokenHash(SessionService.hash(session.refreshToken()))
                .orElseThrow();
        stored.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        refreshTokens.save(stored);

        assertThatThrownBy(() -> sessions.refresh(session.refreshToken()))
                .isInstanceOf(SessionService.InvalidSessionException.class);
    }

    @Test
    @DisplayName("signing out revokes the family, not just the presented token")
    void logoutRevokesTheFamily() {
        SessionService.Session first = sessions.start(user, Instant.now());
        SessionService.Session second = sessions.refresh(first.refreshToken());

        sessions.logout(second.refreshToken());

        assertThatThrownBy(() -> sessions.refresh(second.refreshToken()))
                .isInstanceOf(SessionService.InvalidSessionException.class);
        assertThat(refreshTokens.findAll()).allSatisfy(token ->
                assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    @DisplayName("signing out with an unknown token is not an error")
    void logoutIsSilentAboutUnknownTokens() {
        // A client whose token already expired should still see a clean sign
        // out, and sign-out is not a place to reveal whether a token exists.
        sessions.logout("never-issued");
    }

    @Test
    @DisplayName("the stored token is a hash, not the token")
    void tokensAreStoredHashed() {
        SessionService.Session session = sessions.start(user, Instant.now());

        assertThat(refreshTokens.findAll()).allSatisfy(stored -> {
            assertThat(stored.getTokenHash()).doesNotContain(session.refreshToken());
            assertThat(stored.getTokenHash()).hasSize(64);
        });
        // Nothing in the row can be turned back into the credential.
        assertThat(refreshTokens.findByTokenHash(session.refreshToken())).isEmpty();
    }

    @Test
    @DisplayName("two sessions for one user are independent")
    void sessionsAreIndependent() {
        // Signing out on the phone must not sign the user out on the tablet.
        SessionService.Session phone = sessions.start(user, Instant.now());
        SessionService.Session tablet = sessions.start(user, Instant.now());

        sessions.logout(phone.refreshToken());

        assertThatThrownBy(() -> sessions.refresh(phone.refreshToken()))
                .isInstanceOf(SessionService.InvalidSessionException.class);
        assertThat(sessions.refresh(tablet.refreshToken())).isNotNull();
    }

    @Test
    @DisplayName("deleting an account revokes every session it had")
    void accountDeletionRevokesSessions() {
        SessionService.Session session = sessions.start(user, Instant.now());

        sessions.revokeAllFor(user.getId(), "account_deleted");

        assertThatThrownBy(() -> sessions.refresh(session.refreshToken()))
                .isInstanceOf(SessionService.InvalidSessionException.class);
    }
}
