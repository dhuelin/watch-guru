package com.dhuelin.dev.watchguru.tracking.repository;

import com.dhuelin.dev.watchguru.tracking.domain.EpisodeWatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EpisodeWatchRepository extends JpaRepository<EpisodeWatch, Long> {

    Optional<EpisodeWatch> findByUserIdAndEpisodeId(Long userId, Long episodeId);

    List<EpisodeWatch> findByUserIdAndTitleId(Long userId, Long titleId);

    @Query("select ew.episode.id from EpisodeWatch ew where ew.user.id = :userId and ew.title.id = :titleId")
    List<Long> findWatchedEpisodeIds(@Param("userId") Long userId, @Param("titleId") Long titleId);

    long countByUserIdAndTitleId(Long userId, Long titleId);

    /**
     * Watched episode ids for one title, as a set for membership tests.
     *
     * <p>The episode list needs to tick rows; doing that with a query per row
     * would be an N+1 over a season.
     */
    @Query("select ew.episode.id from EpisodeWatch ew where ew.user.id = :userId and ew.title.id = :titleId")
    Set<Long> findWatchedEpisodeIdSet(@Param("userId") Long userId, @Param("titleId") Long titleId);

    /** Watch counts by episode, so a rewatch can be shown as such. */
    @Query("""
            select ew.episode.id, ew.watchCount
            from EpisodeWatch ew
            where ew.user.id = :userId and ew.title.id = :titleId
            """)
    List<Object[]> findWatchCounts(@Param("userId") Long userId, @Param("titleId") Long titleId);

    /**
     * The most recent time each series was watched, for the titles given.
     *
     * <p>Drives the Up Next ordering: the series someone is actively bingeing
     * should be first, and that is the one they watched most recently.
     */
    @Query("""
            select ew.title.id, max(ew.watchedAt)
            from EpisodeWatch ew
            where ew.user.id = :userId and ew.title.id in :titleIds
            group by ew.title.id
            """)
    List<Object[]> findLastWatchedAt(@Param("userId") Long userId,
                                     @Param("titleIds") Collection<Long> titleIds);
}
