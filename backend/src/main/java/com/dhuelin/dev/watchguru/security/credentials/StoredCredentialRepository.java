package com.dhuelin.dev.watchguru.security.credentials;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface StoredCredentialRepository extends JpaRepository<StoredCredential, Long> {

    Optional<StoredCredential> findByCredentialRef(String credentialRef);

    void deleteByCredentialRef(String credentialRef);

    /** Walks the table by id, so a row that cannot be resealed is not met twice. */
    List<StoredCredential> findTop100ByIdGreaterThanOrderByIdAsc(Long afterId);

    /**
     * Writes a resealed row, but only if nobody has written it since it was read.
     *
     * <p>Conditional on the old ciphertext because a reseal and a token renewal
     * can race: the renewal stores a new refresh token, and an unconditional
     * reseal of the value read a moment earlier would put back the one Trakt has
     * just spent. Zero rows updated means the renewal won, which is the right
     * outcome -- its value is already sealed under the current key.
     */
    @Modifying
    @Transactional
    @Query("""
            update StoredCredential c
               set c.ciphertext = :ciphertext, c.nonce = :nonce, c.keyId = :keyId,
                   c.formatVersion = :formatVersion, c.updatedAt = CURRENT_TIMESTAMP
             where c.id = :id and c.ciphertext = :previousCiphertext
            """)
    int reseal(@Param("id") Long id,
               @Param("previousCiphertext") String previousCiphertext,
               @Param("ciphertext") String ciphertext,
               @Param("nonce") String nonce,
               @Param("keyId") String keyId,
               @Param("formatVersion") int formatVersion);
}
