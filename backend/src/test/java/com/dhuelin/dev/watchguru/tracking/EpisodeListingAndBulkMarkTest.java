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
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.service.EpisodeListService;
import com.dhuelin.dev.watchguru.tracking.service.SeriesProgressCounts;
import com.dhuelin.dev.watchguru.tracking.service.UpNextService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Episode listings, bulk marking, batched progress and Up Next.
 *
 * <p>All of it is query behaviour over real data, so it is tested against a
 * real database rather than mocks — the interesting cases are exactly the ones
 * a mock would let through: specials excluded, unaired episodes excluded, and
 * "next" meaning the first gap in broadcast order rather than the one after the
 * highest watched.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class EpisodeListingAndBulkMarkTest {

    @Autowired private EpisodeListService episodeList;
    @Autowired private WatchlistService watchlist;
    @Autowired private UpNextService upNext;
    @Autowired private EpisodeRepository episodes;
    @Autowired private AppUserRepository users;
    @Autowired private TitleRepository titles;
    @Autowired private SeasonRepository seasons;

    private AppUser user;
    private Title series;
    private List<Episode> seasonOne;
    private List<Episode> seasonTwo;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("lists-" + System.nanoTime() + "@example.com", "Viewer"));

        series = new Title(8_000_000L + System.nanoTime() % 100_000, TitleType.TV_SERIES, "Listed Series");
        series.setRuntimeMinutes(45);
        series = titles.save(series);

        // Season 0 holds specials, which must never count towards progress or
        // be offered as "next".
        Season specials = seasons.save(new Season(series, 0));
        saveEpisode(specials, 1, LocalDate.now().minusDays(60));

        Season one = seasons.save(new Season(series, 1));
        seasonOne = List.of(
                saveEpisode(one, 1, LocalDate.now().minusDays(50)),
                saveEpisode(one, 2, LocalDate.now().minusDays(43)),
                saveEpisode(one, 3, LocalDate.now().minusDays(36)));

        Season two = seasons.save(new Season(series, 2));
        seasonTwo = List.of(
                saveEpisode(two, 1, LocalDate.now().minusDays(29)),
                saveEpisode(two, 2, LocalDate.now().minusDays(22)));
        // Not aired: must be listed but never markable or counted.
        saveEpisode(two, 3, LocalDate.now().plusDays(7));
    }

    private Episode saveEpisode(Season season, int number, LocalDate airDate) {
        Episode episode = new Episode(season, number);
        episode.setTitle(series);
        episode.setSeasonNumber(season.getSeasonNumber());
        episode.setName("S%dE%d".formatted(season.getSeasonNumber(), number));
        episode.setAirDate(airDate);
        episode.setRuntimeMinutes(45);
        return episodes.save(episode);
    }

    // --- listings ---------------------------------------------------------

    @Test
    @DisplayName("every season is listed, specials included, with episodes in order")
    void listsSeasonsAndEpisodes() {
        var listing = episodeList.seasonsFor(user.getId(), series.getId());

        assertThat(listing.seasons()).extracting(EpisodeListService.SeasonWithEpisodes::seasonNumber)
                .containsExactly(0, 1, 2);
        // Specials are shown rather than hidden: people do watch them, and
        // omitting them would make episodes they have seen unreachable.
        assertThat(listing.seasons().getFirst().episodes()).hasSize(1);
        assertThat(listing.seasons().get(2).episodes())
                .extracting(Episode::getEpisodeNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("watched state comes back with the list, not as a second call")
    void listingCarriesWatchedState() {
        watchlist.logEpisodeWatched(user.getId(), seasonOne.getFirst().getId(), Instant.now(), null);

        var listing = episodeList.seasonsFor(user.getId(), series.getId());
        var one = listing.seasons().stream().filter(s -> s.seasonNumber() == 1).findFirst().orElseThrow();

        assertThat(one.isWatched(seasonOne.getFirst())).isTrue();
        assertThat(one.isWatched(seasonOne.get(1))).isFalse();
        assertThat(one.watchedEpisodes()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unaired episode is listed but not counted as aired")
    void unairedEpisodesAreListedButNotCounted() {
        var listing = episodeList.seasonsFor(user.getId(), series.getId());
        var two = listing.seasons().stream().filter(s -> s.seasonNumber() == 2).findFirst().orElseThrow();

        assertThat(two.episodes()).hasSize(3);
        assertThat(two.airedEpisodes()).isEqualTo(2);
    }

    // --- mark all up to here ----------------------------------------------

    @Test
    @DisplayName("marking up to S02E01 marks all of season 1 and nothing later")
    void marksEverythingUpToTheChosenEpisode() {
        var result = watchlist.markWatchedUpTo(user.getId(), seasonTwo.getFirst().getId(), Instant.now());

        // Three from season 1 plus S02E01. The special in season 0 is not
        // swept in, and the unaired S02E03 is not touched.
        assertThat(result.newlyMarked()).isEqualTo(4);
        assertThat(result.watchedEpisodes()).isEqualTo(4);

        var listing = episodeList.seasonsFor(user.getId(), series.getId());
        var specials = listing.seasons().stream().filter(s -> s.seasonNumber() == 0).findFirst().orElseThrow();
        assertThat(specials.watchedEpisodes()).isZero();
    }

    @Test
    @DisplayName("marking up to the same point twice changes nothing the second time")
    void bulkMarkIsIdempotent() {
        watchlist.markWatchedUpTo(user.getId(), seasonTwo.getFirst().getId(), Instant.now());
        var second = watchlist.markWatchedUpTo(user.getId(), seasonTwo.getFirst().getId(), Instant.now());

        // The whole point: a double tap must not turn four episodes into
        // rewatches, which is what looping over the single-episode endpoint
        // would do.
        assertThat(second.newlyMarked()).isZero();
        assertThat(second.alreadyWatched()).isEqualTo(4);
        assertThat(second.watchedEpisodes()).isEqualTo(4);
    }

    @Test
    @DisplayName("a gap is filled without disturbing what was already watched")
    void fillsGapsWithoutRewatching() {
        watchlist.logEpisodeWatched(user.getId(), seasonOne.get(1).getId(), Instant.now(), null);

        var result = watchlist.markWatchedUpTo(user.getId(), seasonOne.get(2).getId(), Instant.now());

        assertThat(result.newlyMarked()).isEqualTo(2);
        assertThat(result.alreadyWatched()).isEqualTo(1);
        assertThat(result.watchedEpisodes()).isEqualTo(3);
    }

    @Test
    @DisplayName("marking the whole run completes the series")
    void completingEverythingAiredMarksCompleted() {
        watchlist.add(user.getId(), TitleType.TV_SERIES, series.getTmdbId(), WatchStatus.WATCHING);

        watchlist.markWatchedUpTo(user.getId(), seasonTwo.get(1).getId(), Instant.now());

        var progress = watchlist.progress(user.getId(), series.getId());
        assertThat(progress.watchedEpisodes()).isEqualTo(progress.airedEpisodes());
    }

    // --- batched progress --------------------------------------------------

    @Test
    @DisplayName("progress for several titles comes back in one query, zeros included")
    void batchedProgressIncludesUnwatchedSeries() {
        watchlist.logEpisodeWatched(user.getId(), seasonOne.getFirst().getId(), Instant.now(), null);

        Title other = titles.save(new Title(8_100_000L + System.nanoTime() % 100_000,
                TitleType.TV_SERIES, "Other Series"));
        Season otherSeason = seasons.save(new Season(other, 1));
        Episode otherEpisode = new Episode(otherSeason, 1);
        otherEpisode.setTitle(other);
        otherEpisode.setSeasonNumber(1);
        otherEpisode.setAirDate(LocalDate.now().minusDays(3));
        episodes.save(otherEpisode);

        List<SeriesProgressCounts> counts =
                episodes.progressForTitles(user.getId(), List.of(series.getId(), other.getId()));

        assertThat(counts).hasSize(2);
        var forSeries = counts.stream().filter(c -> c.titleId().equals(series.getId())).findFirst().orElseThrow();
        // 5 aired across seasons 1 and 2; the special and the unaired episode
        // are both excluded.
        assertThat(forSeries.airedEpisodes()).isEqualTo(5);
        assertThat(forSeries.watchedEpisodes()).isEqualTo(1);

        // A series with nothing watched must still return a row, or the caller
        // cannot tell "no progress" from "not in the result".
        var forOther = counts.stream().filter(c -> c.titleId().equals(other.getId())).findFirst().orElseThrow();
        assertThat(forOther.watchedEpisodes()).isZero();
        assertThat(forOther.airedEpisodes()).isEqualTo(1);
    }

    @Test
    @DisplayName("percentComplete rounds rather than truncating")
    void percentCompleteRounds() {
        assertThat(new SeriesProgressCounts(1L, 3, 2).percentComplete()).isEqualTo(67);
        assertThat(new SeriesProgressCounts(1L, 0, 0).percentComplete()).isZero();
    }

    // --- up next -----------------------------------------------------------

    @Test
    @DisplayName("up next offers the first gap in broadcast order, not the one after the newest watch")
    void upNextFollowsBroadcastOrder() {
        watchlist.add(user.getId(), TitleType.TV_SERIES, series.getTmdbId(), WatchStatus.WATCHING);
        // Someone jumped ahead. They still have not seen season one.
        watchlist.logEpisodeWatched(user.getId(), seasonTwo.getFirst().getId(), Instant.now(), null);

        var entries = upNext.forUser(user.getId(), 10);

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().nextEpisode().getSeasonNumber()).isEqualTo(1);
        assertThat(entries.getFirst().nextEpisode().getEpisodeNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("a caught-up series drops off the list rather than showing an unplayable row")
    void caughtUpSeriesIsOmitted() {
        watchlist.add(user.getId(), TitleType.TV_SERIES, series.getTmdbId(), WatchStatus.WATCHING);
        watchlist.markWatchedUpTo(user.getId(), seasonTwo.get(1).getId(), Instant.now());

        assertThat(upNext.forUser(user.getId(), 10)).isEmpty();
    }

    @Test
    @DisplayName("a special is never offered as the next episode")
    void specialsAreNeverNext() {
        watchlist.add(user.getId(), TitleType.TV_SERIES, series.getTmdbId(), WatchStatus.WATCHING);

        var entries = upNext.forUser(user.getId(), 10);

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().nextEpisode().getSeasonNumber()).isNotZero();
    }

    @Test
    @DisplayName("a user with nothing in progress gets an empty list, not an error")
    void nothingInProgressIsEmpty() {
        assertThat(upNext.forUser(user.getId(), 10)).isEmpty();
    }
}
