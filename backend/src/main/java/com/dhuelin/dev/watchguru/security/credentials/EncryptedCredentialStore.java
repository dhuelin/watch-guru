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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p>Each row is sealed with its own {@code credential_ref} as associated
 * data. Without that, a sealed value is a free-floating blob: somebody who can
 * write to this table but holds no key could still copy one user's Trakt
 * tokens onto another user's row, and it would open there perfectly. With it,
 * the copy fails to authenticate.
 *
 * <p>Keys rotate. Every write uses the current key, and a row is opened with
 * whichever key its {@code key_id} names -- the current one or a retired one
 * still in configuration. {@link #resealOutdated()} moves rows still under a
 * retired key, or in the original format, onto the current key and format, so
 * a retired key can eventually be dropped without losing anybody's
 * connection.
 *
 * <p>The keys are not stored here or anywhere else in this database. Without
 * them the rows are noise, which is the entire reason for the table.
 */
@Service
public class EncryptedCredentialStore implements CredentialStore {

    private static final Logger log = LoggerFactory.getLogger(EncryptedCredentialStore.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_KEY_BYTES = 32;

    /** The original format: no associated data. Still opened, never written. */
    static final int FORMAT_UNBOUND = 0;
    /** Sealed with the row's {@code credential_ref} as associated data. */
    static final int FORMAT_BOUND_TO_REF = 1;
    static final int CURRENT_FORMAT = FORMAT_BOUND_TO_REF;

    private static final String AAD_PREFIX = "watch-guru/streaming_credential/v1/";

    private final StoredCredentialRepository credentials;
    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;
    private final String keyId;
    /** Keys that open old rows and seal nothing new, by id. */
    private final Map<String, SecretKey> retired;

    public EncryptedCredentialStore(StoredCredentialRepository credentials,
                                    CredentialProperties properties) {
        this.credentials = credentials;
        this.keyId = properties.keyId();
        this.key = keyFrom(properties.secret(), "watch-guru.credentials.secret");
        this.retired = retiredKeysFrom(properties.retiredKeys(), keyId);
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

    /**
     * Moves every row still under a retired key, or still in the original
     * format, onto the current key and format.
     *
     * <p>Run at startup and daily rather than as a Flyway migration, because a
     * migration has no access to the keys -- which is the point of keeping them
     * out of the database. A row that cannot be opened with any key this
     * deployment holds is left alone and counted: deleting it would hide the
     * problem, and the user's next sync already reports the connection as
     * needing to be made again.
     *
     * @return what was done, for the log
     */
    @Transactional
    public ResealResult resealOutdated() {
        if (!isConfigured()) {
            return new ResealResult(0, 0);
        }
        int resealed = 0;
        int unreadable = 0;
        long cursor = 0;
        List<StoredCredential> batch;
        do {
            batch = credentials.findTop100ByIdGreaterThanOrderByIdAsc(cursor);
            for (StoredCredential row : batch) {
                cursor = row.getId();
                if (isCurrent(row)) {
                    continue;
                }
                Optional<String> value = open(row);
                if (value.isEmpty()) {
                    unreadable++;
                    continue;
                }
                Sealed fresh = sealValue(row.getCredentialRef(), value.get());
                resealed += credentials.reseal(row.getId(), row.getCiphertext(),
                        fresh.ciphertext(), fresh.nonce(), keyId, CURRENT_FORMAT);
            }
        } while (!batch.isEmpty());

        if (resealed > 0 || unreadable > 0) {
            log.info("Resealed {} credential(s) under key '{}'; {} could not be opened with any "
                    + "configured key", resealed, keyId, unreadable);
        }
        return new ResealResult(resealed, unreadable);
    }

    /** What {@link #resealOutdated()} did. */
    public record ResealResult(int resealed, int unreadable) {
    }

    private boolean isCurrent(StoredCredential row) {
        return keyId.equals(row.getKeyId()) && row.getFormatVersion() == CURRENT_FORMAT;
    }

    private void seal(StoredCredential credential, String value) {
        Sealed sealed = sealValue(credential.getCredentialRef(), value);
        credential.setCiphertext(sealed.ciphertext());
        credential.setNonce(sealed.nonce());
        credential.setKeyId(keyId);
        credential.setFormatVersion(CURRENT_FORMAT);
    }

    private Sealed sealValue(String credentialRef, String value) {
        requireKey();
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData(credentialRef));
            byte[] sealed = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return new Sealed(Base64.getEncoder().encodeToString(sealed),
                    Base64.getEncoder().encodeToString(nonce));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not seal a credential", e);
        }
    }

    private record Sealed(String ciphertext, String nonce) {
    }

    private static byte[] associatedData(String credentialRef) {
        return (AAD_PREFIX + credentialRef).getBytes(StandardCharsets.UTF_8);
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
        SecretKey opening = keyId.equals(credential.getKeyId()) ? key : retired.get(credential.getKeyId());
        if (opening == null) {
            log.warn("Credential {} is sealed under key '{}', which this deployment does not hold",
                    credential.getCredentialRef(), credential.getKeyId());
            return Optional.empty();
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, opening,
                    new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(credential.getNonce())));
            switch (credential.getFormatVersion()) {
                case FORMAT_UNBOUND -> { }
                case FORMAT_BOUND_TO_REF -> cipher.updateAAD(associatedData(credential.getCredentialRef()));
                default -> throw new IllegalArgumentException(
                        "unknown format " + credential.getFormatVersion());
            }
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
    private static SecretKey keyFrom(String secret, String name) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        byte[] material;
        try {
            material = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    name + " is not valid base64. Generate one with `openssl rand -base64 32`.", e);
        }
        if (material.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(name + " decodes to "
                    + material.length + " bytes; AES-256 needs " + MIN_KEY_BYTES + ".");
        }
        return new SecretKeySpec(material, "AES");
    }

    /**
     * Parses {@code id:base64,id:base64}.
     *
     * <p>Strict, because a typo here is found out at the worst time: a retired
     * key that silently failed to load means every row still under it stops
     * opening, which looks to each user like Trakt forgetting them. Failing at
     * boot names the problem instead.
     */
    static Map<String, SecretKey> retiredKeysFrom(String value, String currentKeyId) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        Map<String, SecretKey> keys = new HashMap<>();
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0 || colon == trimmed.length() - 1) {
                throw new IllegalStateException("watch-guru.credentials.retired-keys entries must "
                        + "look like id:base64, separated by commas.");
            }
            String id = trimmed.substring(0, colon).trim();
            if (id.equals(currentKeyId)) {
                throw new IllegalStateException("watch-guru.credentials.retired-keys contains '" + id
                        + "', which is also the current key-id. Give the new key a new id.");
            }
            if (keys.put(id, keyFrom(trimmed.substring(colon + 1),
                    "watch-guru.credentials.retired-keys '" + id + "'")) != null) {
                throw new IllegalStateException(
                        "watch-guru.credentials.retired-keys names '" + id + "' twice.");
            }
        }
        return Map.copyOf(keys);
    }
}
