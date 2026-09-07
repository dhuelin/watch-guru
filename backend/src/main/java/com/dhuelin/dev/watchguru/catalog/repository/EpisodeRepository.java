package com.dhuelin.dev.watchguru.catalog.repository;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
