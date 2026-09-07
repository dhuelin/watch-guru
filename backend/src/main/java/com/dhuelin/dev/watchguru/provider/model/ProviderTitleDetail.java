package com.dhuelin.dev.watchguru.provider.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Full title record.
 *
 * @param imdbId the cross-source identifier; null when the provider has none
 */
public record ProviderTitleDetail(
        ProviderTitleSummary summary,
        String imdbId,
        String tagline,
        String homepage,
        String productionStatus,
        Integer runtimeMinutes,
        Integer numberOfSeasons,
        Integer numberOfEpisodes,
        LocalDate lastAirDate,
        List<ProviderGenre> genres,
        List<ProviderSeasonSummary> seasons
) {
}
