package com.dhuelin.dev.watchguru.streaming.plex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The credential in a webhook URL. */
class WebhookTokenTest {

    @Test
    @DisplayName("a token names its link and verifies against its own hash")
    void roundTrips() {
        String token = WebhookToken.issue(42L);

        assertThat(WebhookToken.accountId(token)).contains(42L);
        assertThat(WebhookToken.matches(token, WebhookToken.hash(token))).isTrue();
    }

    @Test
    @DisplayName("two issued tokens never collide")
    void isRandom() {
        assertThat(WebhookToken.issue(1L)).isNotEqualTo(WebhookToken.issue(1L));
    }

    @Test
    @DisplayName("a token for the right link with the wrong secret does not verify")
    void rejectsAForgedSecret() {
        String real = WebhookToken.issue(7L);
        String forged = "7." + WebhookToken.issue(7L).split("\\.")[1];

        assertThat(WebhookToken.accountId(forged)).contains(7L);
        assertThat(WebhookToken.matches(forged, WebhookToken.hash(real))).isFalse();
    }

    @Test
    @DisplayName("the hash is of the secret, so it cannot be recomputed from the URL alone")
    void storesOnlyAHash() {
        String token = WebhookToken.issue(3L);
        String hash = WebhookToken.hash(token);

        assertThat(hash).hasSize(64).doesNotContain(token.split("\\.")[1]);
    }

    @Test
    @DisplayName("nonsense in the path is not a link id")
    void rejectsMalformedTokens() {
        assertThat(WebhookToken.accountId(null)).isEmpty();
        assertThat(WebhookToken.accountId("")).isEmpty();
        assertThat(WebhookToken.accountId("no-dot")).isEmpty();
        assertThat(WebhookToken.accountId(".secret")).isEmpty();
        assertThat(WebhookToken.accountId("abc.secret")).isEmpty();
        assertThat(WebhookToken.matches("abc.secret", null)).isFalse();
    }
}
