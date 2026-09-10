package com.dhuelin.dev.watchguru.provider.model;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lightweight title as returned by a search. Enough to render a result row and
 * to create a catalog stub, but not a full detail record.
 */
public record ProviderTitleSummary(
        Long providerId,
        TitleType titleType,
        String title,
        String originalTitle,
        LocalDate releaseDate,
        String overview,
        String posterPath,
        String backdropPath,
        String originalLanguage,
        BigDecimal voteAverage,
        Integer voteCount,
        BigDecimal popularity,
        boolean adult,
        List<Long> genreProviderIds
) {
}
