package com.dhuelin.dev.watchguru.streaming.repository;

import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
