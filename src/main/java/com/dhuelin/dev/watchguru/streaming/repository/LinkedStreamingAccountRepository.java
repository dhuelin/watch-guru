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
}
