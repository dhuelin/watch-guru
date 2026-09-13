package com.dhuelin.dev.watchguru.streaming.repository;

import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LinkedStreamingAccountRepository extends JpaRepository<LinkedStreamingAccount, Long> {

    @EntityGraph(attributePaths = "streamingService")
    List<LinkedStreamingAccount> findByUserId(Long userId);

    Optional<LinkedStreamingAccount> findByUserIdAndStreamingServiceId(Long userId, Long streamingServiceId);

    @EntityGraph(attributePaths = "streamingService")
    Optional<LinkedStreamingAccount> findByUserIdAndStreamingServiceSlug(Long userId, String slug);

    /**
     * One link, with the user and service loaded.
     *
     * <p>A webhook arrives on no session and outside any request of the user's
     * own, so the two associations a scrobble needs are fetched here rather
     * than touched later against a session that has already closed.
     */
    @EntityGraph(attributePaths = {"user", "streamingService"})
    Optional<LinkedStreamingAccount> findWithUserAndServiceById(Long id);

    /**
     * Every connection to one service that a scheduled sync should attempt.
     *
     * <p>The user and service are fetched with the row: a job runs outside any
     * request and outside any open session, so an association touched later
     * would fail rather than load.
     */
    @EntityGraph(attributePaths = {"user", "streamingService"})
    @Query("select a from LinkedStreamingAccount a where a.streamingService.slug = :slug "
            + "and a.syncEnabled = true and a.status in (:syncable)")
    List<LinkedStreamingAccount> findSyncable(@Param("slug") String slug,
                                              @Param("syncable") Collection<LinkStatus> syncable);
}
