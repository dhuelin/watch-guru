package com.dhuelin.dev.watchguru.security.credentials;

import com.dhuelin.dev.watchguru.config.CredentialProperties;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * That a stored access token is unreadable without the deployment's key.
 *
 * <p>This is the test that has to hold for Trakt to be safe to offer at all:
 * the schema has always promised that {@code credential_ref} points at a
 * secret rather than holding one, and until #39 nothing wrote a credential to
 * check that promise against.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
@TestPropertySource(properties =
        "watch-guru.credentials.secret=c2l4dGVlbmJ5dGVzc2l4dGVlbmJ5dGVzc2l4dGVlbmI=")
class CredentialStoreTest {

    @Autowired
    private EncryptedCredentialStore store;
    @Autowired
    private StoredCredentialRepository rows;

    @Test
    @DisplayName("what goes in comes back out")
    void roundTrips() {
        String ref = store.store("{\"accessToken\":\"abc\"}");

        assertThat(store.read(ref)).contains("{\"accessToken\":\"abc\"}");
    }

    @Test
    @DisplayName("the row holds no part of the value in the clear")
    void storesNothingReadable() {
        String ref = store.store("a-very-secret-access-token");

        StoredCredential row = rows.findByCredentialRef(ref).orElseThrow();
        assertThat(row.getCiphertext()).doesNotContain("a-very-secret-access-token");
        assertThat(row.getNonce()).isNotBlank();
        assertThat(row.getKeyId()).isEqualTo("primary");
    }

    @Test
    @DisplayName("two writes of the same value look different")
    void usesAFreshNonceEveryTime() {
        // A fresh nonce per write is what GCM requires; reusing one under the
        // same key breaks it outright. Identical ciphertext would be the
        // visible symptom of getting that wrong.
        String first = store.store("same value");
        String second = store.store("same value");

        assertThat(rows.findByCredentialRef(first).orElseThrow().getCiphertext())
                .isNotEqualTo(rows.findByCredentialRef(second).orElseThrow().getCiphertext());
    }

    @Test
    @DisplayName("a row somebody edited fails to open rather than opening to something else")
    void detectsTampering() {
        String ref = store.store("original");
        StoredCredential row = rows.findByCredentialRef(ref).orElseThrow();
        byte[] sealed = Base64.getDecoder().decode(row.getCiphertext());
        sealed[0] ^= 0x01;
        row.setCiphertext(Base64.getEncoder().encodeToString(sealed));
        rows.save(row);

        // AES-GCM authenticates as well as encrypts, which is why this is
        // empty rather than plausible-looking rubbish.
        assertThat(store.read(ref)).isEmpty();
    }

    @Test
    @DisplayName("replacing keeps the reference, so the link that points at it still works")
    void replaceKeepsTheReference() {
        String ref = store.store("first");

        store.replace(ref, "second");

        assertThat(store.read(ref)).contains("second");
        assertThat(rows.findByCredentialRef(ref)).isPresent();
    }

    @Test
    @DisplayName("deleting forgets it, twice over if asked")
    void deleteIsIdempotent() {
        String ref = store.store("gone soon");

        store.delete(ref);
        store.delete(ref);

        assertThat(store.read(ref)).isEmpty();
        assertThat(rows.findByCredentialRef(ref)).isEmpty();
    }

    @Test
    @DisplayName("an unknown reference is empty, not an error")
    void unknownReferenceIsEmpty() {
        assertThat(store.read("no-such-ref")).isEmpty();
        assertThat(store.read(null)).isEmpty();
    }

    /** Without a key, nothing is stored at all -- rather than stored in the clear. */
    @SpringBootTest
    @Testcontainers
    @Import(PostgresTestConfig.class)
    @TestPropertySource(properties = "watch-guru.credentials.secret=")
    static class WithoutAKey {

        @Autowired
        private EncryptedCredentialStore store;

        @Test
        @DisplayName("storing refuses, and reading is simply empty")
        void refusesToStore() {
            assertThat(store.isConfigured()).isFalse();
            assertThatThrownBy(() -> store.store("anything"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("WATCH_GURU_CREDENTIALS_SECRET");
            assertThat(store.read("whatever")).isEqualTo(Optional.empty());
        }
    }
}
