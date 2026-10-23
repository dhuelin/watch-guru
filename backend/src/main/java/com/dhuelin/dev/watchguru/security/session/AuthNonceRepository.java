package com.dhuelin.dev.watchguru.security.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuthNonceRepository extends JpaRepository<AuthNonce, String> {

    /**
     * Records a nonce as used.
     *
     * <p>One statement, so two exchanges of the same token racing each other
     * cannot both see "not used yet": exactly one insert wins.
     *
     * @return 1 if this call claimed it, 0 if it had already been used
     */
    @Modifying
    @Query(value = """
            insert into auth_nonce (nonce_hash, used_at, expires_at)
            values (:nonceHash, :now, :expiresAt)
            on conflict (nonce_hash) do nothing
            """, nativeQuery = true)
    int claim(@Param("nonceHash") String nonceHash,
              @Param("now") Instant now,
              @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("delete from AuthNonce n where n.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
