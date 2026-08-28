package com.dhuelin.dev.watchguru.catalog.repository;

import com.dhuelin.dev.watchguru.catalog.domain.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    Optional<Season> findByTitleIdAndSeasonNumber(Long titleId, Integer seasonNumber);

    List<Season> findByTitleIdOrderBySeasonNumberAsc(Long titleId);
}
