package com.dhuelin.dev.watchguru.tracking.repository;

import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    Optional<WatchlistItem> findByUserIdAndTitleId(Long userId, Long titleId);

    @EntityGraph(attributePaths = "title")
    Page<WatchlistItem> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "title")
    Page<WatchlistItem> findByUserIdAndStatus(Long userId, WatchStatus status, Pageable pageable);

    @Query("select w.status as status, count(w) as count from WatchlistItem w where w.user.id = :userId group by w.status")
    List<StatusCount> countByStatus(@Param("userId") Long userId);

    /** Projection for the status breakdown on the stats endpoint. */
    interface StatusCount {
        WatchStatus getStatus();

        long getCount();
    }
}
