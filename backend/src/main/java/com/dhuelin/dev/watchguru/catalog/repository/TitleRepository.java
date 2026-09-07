package com.dhuelin.dev.watchguru.catalog.repository;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TitleRepository extends JpaRepository<Title, Long> {

    Optional<Title> findByTmdbIdAndTitleType(Long tmdbId, TitleType titleType);

    Optional<Title> findByImdbId(String imdbId);

    @Query("""
            select t from Title t
            where lower(t.primaryTitle) like lower(concat('%', :term, '%'))
               or lower(t.originalTitle) like lower(concat('%', :term, '%'))
            """)
    Page<Title> searchCached(@Param("term") String term, Pageable pageable);
}
