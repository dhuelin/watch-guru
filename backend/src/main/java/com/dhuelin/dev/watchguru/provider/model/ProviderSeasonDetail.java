package com.dhuelin.dev.watchguru.provider.model;

import java.util.List;

public record ProviderSeasonDetail(
        ProviderSeasonSummary season,
        List<ProviderEpisode> episodes
) {
}
