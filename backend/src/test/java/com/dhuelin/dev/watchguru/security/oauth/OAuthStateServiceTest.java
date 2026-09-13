package com.dhuelin.dev.watchguru.security.oauth;

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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The state parameter, which is the only thing tying the browser that comes
 * back from a provider to the user who started the flow.
 *
 * <p>Each of these is an attack if it fails: a guessable state lets somebody
 * attach their account to a stranger's library, a reusable one lets a captured
 * redirect be replayed, and one that never expires leaves an abandoned tab
 * redeemable months later.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class OAuthStateServiceTest {

    @Autowired
    private OAuthStateService states;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private OAuthStateRepository rows;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("state-" + System.nanoTime() + "@example.com", "State"));
    }

    @Test
    @DisplayName("a state names the user who started the flow")
    void redeemsOnce() {
        String state = states.issue(user.getId(), "trakt", Duration.ofMinutes(10));

        assertThat(states.redeem(state, "trakt")).contains(user.getId());
    }

    @Test
    @DisplayName("the second use of the same state gets nothing")
    void isSingleUse() {
        String state = states.issue(user.getId(), "trakt", Duration.ofMinutes(10));
        states.redeem(state, "trakt");

        assertThat(states.redeem(state, "trakt")).isEmpty();
    }

    @Test
    @DisplayName("an expired state is spent rather than honoured")
    void expires() {
        String state = states.issue(user.getId(), "trakt", Duration.ofSeconds(-1));

        assertThat(states.redeem(state, "trakt")).isEmpty();
    }

    @Test
    @DisplayName("a state issued for one provider does not open another")
    void isProviderSpecific() {
        String state = states.issue(user.getId(), "trakt", Duration.ofMinutes(10));

        assertThat(states.redeem(state, "jellyfin")).isEmpty();
        // And it is spent either way: a mismatched attempt must not leave the
        // row behind to be tried again with the right name.
        assertThat(states.redeem(state, "trakt")).isEmpty();
    }

    @Test
    @DisplayName("nothing is stored that a leaked database would let somebody redeem")
    void storesOnlyAHash() {
        String state = states.issue(user.getId(), "trakt", Duration.ofMinutes(10));

        assertThat(rows.findById(state)).isEmpty();
        assertThat(rows.findAll()).noneSatisfy(row ->
                assertThat(row.getStateHash()).isEqualTo(state));
    }

    @Test
    @DisplayName("a state nobody came back with is cleared out")
    void purgesExpired() {
        states.issue(user.getId(), "trakt", Duration.ofSeconds(-1));

        assertThat(states.purgeExpired()).isPositive();
    }

    @Test
    @DisplayName("nonsense presented as a state is simply not a state")
    void ignoresRubbish() {
        assertThat(states.redeem(null, "trakt")).isEmpty();
        assertThat(states.redeem("", "trakt")).isEmpty();
        assertThat(states.redeem("not-a-state", "trakt")).isEmpty();
    }
}
