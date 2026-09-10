package com.dhuelin.dev.watchguru.tracking;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.SeasonRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.EpisodeWatchRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correcting mistakes: unmarking an episode, and deleting one history entry.
 *
 * <p>The invariant under test throughout is that the append-only history and
 * the derived current state never disagree. Deleting one without the other is
 * the easy bug here, and it only shows up later, when something recomputes.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class UnmarkAndDeleteEventTest {

    @Autowired private WatchlistService watchlist;
    @Autowired private AppUserRepository users;
    @Autowired private TitleRepository titles;
    @Autowired private SeasonRepository seasons;
    @Autowired private EpisodeRepository episodes;
    @Autowired private EpisodeWatchRepository episodeWatches;
    @Autowired private WatchEventRepository watchEvents;
    @Autowired private WatchlistItemRepository items;

    private AppUser user;
    private Title series;
    private List<Episode> aired;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("undo-" + System.nanoTime() + "@example.com", "Viewer"));
        series = titles.save(new Title(7_000_000L + System.nanoTime() % 100_000,
                TitleType.TV_SERIES, "Undo Series"));
        Season one = seasons.save(new Season(series, 1));
        aired = List.of(
                saveEpisode(one, 1, LocalDate.now().minusDays(20)),
                saveEpisode(one, 2, LocalDate.now().minusDays(13)));
        watchlist.add(user.getId(), TitleType.TV_SERIES, series.getTmdbId(), WatchStatus.WATCHING);
    }

    private Episode saveEpisode(Season season, int number, LocalDate airDate) {
        Episode episode = new Episode(season, number);
        episode.setTitle(series);
        episode.setSeasonNumber(1);
        episode.setAirDate(airDate);
        episode.setRuntimeMinutes(45);
        return episodes.save(episode);
    }

    private WatchStatus statusOf() {
        return items.findByUserIdAndTitleId(user.getId(), series.getId()).orElseThrow().getStatus();
    }

    // --- unmarking ---------------------------------------------------------

    @Test
    @DisplayName("unmarking removes both the watched row and its history")
    void unmarkRemovesStateAndHistory() {
        Long episodeId = aired.getFirst().getId();
        watchlist.logEpisodeWatched(user.getId(), episodeId, Instant.now(), null);

        assertThat(watchlist.unmarkEpisode(user.getId(), episodeId)).isTrue();

        // Both, not one. Leaving events behind would mean any recomputation
        // silently restored an episode the user says they never watched.
        assertThat(episodeWatches.findByUserIdAndEpisodeId(user.getId(), episodeId)).isEmpty();
        assertThat(watchEvents.findByUserIdAndEpisodeId(user.getId(), episodeId)).isEmpty();
    }

    @Test
    @DisplayName("unmarking something already unwatched is a no-op, not an error")
    void unmarkIsIdempotent() {
        Long episodeId = aired.getFirst().getId();

        // A client retrying after a dropped response must not see a failure for
        // work that is already done.
        assertThat(watchlist.unmarkEpisode(user.getId(), episodeId)).isFalse();
        assertThat(watchlist.unmarkEpisode(user.getId(), episodeId)).isFalse();
    }

    @Test
    @DisplayName("unmarking a rewatched episode removes every one of its events")
    void unmarkRemovesAllRewatches() {
        Long episodeId = aired.getFirst().getId();
        watchlist.logEpisodeWatched(user.getId(), episodeId, Instant.now().minus(2, ChronoUnit.DAYS), null);
        watchlist.logEpisodeWatched(user.getId(), episodeId, Instant.now(), null);

        watchlist.unmarkEpisode(user.getId(), episodeId);

        assertThat(watchEvents.findByUserIdAndEpisodeId(user.getId(), episodeId)).isEmpty();
    }

    @Test
    @DisplayName("a completed series falls back to watching when an episode is unmarked")
    void completedFallsBackToWatching() {
        watchlist.markWatchedUpTo(user.getId(), aired.get(1).getId(), Instant.now());
        assertThat(statusOf()).isEqualTo(WatchStatus.COMPLETED);

        watchlist.unmarkEpisode(user.getId(), aired.get(1).getId());

        // The old advanceSeriesStatus only ever moved forwards; a series stuck
        // on COMPLETED with an unwatched episode is exactly the drift this
        // guards against.
        assertThat(statusOf()).isEqualTo(WatchStatus.WATCHING);
    }

    @Test
    @DisplayName("unmarking the last watched episode returns the series to the watchlist")
    void returnsToWatchlistWhenNothingIsWatched() {
        watchlist.logEpisodeWatched(user.getId(), aired.getFirst().getId(), Instant.now(), null);

        watchlist.unmarkEpisode(user.getId(), aired.getFirst().getId());

        assertThat(statusOf()).isEqualTo(WatchStatus.WATCHLIST);
    }

    @Test
    @DisplayName("unmarking does not overrule a deliberate on-hold or dropped")
    void doesNotOverruleUserIntent() {
        watchlist.logEpisodeWatched(user.getId(), aired.getFirst().getId(), Instant.now(), null);
        var item = items.findByUserIdAndTitleId(user.getId(), series.getId()).orElseThrow();
        watchlist.updateStatus(user.getId(), item.getId(), WatchStatus.DROPPED);

        watchlist.unmarkEpisode(user.getId(), aired.getFirst().getId());

        // DROPPED is a statement about intent. Unmarking an episode is not a
        // reason to overrule it.
        assertThat(statusOf()).isEqualTo(WatchStatus.DROPPED);
    }

    // --- deleting a single history entry -----------------------------------

    @Test
    @DisplayName("deleting one of several rewatches leaves the episode watched")
    void deletingOneRewatchKeepsTheEpisodeWatched() {
        Long episodeId = aired.getFirst().getId();
        watchlist.logEpisodeWatched(user.getId(), episodeId, Instant.now().minus(5, ChronoUnit.DAYS), null);
        watchlist.logEpisodeWatched(user.getId(), episodeId, Instant.now(), null);

        var events = watchEvents.findByUserIdAndEpisodeId(user.getId(), episodeId);
        assertThat(events).hasSize(2);
        watchlist.deleteWatchEvent(user.getId(), events.getFirst().getId());

        var watch = episodeWatches.findByUserIdAndEpisodeId(user.getId(), episodeId).orElseThrow();
        // The count follows the surviving history rather than being decremented
        // blindly, so the two cannot drift apart.
        assertThat(watch.getWatchCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("deleting the last event for an episode makes it unwatched")
    void deletingTheLastEventUnwatchesTheEpisode() {
        Long episodeId = aired.getFirst().getId();
        watchlist.logEpisodeWatched(user.getId(), episodeId, Instant.now(), null);

        var events = watchEvents.findByUserIdAndEpisodeId(user.getId(), episodeId);
        watchlist.deleteWatchEvent(user.getId(), events.getFirst().getId());

        assertThat(episodeWatches.findByUserIdAndEpisodeId(user.getId(), episodeId)).isEmpty();
        assertThat(statusOf()).isEqualTo(WatchStatus.WATCHLIST);
    }

    @Test
    @DisplayName("another user's event is a 404, not a 403")
    void cannotDeleteSomeoneElsesEvent() {
        AppUser other = users.save(new AppUser("other-" + System.nanoTime() + "@example.com", "Other"));
        watchlist.logEpisodeWatched(user.getId(), aired.getFirst().getId(), Instant.now(), null);
        var events = watchEvents.findByUserIdAndEpisodeId(user.getId(), aired.getFirst().getId());

        // 404 rather than 403: a 403 confirms the event exists, and ids are
        // sequential.
        assertThatThrownBy(() -> watchlist.deleteWatchEvent(other.getId(), events.getFirst().getId()))
                .isInstanceOf(NotFoundException.class);

        assertThat(watchEvents.findByUserIdAndEpisodeId(user.getId(), aired.getFirst().getId())).hasSize(1);
    }
}
