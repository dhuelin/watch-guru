package com.dhuelin.dev.watchguru.streaming.repository;

import com.dhuelin.dev.watchguru.streaming.domain.SyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    List<SyncRun> findTop10ByLinkedAccountIdOrderByStartedAtDesc(Long linkedAccountId);
}
