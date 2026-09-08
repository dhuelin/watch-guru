package com.dhuelin.dev.watchguru.tracking.service;

import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalMovieViewings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalEpisodeViewings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long distinctTitles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long minutesLast30Days,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long minutesLast365Days,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int currentStreakDays,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int longestStreakDays,
        Instant firstWatchedAt,
        Instant lastWatchedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<WatchStatus, Long> byStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Bucket> byGenre,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Bucket> byService,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Bucket> topTitles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MonthBucket> byMonth
) {
    /** A named slice of the history. */
    public record Bucket(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long viewings,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long minutes) {
    }

    /** One calendar month of viewing. */
    public record MonthBucket(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int year,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int month,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long viewings,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long minutes) {
    }

    /** Convenience for the UI: total watch time expressed in whole hours. */
    public long totalHours() {
        return totalMinutes / 60;
    }
}
