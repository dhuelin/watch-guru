package com.dhuelin.dev.watchguru.tracking.service;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How far a user is through a series.
 *
 * <p>Counts cover aired episodes only, and exclude season 0 specials, so a
 * running series does not look permanently unfinished.
 *
 * @param nextEpisodeCode  "S01E02" style label of the next unwatched episode, null when caught up
 * @param remainingMinutes estimated time left to finish what has aired
 */
public record TitleProgress(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long titleId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String primaryTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int airedEpisodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int watchedEpisodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int percentComplete,
        // The next-episode triple is genuinely absent once a user is caught up,
        // which is what the clients need to distinguish "no next episode" from
        // "field we forgot to mark".
        Long nextEpisodeId,
        String nextEpisodeCode,
        String nextEpisodeName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int remainingMinutes
) {
}
