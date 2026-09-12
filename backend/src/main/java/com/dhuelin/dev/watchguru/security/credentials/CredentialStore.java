package com.dhuelin.dev.watchguru.security.credentials;

import java.util.Optional;

/**
 * Where a third party's credentials for one user are kept.
 *
 * <p>An interface with one implementation today, and deliberately so: the
 * schema has always described {@code credential_ref} as pointing at "an entry
 * in a secret manager", and a deployment that has one should be able to
 * provide it here without any caller changing. Callers hold a reference and
 * never the secret itself.
 */
public interface CredentialStore {

    /** Seals a value under a fresh reference and returns it. */
    String store(String value);

    /** Replaces what a reference points at, keeping the reference. */
    void replace(String credentialRef, String value);

    /**
     * @return the value, or empty when the reference is unknown or cannot be
     *         opened with the key this deployment holds
     */
    Optional<String> read(String credentialRef);

    /** Forgets a credential. Idempotent. */
    void delete(String credentialRef);
}
