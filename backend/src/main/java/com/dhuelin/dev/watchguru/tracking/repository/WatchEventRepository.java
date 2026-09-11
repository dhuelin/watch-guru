package com.dhuelin.dev.watchguru.tracking.repository;

import com.dhuelin.dev.watchguru.tracking.domain.WatchEvent;
import com.dhuelin.dev.watchguru.tracking.domain.WatchOrigin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Queries over the append-only viewing history.
 *
 * <p>Aggregates are expressed in HQL rather than native SQL so they stay
 * portable; the multi-column groupings return {@code Object[]} and are shaped
 * into DTOs by the stats service.
 */
public interface WatchEventRepository extends JpaRepository<WatchEvent, Long> {

    @EntityGraph(attributePaths = {"title", "episode"})
    Page<WatchEvent> findByUserIdOrderByWatchedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"title", "episode"})
    List<WatchEvent> findByUserIdAndTitleIdOrderByWatchedAtDesc(Long userId, Long titleId);

    boolean existsByUserIdAndTitleIdAndEpisodeIsNull(Long userId, Long titleId);

    /**
     * Every history entry for one episode.
     *
     * <p>Unmarking removes these along with the current-state row. The history
     * is what the derived tables are rebuilt from, so leaving events behind for
     * an episode the user says they never watched would make the two disagree
     * the moment anything recomputed.
     */
    List<WatchEvent> findByUserIdAndEpisodeId(Long userId, Long episodeId);

    /**
     * Aggregates below take a {@code from} instant, and null means all time.
     *
     * <p>Written as {@code (cast(:from as Instant) is null or e.watchedAt >= :from)} rather
     * than as two queries each: the alternative is a second copy of every
     * aggregate in this file, and the copies drift.
     */
    @Query("""
            select coalesce(sum(e.minutesWatched), 0) from WatchEvent e
            where e.user.id = :userId and (cast(:from as Instant) is null or e.watchedAt >= :from)
            """)
    long totalMinutesWatched(@Param("userId") Long userId, @Param("from") Instant from);

    @Query("""
            select coalesce(sum(e.minutesWatched), 0) from WatchEvent e
            where e.user.id = :userId and e.watchedAt >= :from
            """)
    long minutesWatchedSince(@Param("userId") Long userId, @Param("from") Instant from);

    @Query("""
            select count(e) from WatchEvent e
            where e.user.id = :userId and e.episode is null
              and (cast(:from as Instant) is null or e.watchedAt >= :from)
            """)
    long countMovieViewings(@Param("userId") Long userId, @Param("from") Instant from);

    @Query("""
            select count(e) from WatchEvent e
            where e.user.id = :userId and e.episode is not null
              and (cast(:from as Instant) is null or e.watchedAt >= :from)
            """)
    long countEpisodeViewings(@Param("userId") Long userId, @Param("from") Instant from);

    @Query("""
            select count(distinct e.title.id) from WatchEvent e
            where e.user.id = :userId and (cast(:from as Instant) is null or e.watchedAt >= :from)
            """)
    long countDistinctTitles(@Param("userId") Long userId, @Param("from") Instant from);

    @Query("select min(e.watchedAt) from WatchEvent e where e.user.id = :userId")
    Instant firstWatchedAt(@Param("userId") Long userId);

    @Query("select max(e.watchedAt) from WatchEvent e where e.user.id = :userId")
    Instant lastWatchedAt(@Param("userId") Long userId);

    /** Rows of {@code [year, month, viewings, minutes]}, oldest first. */
    @Query("""
            select year(e.watchedAt), month(e.watchedAt), count(e), coalesce(sum(e.minutesWatched), 0)
            from WatchEvent e
            where e.user.id = :userId and e.watchedAt >= :from
            group by year(e.watchedAt), month(e.watchedAt)
            order by year(e.watchedAt), month(e.watchedAt)
            """)
    List<Object[]> monthlyTotals(@Param("userId") Long userId, @Param("from") Instant from);

    /** Rows of {@code [genreName, viewings, minutes]}, largest first. */
    @Query("""
            select g.name, count(e), coalesce(sum(e.minutesWatched), 0)
            from WatchEvent e join e.title t join t.genres g
            where e.user.id = :userId and (cast(:from as Instant) is null or e.watchedAt >= :from)
            group by g.name
            order by coalesce(sum(e.minutesWatched), 0) desc
            """)
    List<Object[]> totalsByGenre(@Param("userId") Long userId, @Param("from") Instant from);

    /** Rows of {@code [serviceName, viewings, minutes]} for events with a known service. */
    @Query("""
            select s.name, count(e), coalesce(sum(e.minutesWatched), 0)
            from WatchEvent e join e.streamingService s
            where e.user.id = :userId and (cast(:from as Instant) is null or e.watchedAt >= :from)
            group by s.name
            order by coalesce(sum(e.minutesWatched), 0) desc
            """)
    List<Object[]> totalsByService(@Param("userId") Long userId, @Param("from") Instant from);

    /** Rows of {@code [titleId, primaryTitle, viewings, minutes]} for the heaviest-watched titles. */
    @Query("""
            select t.id, t.primaryTitle, count(e), coalesce(sum(e.minutesWatched), 0)
            from WatchEvent e join e.title t
            where e.user.id = :userId and (cast(:from as Instant) is null or e.watchedAt >= :from)
            group by t.id, t.primaryTitle
            order by coalesce(sum(e.minutesWatched), 0) desc
            """)
    List<Object[]> topTitles(@Param("userId") Long userId, @Param("from") Instant from, Pageable pageable);

    /**
     * Distinct local dates on which the user watched something, newest first.
     *
     * <p>Never scoped to a period: a streak is a fact about the whole history,
     * and "your longest streak this month" is a different and much less
     * interesting number than the one people mean.
     */
    @Query(value = """
            select distinct cast(e.watched_at at time zone :zone as date) as day
            from watch_event e
            where e.user_id = :userId
            order by day desc
            """, nativeQuery = true)
    List<java.time.LocalDate> distinctWatchDays(@Param("userId") Long userId, @Param("zone") String zone);

    /**
     * Which of these import references this user already has.
     *
     * <p>Asked once for a whole file rather than per row: an import is
     * thousands of rows, and the unique index makes the second write a failure
     * rather than a duplicate, so knowing up front turns "error" into
     * "already imported" in the summary the user reads.
     */
    @Query("""
            select e.originRef from WatchEvent e
            where e.user.id = :userId and e.origin = :origin and e.originRef in :refs
            """)
    Set<String> findExistingOriginRefs(@Param("userId") Long userId,
                                       @Param("origin") WatchOrigin origin,
                                       @Param("refs") Collection<String> refs);
}
