package com.dhuelin.dev.watchguru.streaming.trakt;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** What a Trakt history entry means, before anything is looked up. */
class TraktHistoryTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final Instant WATCHED = Instant.parse("2026-09-11T22:30:00Z");

    @Test
    @DisplayName("a film keeps its own year and IMDb id")
    void readsAMovie() {
        ImportRow row = TraktHistory.asRow(new TraktResponses.HistoryItem(
                981_231L, WATCHED, "scrobble", "movie",
                new TraktResponses.Movie("Heat", 1995,
                        new TraktResponses.Ids(6L, "heat-1995", "tt0113277", 949L)),
                null, null), ZURICH).orElseThrow();

        assertThat(row.titleText()).isEqualTo("Heat");
        assertThat(row.year()).isEqualTo(1995);
        assertThat(row.imdbId()).isEqualTo("tt0113277");
        assertThat(row.titleType()).isEqualTo(TitleType.MOVIE);
        assertThat(row.sourceRef()).isEqualTo("trakt:981231");
    }

    @Test
    @DisplayName("an episode is identified by the show's id and year, not its own")
    void readsAnEpisode() {
        ImportRow row = TraktHistory.asRow(episode(), ZURICH).orElseThrow();

        // The contrast with Plex, which is the whole reason this mapping is
        // shorter: Trakt hands over the series' IMDb id and the series' year
        // alongside the episode, and title.imdb_id holds exactly that.
        assertThat(row.titleText()).isEqualTo("Severance");
        assertThat(row.year()).isEqualTo(2022);
        assertThat(row.imdbId()).isEqualTo("tt11280740");
        assertThat(row.seasonNumber()).isEqualTo(1);
        assertThat(row.episodeNumber()).isEqualTo(3);
        assertThat(row.episodeName()).isEqualTo("In Perpetuity");
        assertThat(row.titleType()).isEqualTo(TitleType.TV_SERIES);
        assertThat(row.isEpisode()).isTrue();
    }

    @Test
    @DisplayName("the date on the row is the user's own, not UTC's")
    void datesInTheUsersZone() {
        // 22:30 UTC is already the next day in Zurich. The row's date is what
        // a person would say they watched it on, and that is a local question.
        ImportRow row = TraktHistory.asRow(episode(), ZURICH).orElseThrow();

        assertThat(row.watchedAt()).isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    @DisplayName("an id that is not an IMDb id is not used as one")
    void ignoresNonImdbIds() {
        ImportRow row = TraktHistory.asRow(new TraktResponses.HistoryItem(
                7L, WATCHED, "watch", "movie",
                new TraktResponses.Movie("Heat", 1995,
                        new TraktResponses.Ids(6L, "heat-1995", "", 949L)),
                null, null), ZURICH).orElseThrow();

        assertThat(row.imdbId()).isNull();
    }

    @Test
    @DisplayName("anything that is not a film or an episode is not a row")
    void ignoresOtherTypes() {
        assertThat(TraktHistory.asRow(new TraktResponses.HistoryItem(
                1L, WATCHED, "scrobble", "season", null, null, null), ZURICH)).isEmpty();
        assertThat(TraktHistory.asRow(new TraktResponses.HistoryItem(
                1L, WATCHED, "scrobble", "movie", null, null, null), ZURICH)).isEmpty();
        assertThat(TraktHistory.asRow(null, ZURICH)).isEmpty();
    }

    @Test
    @DisplayName("an episode with no show is not filed under nothing")
    void ignoresAnEpisodeWithNoShow() {
        assertThat(TraktHistory.asRow(new TraktResponses.HistoryItem(
                1L, WATCHED, "scrobble", "episode", null,
                new TraktResponses.Episode(1, 3, "In Perpetuity", null), null), ZURICH)).isEmpty();
    }

    @Test
    @DisplayName("identity is Trakt's own history id, so a rewatch is a new viewing")
    void identifiesAViewingByHistoryId() {
        assertThat(TraktHistory.originRef(981_231L)).isEqualTo("trakt:981231");
        assertThat(TraktHistory.originRef(981_232L)).isNotEqualTo(TraktHistory.originRef(981_231L));
    }

    private TraktResponses.HistoryItem episode() {
        return new TraktResponses.HistoryItem(
                55L, WATCHED, "scrobble", "episode", null,
                new TraktResponses.Episode(1, 3, "In Perpetuity",
                        new TraktResponses.Ids(9L, null, "tt13148456", 3_142L)),
                new TraktResponses.Show("Severance", 2022,
                        new TraktResponses.Ids(4L, "severance", "tt11280740", 95_396L)));
    }
}
