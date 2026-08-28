package com.dhuelin.dev.watchguru.catalog.repository;

import com.dhuelin.dev.watchguru.catalog.domain.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    Optional<Genre> findByTmdbId(Long tmdbId);
}
