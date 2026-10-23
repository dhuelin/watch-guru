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
@TestPropertySource(properties = "watch-guru.credentials.secret=" + CredentialStoreTest.PRIMARY)
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
    @DisplayName("a sealed value copied onto another row does not open there")
    void bindsTheValueToItsRow() {
        String victim = store.store("victim's token");
        String attacker = store.store("attacker's token");
        StoredCredential from = rows.findByCredentialRef(victim).orElseThrow();
        StoredCredential to = rows.findByCredentialRef(attacker).orElseThrow();
        to.setCiphertext(from.getCiphertext());
        to.setNonce(from.getNonce());
        rows.save(to);

        // Somebody with write access to the table but no key could otherwise
        // move one user's Trakt tokens onto another user's link.
        assertThat(store.read(attacker)).isEmpty();
        assertThat(store.read(victim)).contains("victim's token");
    }

    @Test
    @DisplayName("a row in the original format still opens, and is moved to the current one")
    void resealsTheOriginalFormat() {
        String ref = "legacy-" + java.util.UUID.randomUUID();
        rows.save(sealedTheOldWay(ref, "old token", PRIMARY, "primary"));

        assertThat(store.read(ref)).contains("old token");

        store.resealOutdated();

        StoredCredential row = rows.findByCredentialRef(ref).orElseThrow();
        assertThat(row.getFormatVersion()).isEqualTo(EncryptedCredentialStore.CURRENT_FORMAT);
        assertThat(store.read(ref)).contains("old token");
    }

    @Test
    @DisplayName("a reseal does not put back a value that was replaced after it was read")
    void resealLosesToAConcurrentWrite() {
        String ref = "legacy-" + java.util.UUID.randomUUID();
        StoredCredential stale = rows.save(sealedTheOldWay(ref, "spent refresh token", PRIMARY, "primary"));
        store.replace(ref, "fresh refresh token");

        // The conditional update is keyed on the ciphertext the reseal read;
        // a renewal in between changed it, so nothing is written.
        assertThat(rows.reseal(stale.getId(), stale.getCiphertext(), "x", "y", "primary", 1)).isZero();
        assertThat(store.read(ref)).contains("fresh refresh token");
    }

    @Test
    @DisplayName("an unknown reference is empty, not an error")
    void unknownReferenceIsEmpty() {
        assertThat(store.read("no-such-ref")).isEmpty();
        assertThat(store.read(null)).isEmpty();
    }

    static final String PRIMARY = "c2l4dGVlbmJ5dGVzc2l4dGVlbmJ5dGVzc2l4dGVlbmI=";
    static final String SECOND = "c2Vjb25ka2V5c2Vjb25ka2V5c2Vjb25ka2V5c2Vjb24=";

    /** A row as every deployment wrote it before format 1: no associated data. */
    static StoredCredential sealedTheOldWay(String ref, String value, String secret, String keyId) {
        try {
            byte[] nonce = new byte[12];
            new java.security.SecureRandom().nextBytes(nonce);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(secret), "AES"),
                    new javax.crypto.spec.GCMParameterSpec(128, nonce));
            StoredCredential row = new StoredCredential(ref);
            row.setCiphertext(Base64.getEncoder().encodeToString(
                    cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            row.setNonce(Base64.getEncoder().encodeToString(nonce));
            row.setKeyId(keyId);
            row.setFormatVersion(0);
            return row;
        } catch (java.security.GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    /** After a rotation: a new current key, and the old one kept only to open rows. */
    @SpringBootTest
    @Testcontainers
    @Import(PostgresTestConfig.class)
    @TestPropertySource(properties = {
            "watch-guru.credentials.secret=" + SECOND,
            "watch-guru.credentials.key-id=second",
            "watch-guru.credentials.retired-keys=primary:" + PRIMARY})
    static class AfterARotation {

        @Autowired
        private EncryptedCredentialStore store;
        @Autowired
        private StoredCredentialRepository rows;

        @Test
        @DisplayName("rows under the retired key open, and are moved onto the new one")
        void movesRowsOntoTheNewKey() {
            String ref = "rotated-" + java.util.UUID.randomUUID();
            rows.save(sealedTheOldWay(ref, "still mine", PRIMARY, "primary"));

            assertThat(store.read(ref)).contains("still mine");

            EncryptedCredentialStore.ResealResult result = store.resealOutdated();

            assertThat(result.resealed()).isGreaterThanOrEqualTo(1);
            StoredCredential row = rows.findByCredentialRef(ref).orElseThrow();
            assertThat(row.getKeyId()).isEqualTo("second");
            assertThat(store.read(ref)).contains("still mine");
        }

        @Test
        @DisplayName("a row under a key nobody configured is left alone and counted")
        void leavesUnknownKeysAlone() {
            String ref = "orphan-" + java.util.UUID.randomUUID();
            rows.save(sealedTheOldWay(ref, "lost", SECOND, "long-gone"));

            assertThat(store.read(ref)).isEmpty();
            assertThat(store.resealOutdated().unreadable()).isGreaterThanOrEqualTo(1);
            assertThat(rows.findByCredentialRef(ref).orElseThrow().getKeyId()).isEqualTo("long-gone");
        }
    }

    @Test
    @DisplayName("a retired key that cannot be read stops the boot rather than every connection")
    void rejectsMalformedRetiredKeys() {
        assertThatThrownBy(() -> EncryptedCredentialStore.retiredKeysFrom("no-colon", "primary"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EncryptedCredentialStore.retiredKeysFrom("primary:" + PRIMARY, "primary"))
                .hasMessageContaining("current key-id");
        assertThatThrownBy(() -> EncryptedCredentialStore.retiredKeysFrom("old:c2hvcnQ=", "primary"))
                .hasMessageContaining("bytes");
        assertThat(EncryptedCredentialStore.retiredKeysFrom(" old:" + PRIMARY + " , ", "primary"))
                .containsOnlyKeys("old");
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
