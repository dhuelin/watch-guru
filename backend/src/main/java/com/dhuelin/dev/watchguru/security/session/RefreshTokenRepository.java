package com.dhuelin.dev.watchguru.security.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * The same lookup, holding the row until the transaction ends.
     *
     * <p>Used by rotation. Without the lock, two refreshes racing with the
     * same token both read {@code usedAt == null} and both succeed -- a
     * stolen token raced against its owner yields a working session and the
     * reuse detection never fires. With it, the second waits, then sees the
     * token spent and revokes the family.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * Revokes every unrevoked token in a family.
     *
     * <p>A bulk update rather than a load-and-save loop: this runs on the reuse
     * path, where the goal is to close the window as fast as possible, and the
     * number of rows is unbounded in principle.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revokedAt = :now, t.revokedReason = :reason
             where t.familyId = :familyId
               and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId,
                     @Param("now") Instant now,
                     @Param("reason") String reason);

    /** Revokes every session a user has, used on account deletion. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revokedAt = :now, t.revokedReason = :reason
             where t.user.id = :userId
               and t.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("now") Instant now,
                         @Param("reason") String reason);

    /**
     * Drops rows that can no longer authenticate anything.
     *
     * <p>Expired and revoked tokens are kept for a grace period rather than
     * deleted on the spot: reuse detection needs to still find a token that was
     * revoked a moment ago, otherwise a replayed token looks merely unknown and
     * the family is never closed.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
