package com.dhuelin.dev.watchguru.streaming.mediaserver;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What each media server's delivery means, before anything is looked up.
 *
 * <p>The payloads here are trimmed copies of real ones. All three servers send
 * considerably more and add fields between releases, which is the first thing
 * these pin down: an unknown field is ignored, not an error.
 *
 * <p>The three adapters agree on the two rules that matter and differ in
 * everything else, so each rule is tested against each server rather than once
 * against whichever was written first.
 */
class MediaServerAdapterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

    private final JsonMapper json = JsonMapper.builder().build();

    @Nested
    @DisplayName("Plex")
    class Plex {

        private final PlexAdapter adapter = new PlexAdapter(json);

        @Test
        @DisplayName("a film scrobble keeps its year and its IMDb id")
        void readsAMovie() {
            MediaServerEvent event = adapter.read("""
                    {
                      "event": "media.scrobble",
                      "user": true,
                      "somethingPlexAddedLater": {"nested": true},
                      "Account": {"id": 1, "title": "denis"},
                      "Metadata": {
                        "type": "movie", "title": "Heat", "year": 1995, "ratingKey": "9021",
                        "Guid": [{"id": "imdb://tt0113277"}, {"id": "tmdb://949"}]
                      }
                    }
                    """, TODAY).orElseThrow();

            ImportRow row = event.row();
            assertThat(row.titleText()).isEqualTo("Heat");
            assertThat(row.year()).isEqualTo(1995);
            assertThat(row.imdbId()).isEqualTo("tt0113277");
            assertThat(row.titleType()).isEqualTo(TitleType.MOVIE);
            assertThat(row.sourceRef()).isEqualTo("plex:9021:2026-09-13");
            assertThat(event.accountName()).isEqualTo("denis");
            assertThat(event.webhookOwners()).isTrue();
        }

        @Test
        @DisplayName("an episode is filed under its series, by season and number")
        void readsAnEpisode() {
            ImportRow row = adapter.read(episode(), TODAY).orElseThrow().row();

            assertThat(row.titleText()).isEqualTo("Breaking Bad");
            assertThat(row.seasonNumber()).isEqualTo(5);
            assertThat(row.episodeNumber()).isEqualTo(14);
            assertThat(row.episodeName()).isEqualTo("Ozymandias");
            assertThat(row.isEpisode()).isTrue();
        }

        @Test
        @DisplayName("an episode's year and guid are dropped: they are the episode's")
        void dropsEpisodeIdentity() {
            ImportRow row = adapter.read(episode(), TODAY).orElseThrow().row();

            // Plex says 2013, which is when this episode aired; Breaking Bad
            // began in 2008. The tt-id is the episode's for the same reason.
            assertThat(row.year()).isNull();
            assertThat(row.imdbId()).isNull();
        }

        @Test
        @DisplayName("play and pause are not claims that anything was watched")
        void onlyScrobbleCounts() {
            for (String event : new String[] {"media.play", "media.pause", "media.resume",
                    "media.stop", "media.rate"}) {
                assertThat(adapter.read(episode().replace("media.scrobble", event), TODAY))
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("music and photos are not this app's business")
        void ignoresOtherLibraryTypes() {
            assertThat(adapter.read("""
                    {"event": "media.scrobble", "user": true,
                     "Metadata": {"type": "track", "title": "Teardrop", "ratingKey": "77"}}
                    """, TODAY)).isEmpty();
        }

        @Test
        @DisplayName("Plex is the one server that can be connected without a username")
        void doesNotRequireAnAccountName() {
            // It sends a flag saying whether the account is the webhook's own,
            // which the other two do not.
            assertThat(adapter.requiresAccountName()).isFalse();
        }

        private String episode() {
            return """
                    {
                      "event": "media.scrobble", "user": true, "owner": true,
                      "Account": {"id": 1, "title": "denis"},
                      "Metadata": {
                        "type": "episode", "title": "Ozymandias",
                        "grandparentTitle": "Breaking Bad", "parentIndex": 5, "index": 14,
                        "year": 2013, "ratingKey": "45121",
                        "Guid": [{"id": "imdb://tt2301455"}]
                      }
                    }
                    """;
        }
    }

    @Nested
    @DisplayName("Jellyfin")
    class Jellyfin {

        private final JellyfinAdapter adapter = new JellyfinAdapter(json);

        @Test
        @DisplayName("a stop that played to completion is a viewing")
        void readsAMovie() {
            MediaServerEvent event = adapter.read("""
                    {
                      "NotificationType": "PlaybackStop",
                      "NotificationUsername": "denis",
                      "ItemType": "Movie", "ItemId": "abc123",
                      "Name": "Heat", "Year": "1995",
                      "Provider_imdb": "tt0113277",
                      "PlayedToCompletion": "True"
                    }
                    """, TODAY).orElseThrow();

            // Jellyfin's default template quotes everything, so "1995" has to
            // read as 1995 and "True" as true.
            assertThat(event.row().year()).isEqualTo(1995);
            assertThat(event.row().imdbId()).isEqualTo("tt0113277");
            assertThat(event.row().sourceRef()).isEqualTo("jellyfin:abc123:2026-09-13");
            assertThat(event.accountName()).isEqualTo("denis");
            assertThat(event.webhookOwners()).isNull();
        }

        @Test
        @DisplayName("a stop halfway through is somebody giving up, not watching")
        void ignoresAnUnfinishedStop() {
            assertThat(adapter.read("""
                    {"NotificationType": "PlaybackStop", "NotificationUsername": "denis",
                     "ItemType": "Movie", "ItemId": "abc", "Name": "Heat",
                     "PlayedToCompletion": "False"}
                    """, TODAY)).isEmpty();
        }

        @Test
        @DisplayName("starting something is not watching it either")
        void ignoresPlaybackStart() {
            assertThat(adapter.read("""
                    {"NotificationType": "PlaybackStart", "NotificationUsername": "denis",
                     "ItemType": "Movie", "ItemId": "abc", "Name": "Heat",
                     "PlayedToCompletion": "True"}
                    """, TODAY)).isEmpty();
        }

        @Test
        @DisplayName("an episode drops its own year and id, exactly as Plex's does")
        void dropsEpisodeIdentity() {
            ImportRow row = adapter.read("""
                    {
                      "NotificationType": "PlaybackStop", "NotificationUsername": "denis",
                      "ItemType": "Episode", "ItemId": "ep1",
                      "Name": "Ozymandias", "Year": 2013,
                      "SeriesName": "Breaking Bad", "SeasonNumber": 5, "EpisodeNumber": 14,
                      "Provider_imdb": "tt2301455",
                      "PlayedToCompletion": true
                    }
                    """, TODAY).orElseThrow().row();

            assertThat(row.titleText()).isEqualTo("Breaking Bad");
            assertThat(row.seasonNumber()).isEqualTo(5);
            assertThat(row.episodeNumber()).isEqualTo(14);
            assertThat(row.year()).isNull();
            assertThat(row.imdbId()).isNull();
        }

        @Test
        @DisplayName("a username is required, because this webhook is the server's")
        void requiresAnAccountName() {
            assertThat(adapter.requiresAccountName()).isTrue();
        }
    }

    @Nested
    @DisplayName("Emby")
    class Emby {

        private final EmbyAdapter adapter = new EmbyAdapter(json);

        @Test
        @DisplayName("a completed playback.stop is a viewing")
        void readsAMovie() {
            MediaServerEvent event = adapter.read("""
                    {
                      "Event": "playback.stop",
                      "User": {"Name": "denis", "Id": "u1"},
                      "Item": {
                        "Name": "Heat", "Type": "Movie", "ProductionYear": 1995, "Id": "i1",
                        "ProviderIds": {"Imdb": "tt0113277", "Tmdb": "949"}
                      },
                      "PlaybackInfo": {"PlayedToCompletion": true}
                    }
                    """, TODAY).orElseThrow();

            assertThat(event.row().titleText()).isEqualTo("Heat");
            assertThat(event.row().year()).isEqualTo(1995);
            assertThat(event.row().imdbId()).isEqualTo("tt0113277");
            assertThat(event.row().sourceRef()).isEqualTo("emby:i1:2026-09-13");
            assertThat(event.accountName()).isEqualTo("denis");
        }

        @Test
        @DisplayName("an episode uses the nested names Emby gives it")
        void readsAnEpisode() {
            ImportRow row = adapter.read("""
                    {
                      "Event": "playback.stop",
                      "User": {"Name": "denis"},
                      "Item": {
                        "Name": "Ozymandias", "Type": "Episode", "ProductionYear": 2013,
                        "SeriesName": "Breaking Bad", "ParentIndexNumber": 5, "IndexNumber": 14,
                        "Id": "i2", "ProviderIds": {"Imdb": "tt2301455"}
                      },
                      "PlaybackInfo": {"PlayedToCompletion": true}
                    }
                    """, TODAY).orElseThrow().row();

            assertThat(row.titleText()).isEqualTo("Breaking Bad");
            assertThat(row.seasonNumber()).isEqualTo(5);
            assertThat(row.episodeNumber()).isEqualTo(14);
            assertThat(row.year()).isNull();
            assertThat(row.imdbId()).isNull();
        }

        @Test
        @DisplayName("an unfinished stop is not a viewing")
        void ignoresAnUnfinishedStop() {
            assertThat(adapter.read("""
                    {"Event": "playback.stop", "User": {"Name": "denis"},
                     "Item": {"Name": "Heat", "Type": "Movie", "Id": "i1"},
                     "PlaybackInfo": {"PlayedToCompletion": false}}
                    """, TODAY)).isEmpty();
        }

        @Test
        @DisplayName("other events are ignored")
        void ignoresOtherEvents() {
            assertThat(adapter.read("""
                    {"Event": "playback.start", "User": {"Name": "denis"},
                     "Item": {"Name": "Heat", "Type": "Movie", "Id": "i1"},
                     "PlaybackInfo": {"PlayedToCompletion": true}}
                    """, TODAY)).isEmpty();
        }
    }

    /**
     * The rules that hold whichever server called.
     *
     * <p>In a nested class rather than on the outer one because that is where
     * the runner actually executes them: a test method beside {@code @Nested}
     * classes was silently contributing nothing to the count, which is the one
     * kind of test failure nobody notices.
     */
    @Nested
    @DisplayName("every server")
    class EveryServer {

        @Test
        @DisplayName("something that is not JSON is refused rather than half-read")
        void refusesUnreadablePayloads() {
            for (MediaServerAdapter adapter : new MediaServerAdapter[] {
                    new PlexAdapter(json), new JellyfinAdapter(json), new EmbyAdapter(json)}) {
                assertThatThrownBy(() -> adapter.read("this is not json", TODAY))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        @DisplayName("a viewing is identified by the item and the day, so a replay is the same viewing")
        void identifiesAViewingByItemAndDay() {
            assertThat(MediaServerRows.originRef("plex", "9021", TODAY))
                    .isEqualTo("plex:9021:2026-09-13");
            assertThat(MediaServerRows.originRef("plex", "9021", TODAY.plusDays(1)))
                    .isNotEqualTo(MediaServerRows.originRef("plex", "9021", TODAY));
            // Two servers could use the same item id; the slug keeps them apart.
            assertThat(MediaServerRows.originRef("emby", "9021", TODAY))
                    .isNotEqualTo(MediaServerRows.originRef("plex", "9021", TODAY));
            assertThat(MediaServerRows.originRef("plex", null, TODAY))
                    .isEqualTo("plex:unknown:2026-09-13");
        }
    }
}
