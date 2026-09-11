package com.dhuelin.dev.watchguru.notifications.repository;

import com.dhuelin.dev.watchguru.notifications.domain.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    boolean existsByUserIdAndEpisodeId(Long userId, Long episodeId);

    /** Which of these episodes this user has already been told about. */
    @Query("""
            select d.episode.id from NotificationDelivery d
            where d.user.id = :userId and d.episode.id in :episodeIds
            """)
    Set<Long> findNotifiedEpisodeIds(@Param("userId") Long userId,
                                     @Param("episodeIds") Collection<Long> episodeIds);

    /**
     * How many notifications this user has had since [since]; the daily cap.
     *
     * <p>Distinct batches, not rows. A three-episode announcement is one
     * notification to the person receiving it, and counting its rows would
     * spend three days of allowance on one buzz.
     */
    @Query("""
            select count(distinct d.batchId) from NotificationDelivery d
            where d.user.id = :userId and d.createdAt > :since
            """)
    long countNotificationsSince(@Param("userId") Long userId, @Param("since") Instant since);

    /** Releases a claim whose push never reached anybody. */
    void deleteByBatchId(UUID batchId);
}
