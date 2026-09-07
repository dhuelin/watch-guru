package com.dhuelin.dev.watchguru.tracking.service;

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
        Long titleId,
        String primaryTitle,
        int airedEpisodes,
        int watchedEpisodes,
        int percentComplete,
        Long nextEpisodeId,
        String nextEpisodeCode,
        String nextEpisodeName,
        int remainingMinutes
) {
}
