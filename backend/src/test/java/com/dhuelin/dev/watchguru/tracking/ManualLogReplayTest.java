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
import com.dhuelin.dev.watchguru.tracking.domain.EpisodeWatch;
import com.dhuelin.dev.watchguru.tracking.domain.WatchEvent;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.EpisodeWatchRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logging the same viewing twice, which is not the same as watching it twice.
 *
 * <p>An app that files a viewing while offline cannot tell "the server never
 * saw it" from "the server saw it and the reply was lost", so it has to be safe
 * to send again. Neither log is naturally idempotent -- a second call from a
 * person is a rewatch, and rewatches are the point of the feature -- so the app
 * names the viewing it means with a {@code clientRef} and a replay of that
 * reference is a no-op.
 *
 * <p>The distinction these tests defend: the same reference twice is one
 * viewing, and two references are two.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class ManualLogReplayTest {

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
    private WatchEventRepository watchEvents;

    private AppUser user;
    private Title film;
    private Episode episode;

    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        user = users.save(new AppUser("replay-" + seed + "@example.com", "Replay"));

        film = new Title(9_200_000L + seed % 100_000, TitleType.MOVIE, "Sicario " + seed);
        film.setRuntimeMinutes(121);
        film = titles.save(film);

        Title series = titles.save(new Title(9_300_000L + seed % 100_000, TitleType.TV_SERIES,
                "Andor " + seed));
        Season season = seasons.save(new Season(series, 1));
        Episode created = new Episode(season, 5);
        created.setName("The Axe Forgets");
        created.setAirDate(LocalDate.of(2022, 9, 28));
        episode = episodes.save(created);
    }

    @Test
    @DisplayName("replaying a film log returns the viewing already filed")
    void replayingAFilmIsTheSameViewing() {
        WatchEvent first = watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null, "queued-1");
        WatchEvent replay = watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null, "queued-1");

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(watchEvents.findByUserIdAndTitleIdOrderByWatchedAtDesc(user.getId(), film.getId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("a replay is not a rewatch")
    void aReplayIsNotARewatch() {
        // The failure this guards against is not a duplicate row -- it is the
        // second row claiming to be a rewatch, which is a story about the user
        // that never happened.
        watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null, "queued-1");
        WatchEvent replay = watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null, "queued-1");

        assertThat(replay.isRewatch()).isFalse();
    }

    @Test
    @DisplayName("a different reference is a real second viewing")
    void aDifferentReferenceIsARewatch() {
        watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null, "queued-1");
        WatchEvent second = watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null, "queued-2");

        assertThat(second.isRewatch()).isTrue();
        assertThat(watchEvents.findByUserIdAndTitleIdOrderByWatchedAtDesc(user.getId(), film.getId()))
                .hasSize(2);
    }

    @Test
    @DisplayName("without a reference, watching it again is still a rewatch")
    void noReferenceKeepsTheOldBehaviour() {
        // Clients that do not queue do not have to send one, and for them a
        // second call means exactly what it used to.
        watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null);
        WatchEvent second = watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null);

        assertThat(second.isRewatch()).isTrue();
    }

    @Test
    @DisplayName("replaying an episode leaves its watch count and last-seen date alone")
    void replayingAnEpisodeDoesNotBumpItsCount() {
        // The reason the check has to come before anything is written: this
        // path moves the episode's last-watched date and bumps its count before
        // it ever reaches the event insert that would have refused the replay.
        Instant lastNight = Instant.now().minus(9, ChronoUnit.HOURS);
        watchlist.logEpisodeWatched(user.getId(), episode.getId(), lastNight, null, "queued-ep");

        EpisodeWatch afterFirst = episodeWatches
                .findByUserIdAndEpisodeId(user.getId(), episode.getId()).orElseThrow();
        int countAfterFirst = afterFirst.getWatchCount();
        Instant seenAfterFirst = afterFirst.getWatchedAt();

        watchlist.logEpisodeWatched(user.getId(), episode.getId(), Instant.now(), null, "queued-ep");

        EpisodeWatch afterReplay = episodeWatches
                .findByUserIdAndEpisodeId(user.getId(), episode.getId()).orElseThrow();
        assertThat(afterReplay.getWatchCount()).isEqualTo(countAfterFirst);
        assertThat(afterReplay.getWatchedAt()).isEqualTo(seenAfterFirst);
        assertThat(watchEvents.findByUserIdAndEpisodeId(user.getId(), episode.getId())).hasSize(1);
    }

    @Test
    @DisplayName("one user's reference does not reach another's")
    void referencesAreScopedToTheirUser() {
        // Two phones queueing their first viewing can easily pick the same
        // reference; whether they do is not something either user can see.
        AppUser other = users.save(new AppUser("replay-other-" + System.nanoTime() + "@example.com", "Other"));

        watchlist.logMovieWatched(user.getId(), film.getId(), Instant.now(), null, "queued-1");
        WatchEvent theirs = watchlist.logMovieWatched(other.getId(), film.getId(), Instant.now(), null, "queued-1");

        assertThat(theirs.getUser().getId()).isEqualTo(other.getId());
        assertThat(theirs.isRewatch()).isFalse();
    }
}
