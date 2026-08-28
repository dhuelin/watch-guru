package com.dhuelin.dev.watchguru.provider.model;

import java.time.LocalDate;

public record ProviderSeasonSummary(
        Long providerId,
        Integer seasonNumber,
        String name,
        String overview,
        LocalDate airDate,
        Integer episodeCount,
        String posterPath
) {
}
