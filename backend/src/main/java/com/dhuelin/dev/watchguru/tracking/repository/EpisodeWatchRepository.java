package com.dhuelin.dev.watchguru.tracking.repository;

import com.dhuelin.dev.watchguru.tracking.domain.EpisodeWatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EpisodeWatchRepository extends JpaRepository<EpisodeWatch, Long> {

    Optional<EpisodeWatch> findByUserIdAndEpisodeId(Long userId, Long episodeId);

    List<EpisodeWatch> findByUserIdAndTitleId(Long userId, Long titleId);

    @Query("select ew.episode.id from EpisodeWatch ew where ew.user.id = :userId and ew.title.id = :titleId")
    List<Long> findWatchedEpisodeIds(@Param("userId") Long userId, @Param("titleId") Long titleId);

    long countByUserIdAndTitleId(Long userId, Long titleId);
}
