package com.dhuelin.dev.watchguru.tracking.service;

/**
 * Aired and watched episode counts for one series.
 *
 * <p>A projection rather than an entity: the library list needs these numbers
 * for every row at once, and loading episodes to count them would be an N+1
 * across a page.
 *
 * @param titleId        the series
 * @param airedEpisodes  episodes that have aired, excluding season 0 specials
 * @param watchedEpisodes how many of those the user has seen
 */
public record SeriesProgressCounts(Long titleId, long airedEpisodes, long watchedEpisodes) {

    public int percentComplete() {
        if (airedEpisodes <= 0) {
            return 0;
        }
        return (int) Math.round(watchedEpisodes * 100.0 / airedEpisodes);
    }
}
