package com.dhuelin.dev.watchguru.tracking;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.service.StatsPeriod;
import com.dhuelin.dev.watchguru.tracking.service.StatsService;
import com.dhuelin.dev.watchguru.tracking.service.WatchStats;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That a period scopes the whole screen, not only its headline.
 *
 * <p>The failure this guards against is a screen whose parts do not add up:
 * "4 hours this month" above a genre breakdown of the last decade. Every
 * figure a period touches is checked here against the same data.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class StatsPeriodIntegrationTest {

    @Autowired
    private StatsService stats;
    @Autowired
    private WatchlistService watchlist;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TitleRepository titles;

    private AppUser user;
    private Title thisMonth;
    private Title lastYear;

    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        user = users.save(new AppUser("stats-" + seed + "@example.com", "Statistician"));

        thisMonth = titles.save(film("Recent " + seed, seed, 90));
        lastYear = titles.save(film("Ancient " + seed, seed + 1, 120));

        LocalDate today = LocalDate.now(user.zone());
        // Today, so it is inside every period.
        watchlist.logMovieWatched(user.getId(), thisMonth.getId(), Instant.now(), null);
        // Comfortably in a previous year, so only all-time includes it.
        watchlist.logMovieWatched(user.getId(), lastYear.getId(),
                today.minusYears(2).atStartOfDay(user.zone()).toInstant(), null);
    }

    private Title film(String name, long seed, int runtime) {
        Title title = new Title(3_000_000L + seed % 100_000, TitleType.MOVIE, name);
        title.setRuntimeMinutes(runtime);
        return title;
    }

    @Test
    @DisplayName("all time counts both viewings")
    void allTime() {
        WatchStats all = stats.forUser(user.getId(), 12, StatsPeriod.ALL_TIME);

        assertThat(all.totalMovieViewings()).isEqualTo(2);
        assertThat(all.totalMinutes()).isEqualTo(210);
        assertThat(all.distinctTitles()).isEqualTo(2);
    }

    @Test
    @DisplayName("this month counts only the recent one, in every figure")
    void thisMonthScopesEverything() {
        WatchStats month = stats.forUser(user.getId(), 12, StatsPeriod.MONTH);

        assertThat(month.totalMovieViewings()).isEqualTo(1);
        assertThat(month.totalMinutes()).isEqualTo(90);
        assertThat(month.distinctTitles()).isEqualTo(1);
        // The breakdowns move with the headline, or the screen contradicts
        // itself.
        assertThat(month.topTitles()).singleElement()
                .satisfies(top -> assertThat(top.label()).isEqualTo(thisMonth.getPrimaryTitle()));
        assertThat(month.byMonth()).hasSize(1);
    }

    @Test
    @DisplayName("this year excludes a viewing from two years ago")
    void thisYear() {
        WatchStats year = stats.forUser(user.getId(), 12, StatsPeriod.YEAR);

        assertThat(year.totalMovieViewings()).isEqualTo(1);
        assertThat(year.topTitles()).extracting(WatchStats.Bucket::label)
                .doesNotContain(lastYear.getPrimaryTitle());
    }

    @Test
    @DisplayName("streaks stay whole-history whatever the period says")
    void streaksIgnoreThePeriod() {
        // "Your longest streak this month" is a different, much less
        // interesting number than the one people mean by a streak.
        WatchStats month = stats.forUser(user.getId(), 12, StatsPeriod.MONTH);
        WatchStats all = stats.forUser(user.getId(), 12, StatsPeriod.ALL_TIME);

        assertThat(month.longestStreakDays()).isEqualTo(all.longestStreakDays());
        assertThat(month.currentStreakDays()).isEqualTo(all.currentStreakDays());
    }

    @Test
    @DisplayName("a period with nothing in it is zeroes, not an error")
    void emptyPeriod() {
        AppUser fresh = users.save(new AppUser("empty-" + System.nanoTime() + "@example.com", "Newcomer"));

        WatchStats month = stats.forUser(fresh.getId(), 12, StatsPeriod.MONTH);

        assertThat(month.totalMinutes()).isZero();
        assertThat(month.topTitles()).isEmpty();
        assertThat(month.byGenre()).isEmpty();
        assertThat(month.firstWatchedAt()).isNull();
    }

    @Test
    @DisplayName("the monthly series never runs past the period it sits under")
    void monthlySeriesFollowsThePeriod() {
        // Twelve months of bars under a "this month" heading would draw eleven
        // bars the figures above exclude.
        WatchStats month = stats.forUser(user.getId(), 12, StatsPeriod.MONTH);
        LocalDate today = LocalDate.now(ZoneId.of(user.getTimeZone()));

        assertThat(month.byMonth()).allSatisfy(bucket -> {
            assertThat(bucket.year()).isEqualTo(today.getYear());
            assertThat(bucket.month()).isEqualTo(today.getMonthValue());
        });
    }
}
