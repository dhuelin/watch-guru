package com.dhuelin.dev.watchguru.tracking.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Streak arithmetic, exercised without a database. */
class StatsServiceStreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 29);

    @Test
    void noHistoryMeansNoStreak() {
        assertThat(StatsService.currentStreak(List.of(), TODAY)).isZero();
        assertThat(StatsService.longestStreak(List.of())).isZero();
    }

    @Test
    void countsConsecutiveDaysEndingToday() {
        List<LocalDate> days = List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));

        assertThat(StatsService.currentStreak(days, TODAY)).isEqualTo(3);
    }

    @Test
    void yesterdayStillCountsSoTheStreakSurvivesUntilTonight() {
        List<LocalDate> days = List.of(TODAY.minusDays(1), TODAY.minusDays(2));

        assertThat(StatsService.currentStreak(days, TODAY)).isEqualTo(2);
    }

    @Test
    void aGapOfTwoDaysBreaksTheCurrentStreak() {
        List<LocalDate> days = List.of(TODAY.minusDays(2), TODAY.minusDays(3));

        assertThat(StatsService.currentStreak(days, TODAY)).isZero();
    }

    @Test
    void longestStreakIgnoresWhereItSitsInHistory() {
        // A four-day run in the past, then a gap, then a single recent day.
        List<LocalDate> days = List.of(
                TODAY,
                TODAY.minusDays(10), TODAY.minusDays(11), TODAY.minusDays(12), TODAY.minusDays(13));

        assertThat(StatsService.longestStreak(days)).isEqualTo(4);
        assertThat(StatsService.currentStreak(days, TODAY)).isEqualTo(1);
    }
}
