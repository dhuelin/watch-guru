package com.dhuelin.dev.watchguru.security.credentials;

import com.dhuelin.dev.watchguru.config.CredentialProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Credentials sealed with AES-GCM under a key from the deployment's
 * configuration.
 *
 * <p>AES-GCM rather than plain AES because it authenticates as well as
 * encrypts: a row somebody edited in the database fails to open rather than
 * decrypting to something subtly different. A fresh 96-bit nonce per write, as
 * GCM requires -- reusing one under the same key is the mistake that breaks
 * GCM outright, so the nonce is generated at every write and never derived
 * from anything about the row.
 *
 * <p>The key is not stored here or anywhere else in this database. Without it
 * the rows are noise, which is the entire reason for the table.
 */
@Service
public class EncryptedCredentialStore implements CredentialStore {

    private static final Logger log = LoggerFactory.getLogger(EncryptedCredentialStore.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_KEY_BYTES = 32;

    private final StoredCredentialRepository credentials;
    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;
    private final String keyId;

    public EncryptedCredentialStore(StoredCredentialRepository credentials,
                                    CredentialProperties properties) {
        this.credentials = credentials;
        this.keyId = properties.keyId();
        this.key = keyFrom(properties.secret());
    }

    /**
     * Whether this deployment can hold third-party credentials at all.
     *
     * <p>Asked before an OAuth flow starts rather than after the user has
     * authorised: refusing to begin is an explicable message, and failing after
     * Trakt has redirected them back means they granted access to something
     * that then dropped the token.
     */
    public boolean isConfigured() {
        return key != null;
    }

    @Override
    @Transactional
    public String store(String value) {
        String ref = UUID.randomUUID().toString();
        StoredCredential credential = new StoredCredential(ref);
        seal(credential, value);
        credentials.save(credential);
        return ref;
    }

    @Override
    @Transactional
    public void replace(String credentialRef, String value) {
        StoredCredential credential = credentials.findByCredentialRef(credentialRef)
                .orElseGet(() -> new StoredCredential(credentialRef));
        seal(credential, value);
        credentials.save(credential);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> read(String credentialRef) {
        if (credentialRef == null || !isConfigured()) {
            return Optional.empty();
        }
        return credentials.findByCredentialRef(credentialRef).flatMap(this::open);
    }

    @Override
    @Transactional
    public void delete(String credentialRef) {
        if (credentialRef != null) {
            credentials.deleteByCredentialRef(credentialRef);
        }
    }

    private void seal(StoredCredential credential, String value) {
        requireKey();
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            credential.setCiphertext(Base64.getEncoder().encodeToString(sealed));
            credential.setNonce(Base64.getEncoder().encodeToString(nonce));
            credential.setKeyId(keyId);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not seal a credential", e);
        }
    }

    /**
     * Opens a row, or reports that it could not be opened.
     *
     * <p>Empty rather than an exception, because every caller's answer is the
     * same: this connection no longer works and the user has to reconnect. The
     * key id is logged, since "sealed under a key this deployment no longer
     * holds" and "corrupt" look identical from here and are different problems.
     */
    private Optional<String> open(StoredCredential credential) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(credential.getNonce())));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(credential.getCiphertext()));
            return Optional.of(new String(plain, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Credential {} sealed under key '{}' could not be opened: {}",
                    credential.getCredentialRef(), credential.getKeyId(), e.toString());
            return Optional.empty();
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "No watch-guru.credentials.secret configured. Set WATCH_GURU_CREDENTIALS_SECRET to "
                            + "base64 of at least " + MIN_KEY_BYTES + " random bytes. Nothing that "
                            + "holds somebody else's access token will start without it, and there is "
                            + "no generated fallback: a key invented at boot would lose every "
                            + "connection on the next restart.");
        }
    }

    /**
     * The configured key, or null where there is none.
     *
     * <p>Null rather than a startup failure: a deployment that never connects
     * Trakt has no use for this key, and refusing to boot without it would
     * make an optional feature mandatory. What must not happen is a credential
     * stored in the clear, which {@link #requireKey()} prevents at the only
     * point it could.
     */
    private static SecretKey keyFrom(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        byte[] material;
        try {
            material = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "watch-guru.credentials.secret is not valid base64. Generate one with "
                            + "`openssl rand -base64 32`.", e);
        }
        if (material.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("watch-guru.credentials.secret decodes to "
                    + material.length + " bytes; AES-256 needs " + MIN_KEY_BYTES + ".");
        }
        return new SecretKeySpec(material, "AES");
    }
}
