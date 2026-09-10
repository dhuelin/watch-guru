package com.dhuelin.dev.watchguru.notifications.repository;

import com.dhuelin.dev.watchguru.notifications.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserIdAndTitleId(Long userId, Long titleId);

    List<NotificationPreference> findByUserId(Long userId);

    /**
     * Titles this user has explicitly muted.
     *
     * <p>Only the "off" rows, because that is the whole question the scan
     * asks: everything followed is on unless it appears here.
     */
    @Query("""
            select p.title.id from NotificationPreference p
            where p.user.id = :userId and p.newEpisodes = false
            """)
    Set<Long> findMutedTitleIds(@Param("userId") Long userId);
}
