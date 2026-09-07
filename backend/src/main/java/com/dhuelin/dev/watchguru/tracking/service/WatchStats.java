package com.dhuelin.dev.watchguru.tracking.service;

import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregated view of everything a user has watched.
 *
 * @param currentStreakDays consecutive days up to today with at least one viewing
 * @param longestStreakDays longest such run in the whole history
 */
public record WatchStats(
        long totalMinutes,
        long totalMovieViewings,
        long totalEpisodeViewings,
        long distinctTitles,
        long minutesLast30Days,
        long minutesLast365Days,
        int currentStreakDays,
        int longestStreakDays,
        Instant firstWatchedAt,
        Instant lastWatchedAt,
        Map<WatchStatus, Long> byStatus,
        List<Bucket> byGenre,
        List<Bucket> byService,
        List<Bucket> topTitles,
        List<MonthBucket> byMonth
) {
    /** A named slice of the history. */
    public record Bucket(String label, long viewings, long minutes) {
    }

    /** One calendar month of viewing. */
    public record MonthBucket(int year, int month, long viewings, long minutes) {
    }

    /** Convenience for the UI: total watch time expressed in whole hours. */
    public long totalHours() {
        return totalMinutes / 60;
    }
}
