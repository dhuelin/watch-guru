package com.dhuelin.dev.watchguru.notifications.repository;

import com.dhuelin.dev.watchguru.notifications.domain.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    boolean existsByUserIdAndEpisodeId(Long userId, Long episodeId);

    /** Which of these episodes this user has already been told about. */
    @Query("""
            select d.episode.id from NotificationDelivery d
            where d.user.id = :userId and d.episode.id in :episodeIds
            """)
    Set<Long> findNotifiedEpisodeIds(@Param("userId") Long userId,
                                     @Param("episodeIds") Collection<Long> episodeIds);

    /** How many notifications this user has had since [since]; the daily cap. */
    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);
}
