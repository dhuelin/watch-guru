package com.dhuelin.dev.watchguru.provider.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderEpisode(
        Long providerId,
        Integer seasonNumber,
        Integer episodeNumber,
        String name,
        String overview,
        LocalDate airDate,
        Integer runtimeMinutes,
        String stillPath,
        BigDecimal voteAverage
) {
}
