package com.dhuelin.dev.watchguru.tracking;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.SeasonRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import com.dhuelin.dev.watchguru.tracking.service.StatsService;
import com.dhuelin.dev.watchguru.tracking.service.TitleProgress;
import com.dhuelin.dev.watchguru.tracking.service.WatchStats;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Episode tracking end to end against Postgres: progress, status transitions,
 * rewatch handling and the aggregate stats.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class WatchTrackingIntegrationTest {

    @Autowired
    private WatchlistService watchlist;
    @Autowired
    private StatsService stats;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TitleRepository titles;
    @Autowired
    private SeasonRepository seasons;
    @Autowired
    private EpisodeRepository episodes;
    @Autowired
    private WatchlistItemRepository items;

    private AppUser user;
    private Title series;
    private List<Episode> airedEpisodes;

    @BeforeEach
    void setUp() {
        // Unique email per run so repeated executions do not collide.
        user = users.save(new AppUser("viewer-" + System.nanoTime() + "@example.com", "Viewer"));

        series = new Title(9_000_000L + System.nanoTime() % 100_000, TitleType.TV_SERIES, "Test Series");
        series.setRuntimeMinutes(45);
        series = titles.save(series);

        Season season = seasons.save(new Season(series, 1));

        // Three aired episodes, plus one that has not aired yet.
        airedEpisodes = List.of(
                saveEpisode(season, 1, LocalDate.now().minusDays(21)),
                saveEpisode(season, 2, LocalDate.now().minusDays(14)),
                saveEpisode(season, 3, LocalDate.now().minusDays(7)));
        saveEpisode(season, 4, LocalDate.now().plusDays(7));
    }

    private Episode saveEpisode(Season season, int number, LocalDate airDate) {
        Episode episode = new Episode(season, number);
        episode.setName("Episode " + number);
        episode.setAirDate(airDate);
        episode.setRuntimeMinutes(45);
        return episodes.save(episode);
    }

    @Test
    void unairedEpisodesAreExcludedFromProgress() {
        TitleProgress progress = watchlist.progress(user.getId(), series.getId());

        // Four episodes exist, but only three have aired.
        assertThat(progress.airedEpisodes()).isEqualTo(3);
        assertThat(progress.watchedEpisodes()).isZero();
        assertThat(progress.percentComplete()).isZero();
        assertThat(progress.nextEpisodeCode()).isEqualTo("S01E01");
        assertThat(progress.remainingMinutes()).isEqualTo(135);
    }

    @Test
    void watchingAnEpisodeAdvancesProgressAndPointsAtTheNextOne() {
        watchlist.logEpisodeWatched(user.getId(), airedEpisodes.getFirst().getId(), Instant.now(), null);

        TitleProgress progress = watchlist.progress(user.getId(), series.getId());

        assertThat(progress.watchedEpisodes()).isEqualTo(1);
        assertThat(progress.percentComplete()).isEqualTo(33);
        assertThat(progress.nextEpisodeCode()).isEqualTo("S01E02");
        assertThat(progress.remainingMinutes()).isEqualTo(90);
    }

    @Test
    void completingEveryAiredEpisodeMovesTheItemToCompleted() {
        WatchlistItem item = items.save(new WatchlistItem(user, series, WatchStatus.WATCHLIST));

        watchlist.logEpisodeWatched(user.getId(), airedEpisodes.get(0).getId(), Instant.now(), null);
        assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(WatchStatus.WATCHING);

        watchlist.logEpisodeWatched(user.getId(), airedEpisodes.get(1).getId(), Instant.now(), null);
        watchlist.logEpisodeWatched(user.getId(), airedEpisodes.get(2).getId(), Instant.now(), null);

        WatchlistItem reloaded = items.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(WatchStatus.COMPLETED);
        assertThat(reloaded.getStartedAt()).isNotNull();
        assertThat(reloaded.getCompletedAt()).isNotNull();

        TitleProgress progress = watchlist.progress(user.getId(), series.getId());
        assertThat(progress.percentComplete()).isEqualTo(100);
        assertThat(progress.nextEpisodeCode()).isNull();
    }

    @Test
    void rewatchingAnEpisodeAddsHistoryWithoutInflatingProgress() {
        Long episodeId = airedEpisodes.getFirst().getId();
        watchlist.logEpisodeWatched(user.getId(), episodeId, Instant.now().minus(2, ChronoUnit.DAYS), null);
        watchlist.logEpisodeWatched(user.getId(), episodeId, Instant.now(), null);

        // Progress still counts one distinct episode...
        assertThat(watchlist.progress(user.getId(), series.getId()).watchedEpisodes()).isEqualTo(1);

        // ...but both viewings are in the history and the time is counted twice.
        WatchStats result = stats.forUser(user.getId(), 12);
        assertThat(result.totalEpisodeViewings()).isEqualTo(2);
        assertThat(result.totalMinutes()).isEqualTo(90);
        assertThat(result.distinctTitles()).isEqualTo(1);
    }

    @Test
    void statsAggregateMinutesGenresAndStreaks() {
        watchlist.logEpisodeWatched(user.getId(), airedEpisodes.get(0).getId(),
                Instant.now().minus(1, ChronoUnit.DAYS), null);
        watchlist.logEpisodeWatched(user.getId(), airedEpisodes.get(1).getId(), Instant.now(), null);

        WatchStats result = stats.forUser(user.getId(), 12);

        assertThat(result.totalMinutes()).isEqualTo(90);
        assertThat(result.totalHours()).isEqualTo(1);
        assertThat(result.totalMovieViewings()).isZero();
        assertThat(result.currentStreakDays()).isEqualTo(2);
        assertThat(result.firstWatchedAt()).isNotNull();
        assertThat(result.byMonth()).isNotEmpty();
    }
}
