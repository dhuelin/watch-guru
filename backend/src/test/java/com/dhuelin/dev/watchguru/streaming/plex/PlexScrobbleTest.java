package com.dhuelin.dev.watchguru.streaming.plex;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a Plex delivery means, before anything is looked up.
 *
 * <p>The payloads here are trimmed copies of real ones. Plex sends far more
 * than this and adds fields between releases, which is the first thing these
 * tests pin: an unknown field is ignored, not an error.
 */
class PlexScrobbleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 11);

    private final JsonMapper json = JsonMapper.builder().build();

    private PlexWebhookPayload parse(String payload) {
        return json.readValue(payload, PlexWebhookPayload.class);
    }

    @Test
    @DisplayName("a film scrobble keeps its year and its IMDb id")
    void readsAMovie() {
        PlexWebhookPayload payload = parse("""
                {
                  "event": "media.scrobble",
                  "user": true,
                  "owner": true,
                  "somethingPlexAddedLater": {"nested": true},
                  "Account": {"id": 1, "title": "denis"},
                  "Metadata": {
                    "type": "movie",
                    "title": "Heat",
                    "year": 1995,
                    "ratingKey": "9021",
                    "guid": "plex://movie/5d776826",
                    "Guid": [{"id": "imdb://tt0113277"}, {"id": "tmdb://949"}]
                  }
                }
                """);

        ImportRow row = PlexScrobble.asRow(payload, TODAY).orElseThrow();

        assertThat(row.titleText()).isEqualTo("Heat");
        assertThat(row.year()).isEqualTo(1995);
        assertThat(row.imdbId()).isEqualTo("tt0113277");
        assertThat(row.titleType()).isEqualTo(TitleType.MOVIE);
        assertThat(row.isEpisode()).isFalse();
        assertThat(row.watchedAt()).isEqualTo(TODAY);
        assertThat(row.sourceRef()).isEqualTo("plex:9021:2026-09-11");
    }

    @Test
    @DisplayName("an episode is filed under its series, with the season and episode numbers")
    void readsAnEpisode() {
        ImportRow row = PlexScrobble.asRow(episodePayload(), TODAY).orElseThrow();

        assertThat(row.titleText()).isEqualTo("Breaking Bad");
        assertThat(row.seasonNumber()).isEqualTo(5);
        assertThat(row.episodeNumber()).isEqualTo(14);
        assertThat(row.episodeName()).isEqualTo("Ozymandias");
        assertThat(row.titleType()).isEqualTo(TitleType.TV_SERIES);
        assertThat(row.isEpisode()).isTrue();
    }

    @Test
    @DisplayName("an episode's year and guid are dropped: they are the episode's, not the series'")
    void doesNotMistakeEpisodeIdentityForSeriesIdentity() {
        ImportRow row = PlexScrobble.asRow(episodePayload(), TODAY).orElseThrow();

        // Plex says 2013, which is when this episode aired; Breaking Bad began
        // in 2008. Passing the year through would ask the catalogue for a
        // series that does not exist. The tt-id is the episode's for the same
        // reason -- title.imdb_id holds the series'.
        assertThat(row.year()).isNull();
        assertThat(row.imdbId()).isNull();
    }

    @Test
    @DisplayName("music, photos and trailers are not this app's business")
    void ignoresOtherLibraryTypes() {
        PlexWebhookPayload payload = parse("""
                {
                  "event": "media.scrobble",
                  "user": true,
                  "Metadata": {"type": "track", "title": "Teardrop", "ratingKey": "77"}
                }
                """);

        assertThat(PlexScrobble.asRow(payload, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("an episode with no series name is not a row")
    void ignoresAnEpisodeItCannotName() {
        PlexWebhookPayload payload = parse("""
                {
                  "event": "media.scrobble",
                  "user": true,
                  "Metadata": {"type": "episode", "title": "Pilot", "index": 1, "ratingKey": "8"}
                }
                """);

        assertThat(PlexScrobble.asRow(payload, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("play and pause are not claims that anything was watched")
    void onlyScrobbleCounts() {
        PlexWebhookPayload payload = parse("""
                {"event": "media.play", "user": true, "Metadata": {"type": "movie", "title": "Heat"}}
                """);

        assertThat(payload.isScrobble()).isFalse();
    }

    @Test
    @DisplayName("identity is the rating key and the day, so a replay is the same viewing")
    void identifiesAViewingByKeyAndDay() {
        assertThat(PlexScrobble.originRef("9021", TODAY)).isEqualTo("plex:9021:2026-09-11");
        assertThat(PlexScrobble.originRef("9021", TODAY.plusDays(1)))
                .isNotEqualTo(PlexScrobble.originRef("9021", TODAY));
        assertThat(PlexScrobble.originRef(null, TODAY)).isEqualTo("plex:unknown:2026-09-11");
    }

    @Test
    @DisplayName("a payload with no metadata at all is not a row")
    void toleratesAnEmptyPayload() {
        assertThat(PlexScrobble.asRow(parse("{\"event\": \"media.scrobble\"}"), TODAY)).isEmpty();
    }

    private PlexWebhookPayload episodePayload() {
        return parse("""
                {
                  "event": "media.scrobble",
                  "user": true,
                  "owner": true,
                  "Account": {"id": 1, "title": "denis"},
                  "Server": {"title": "basement", "uuid": "abc"},
                  "Metadata": {
                    "type": "episode",
                    "title": "Ozymandias",
                    "grandparentTitle": "Breaking Bad",
                    "parentTitle": "Season 5",
                    "index": 14,
                    "parentIndex": 5,
                    "year": 2013,
                    "ratingKey": "45121",
                    "guid": "plex://episode/5d9c0",
                    "Guid": [{"id": "imdb://tt2301455"}, {"id": "tvdb://4588780"}]
                  }
                }
                """);
    }
}
