package com.dhuelin.dev.watchguru.tracking;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.SeasonRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchEvent;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.EpisodeWatchRepository;
import com.dhuelin.dev.watchguru.tracking.service.WatchHistoryService;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reading and correcting the viewing history (#22).
 *
 * <p>The history is append-only and authoritative; {@code episode_watch} and
 * {@code watchlist_item} are derived from it. So the interesting tests here are
 * not that a date changed -- they are that everything derived from the order of
 * events still agrees with it afterwards.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class HistoryEditingIntegrationTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    @Autowired
    private WatchHistoryService history;
    @Autowired
    private WatchlistService watchlist;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TitleRepository titles;
    @Autowired
    private SeasonRepository seasons;
    @Autowired
    private EpisodeRepository episodes;
    @Autowired
    private EpisodeWatchRepository episodeWatches;
    @Autowired
    private StreamingServiceRepository services;

    private AppUser user;
    private Title film;
    private Title series;
    private Episode episode;
    private StreamingService netflix;
    private StreamingService plex;

    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        user = new AppUser("history-" + seed + "@example.com", "History");
        user.setTimeZone(ZURICH.getId());
        user = users.save(user);

        film = new Title(9_000_000L + seed % 100_000, TitleType.MOVIE, "Heat " + seed);
        film.setRuntimeMinutes(170);
        film = titles.save(film);

        series = titles.save(new Title(9_100_000L + seed % 100_000, TitleType.TV_SERIES,
                "Severance " + seed));
        Season season = seasons.save(new Season(series, 1));
        Episode created = new Episode(season, 3);
        created.setName("In Perpetuity");
        created.setAirDate(LocalDate.of(2022, 2, 25));
        episode = episodes.save(created);

        netflix = services.findBySlug("netflix").orElseThrow();
        plex = services.findBySlug("plex").orElseThrow();
    }

    @Test
    @DisplayName("a date range includes the whole of its last day, in the user's own zone")
    void rangeIncludesItsLastEvening() {
        // 21:00 in Zurich on the 14th is 19:00 UTC, and a range ending on the
        // 14th has to include it. Treating the bound as the date itself would
        // drop the evening, which looks like having watched nothing.
        Instant lateOnTheFourteenth = LocalDate.of(2026, 9, 14)
                .atTime(21, 0).atZone(ZURICH).toInstant();
        watchlist.logMovieWatched(user.getId(), film.getId(), lateOnTheFourteenth, null);

        List<WatchEvent> inRange = list(new WatchHistoryService.Filter(
                WatchHistoryService.startOf(LocalDate.of(2026, 9, 14), ZURICH),
                WatchHistoryService.endOf(LocalDate.of(2026, 9, 14), ZURICH),
                null, null, null));

        assertThat(inRange).singleElement()
                .satisfies(event -> assertThat(event.getTitle().getId()).isEqualTo(film.getId()));
    }

    @Test
    @DisplayName("a range that ends the day before excludes it")
    void rangeExcludesLaterDays() {
        Instant onTheFourteenth = LocalDate.of(2026, 9, 14).atTime(21, 0).atZone(ZURICH).toInstant();
        watchlist.logMovieWatched(user.getId(), film.getId(), onTheFourteenth, null);

        assertThat(list(new WatchHistoryService.Filter(
                null, WatchHistoryService.endOf(LocalDate.of(2026, 9, 13), ZURICH),
                null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("filtering by type keeps films and episodes apart")
    void filtersByType() {
        watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null);
        watchlist.logEpisodeWatched(user.getId(), episode.getId(), Instant.now(), null);

        assertThat(list(filter(TitleType.MOVIE))).singleElement()
                .satisfies(e -> assertThat(e.getEpisode()).isNull());
        assertThat(list(filter(TitleType.TV_SERIES))).singleElement()
                .satisfies(e -> assertThat(e.getEpisode()).isNotNull());
        assertThat(list(WatchHistoryService.Filter.none())).hasSize(2);
    }

    @Test
    @DisplayName("filtering by service keeps only what was watched there")
    void filtersByService() {
        watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), netflix.getId());
        watchlist.logEpisodeWatched(user.getId(), episode.getId(), Instant.now(), plex.getId());

        assertThat(list(new WatchHistoryService.Filter(null, null, null, netflix.getId(), null)))
                .singleElement()
                .satisfies(e -> assertThat(e.getTitle().getId()).isEqualTo(film.getId()));
    }

    @Test
    @DisplayName("search matches a title or an episode name, and keeps films in the results")
    void searchesTitlesAndEpisodes() {
        watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null);
        watchlist.logEpisodeWatched(user.getId(), episode.getId(), Instant.now(), null);

        // The episode join has to be a left join: an implicit one would turn
        // into an inner join and drop every film the moment a term was typed.
        assertThat(list(search("heat"))).singleElement()
                .satisfies(e -> assertThat(e.getEpisode()).isNull());
        assertThat(list(search("in perpetuity"))).singleElement()
                .satisfies(e -> assertThat(e.getEpisode()).isNotNull());
        assertThat(list(search("nothing called this"))).isEmpty();
    }

    @Test
    @DisplayName("a wildcard somebody typed is a character, not a pattern")
    void escapesWildcards() {
        // Searching for "%" should find titles containing a per-cent sign, not
        // every row in the history.
        assertThat(list(search("%"))).isEmpty();
        assertThat(list(search("_"))).isEmpty();
    }

    @Test
    @DisplayName("moving an event's date moves it in the timeline")
    void editingTheDateMovesIt() {
        WatchEvent event = watchlist.logMovieWatched(
                user.getId(), film.getId(), Instant.parse("2026-09-13T19:00:00Z"), null);

        history.update(user.getId(), event.getId(), Instant.parse("2026-03-01T19:00:00Z"), null);

        assertThat(list(new WatchHistoryService.Filter(
                WatchHistoryService.startOf(LocalDate.of(2026, 9, 1), ZURICH), null, null, null, null)))
                .isEmpty();
        assertThat(list(new WatchHistoryService.Filter(
                WatchHistoryService.startOf(LocalDate.of(2026, 3, 1), ZURICH),
                WatchHistoryService.endOf(LocalDate.of(2026, 3, 1), ZURICH), null, null, null)))
                .hasSize(1);
    }

    @Test
    @DisplayName("correcting which service it was watched on leaves the date alone")
    void editingTheServiceLeavesTheDate() {
        Instant watchedAt = Instant.parse("2026-09-13T19:00:00Z");
        WatchEvent event = watchlist.logMovieWatched(user.getId(), film.getId(), watchedAt, netflix.getId());

        WatchEvent updated = history.update(user.getId(), event.getId(), null, plex.getId());

        assertThat(updated.getStreamingService().getId()).isEqualTo(plex.getId());
        assertThat(updated.getWatchedAt()).isEqualTo(watchedAt);
    }

    @Test
    @DisplayName("backdating a rewatch before the first viewing fixes which one is the rewatch")
    void recomputesRewatchFlags() {
        watchlist.logEpisodeWatched(user.getId(), episode.getId(),
                Instant.parse("2026-09-01T19:00:00Z"), null);
        WatchEvent second = watchlist.logEpisodeWatched(user.getId(), episode.getId(),
                Instant.parse("2026-09-10T19:00:00Z"), null);
        assertThat(second.isRewatch()).isTrue();

        // Moved to before the first: it is now the original viewing, and the
        // other one is the rewatch. Without recomputing, the history would say
        // somebody rewatched something before they ever saw it.
        history.update(user.getId(), second.getId(), Instant.parse("2026-08-01T19:00:00Z"), null);

        List<WatchEvent> ordered = list(WatchHistoryService.Filter.none()).reversed();
        assertThat(ordered.getFirst().isRewatch()).isFalse();
        assertThat(ordered.getLast().isRewatch()).isTrue();
    }

    @Test
    @DisplayName("when an episode was last seen follows the events, not the edit")
    void recomputesWhenTheEpisodeWasLastSeen() {
        watchlist.logEpisodeWatched(user.getId(), episode.getId(),
                Instant.parse("2026-09-01T19:00:00Z"), null);
        WatchEvent latest = watchlist.logEpisodeWatched(user.getId(), episode.getId(),
                Instant.parse("2026-09-10T19:00:00Z"), null);

        history.update(user.getId(), latest.getId(), Instant.parse("2026-08-01T19:00:00Z"), null);

        // The most recent viewing is now the September one, and episode_watch
        // has to say so rather than keeping the date of the event that moved.
        assertThat(episodeWatches.findByUserIdAndEpisodeId(user.getId(), episode.getId()))
                .get()
                .extracting(watch -> watch.getWatchedAt())
                .isEqualTo(Instant.parse("2026-09-01T19:00:00Z"));
    }

    @Test
    @DisplayName("somebody else's event is not found rather than forbidden")
    void refusesAnotherUsersEvent() {
        AppUser other = users.save(new AppUser("other-" + System.nanoTime() + "@example.com", "Other"));
        WatchEvent theirs = watchlist.logMovieWatched(other.getId(), film.getId(), Instant.now(), null);

        // 404 rather than 403: a 403 confirms the event exists, and ids are
        // sequential.
        assertThatThrownBy(() -> history.update(user.getId(), theirs.getId(), Instant.now(), null))
                .isInstanceOf(NotFoundException.class);
    }

    private List<WatchEvent> list(WatchHistoryService.Filter filter) {
        return history.list(user.getId(), filter, PageRequest.of(0, 50)).getContent();
    }

    private WatchHistoryService.Filter filter(TitleType type) {
        return new WatchHistoryService.Filter(null, null, type, null, null);
    }

    private WatchHistoryService.Filter search(String query) {
        return new WatchHistoryService.Filter(null, null, null, null, query);
    }
}
