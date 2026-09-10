package com.dhuelin.dev.watchguru.catalog.repository;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dhuelin.dev.watchguru.tracking.service.SeriesProgressCounts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    Optional<Episode> findBySeasonIdAndEpisodeNumber(Long seasonId, Integer episodeNumber);

    List<Episode> findByTitleIdOrderBySeasonNumberAscEpisodeNumberAsc(Long titleId);

    /**
     * Episodes that have already aired. Unaired episodes must not count towards
     * "seasons remaining" or completion percentages.
     */
    @Query("""
            select e from Episode e
            where e.title.id = :titleId
              and e.airDate is not null
              and e.airDate <= current_date
            order by e.seasonNumber asc, e.episodeNumber asc
            """)
    List<Episode> findAiredByTitleId(@Param("titleId") Long titleId);

    @Query("""
            select count(e) from Episode e
            where e.title.id = :titleId
              and e.airDate is not null
              and e.airDate <= current_date
            """)
    long countAiredByTitleId(@Param("titleId") Long titleId);

    /**
     * Aired and watched counts for several series in one query.
     *
     * <p>This exists so a page of library rows costs one query rather than one
     * per row. Season 0 is excluded from both counts: specials are optional
     * viewing, and counting them would leave someone who has seen every real
     * episode looking permanently unfinished.
     *
     * <p>The left join is deliberate -- a series with nothing watched must
     * still come back with a zero rather than be missing from the result, or
     * the caller has to distinguish "no row" from "zero" for every title.
     */
    @Query("""
            select new com.dhuelin.dev.watchguru.tracking.service.SeriesProgressCounts(
                       e.title.id,
                       count(e.id),
                       count(ew.id))
            from Episode e
            left join EpisodeWatch ew
                   on ew.episode.id = e.id
                  and ew.user.id = :userId
            where e.title.id in :titleIds
              and e.seasonNumber > 0
              and e.airDate is not null
              and e.airDate <= current_date
            group by e.title.id
            """)
    List<SeriesProgressCounts> progressForTitles(@Param("userId") Long userId,
                                                 @Param("titleIds") Collection<Long> titleIds);

    /**
     * Aired episodes up to and including a given one, in broadcast order.
     *
     * <p>Backs "mark all up to here". Ordering by (season, episode) rather than
     * air date is correct: air dates are frequently missing or wrong in the
     * catalogue, and users think in season and episode numbers.
     */
    @Query("""
            select e from Episode e
            where e.title.id = :titleId
              and e.seasonNumber > 0
              and e.airDate is not null
              and e.airDate <= current_date
              and (e.seasonNumber < :seasonNumber
                   or (e.seasonNumber = :seasonNumber and e.episodeNumber <= :episodeNumber))
            order by e.seasonNumber asc, e.episodeNumber asc
            """)
    List<Episode> findAiredUpTo(@Param("titleId") Long titleId,
                                @Param("seasonNumber") Integer seasonNumber,
                                @Param("episodeNumber") Integer episodeNumber);
}
