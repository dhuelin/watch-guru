package com.dhuelin.dev.watchguru.streaming.plex;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.SeasonRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.domain.SyncRun;
import com.dhuelin.dev.watchguru.streaming.domain.SyncStatus;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.streaming.repository.SyncRunRepository;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchEvent;
import com.dhuelin.dev.watchguru.tracking.domain.WatchOrigin;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A Plex server saying somebody watched something, end to end (#39).
 *
 * <p>The acceptance criteria on the issue are all about what happens when it
 * does not go cleanly: a replayed delivery writes nothing twice, a title the
 * catalogue does not hold is reported rather than dropped, a disconnected link
 * stops working, and somebody else's viewing on a shared server never lands in
 * this user's history. Each of those is a test here.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class PlexWebhookIntegrationTest {

    @Autowired
    private PlexLinkService links;
    @Autowired
    private PlexWebhookService webhooks;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TitleRepository titles;
    @Autowired
    private SeasonRepository seasons;
    @Autowired
    private EpisodeRepository episodes;
    @Autowired
    private WatchEventRepository watchEvents;
    @Autowired
    private WatchlistItemRepository items;
    @Autowired
    private LinkedStreamingAccountRepository accounts;
    @Autowired
    private SyncRunRepository syncRuns;
    @Autowired
    private StreamingServiceRepository services;

    private AppUser user;
    private Title series;
    private Title film;
    private String seriesName;
    private String filmName;
    private String token;

    /** Names are unique per test: the suite may share a database. */
    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        user = users.save(new AppUser("plex-" + seed + "@example.com", "Plex watcher"));
        seriesName = "Severance " + seed;
        filmName = "Heat " + seed;

        series = titles.save(new Title(6_000_000L + seed % 100_000, TitleType.TV_SERIES, seriesName));
        Season season = seasons.save(new Season(series, 1));
        Episode episode = new Episode(season, 3);
        episode.setName("In Perpetuity");
        episode.setAirDate(LocalDate.of(2022, 2, 25));
        episodes.save(episode);

        film = new Title(6_100_000L + seed % 100_000, TitleType.MOVIE, filmName);
        film.setImdbId("tt" + seed);
        film.setReleaseDate(LocalDate.of(1995, 12, 15));
        film.setRuntimeMinutes(170);
        film = titles.save(film);

        token = links.connect(user, "denis").token();
    }

    @Test
    @DisplayName("watching an episode on Plex records it without anybody opening the app")
    void recordsAnEpisode() {
        PlexWebhookService.Outcome outcome = webhooks.receive(token, episodeScrobble("denis"));

        assertThat(outcome).isEqualTo(PlexWebhookService.Outcome.RECORDED);

        List<WatchEvent> events = eventsOf(user);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getOrigin()).isEqualTo(WatchOrigin.STREAMING_SYNC);
            assertThat(event.getOriginRef()).startsWith("plex:45121:");
            assertThat(event.getEpisode()).isNotNull();
            assertThat(event.getTitle().getId()).isEqualTo(series.getId());
            // The event says where it came from, so the statistics screen can
            // count Plex alongside everything else.
            assertThat(event.getStreamingService()).isNotNull();
            assertThat(event.getStreamingService().getId()).isEqualTo(plexServiceId());
        });

        // Started on Plex, so it is in the library afterwards rather than only
        // in the statistics.
        assertThat(items.findByUserIdAndTitleId(user.getId(), series.getId())).isPresent();
    }

    @Test
    @DisplayName("a film scrobble matches on its own name and year")
    void recordsAFilm() {
        PlexWebhookService.Outcome outcome = webhooks.receive(token, """
                {
                  "event": "media.scrobble",
                  "user": true,
                  "Account": {"id": 1, "title": "denis"},
                  "Metadata": {"type": "movie", "title": "%s", "year": 1995, "ratingKey": "77"}
                }
                """.formatted(filmName));

        assertThat(outcome).isEqualTo(PlexWebhookService.Outcome.RECORDED);
        assertThat(eventsOf(user)).singleElement()
                .satisfies(event -> assertThat(event.getTitle().getId()).isEqualTo(film.getId()));
    }

    @Test
    @DisplayName("the same delivery twice records one viewing")
    void replayingADeliveryWritesNothingTwice() {
        webhooks.receive(token, episodeScrobble("denis"));

        PlexWebhookService.Outcome second = webhooks.receive(token, episodeScrobble("denis"));

        assertThat(second).isEqualTo(PlexWebhookService.Outcome.DUPLICATE);
        assertThat(eventsOf(user)).hasSize(1);
        assertThat(runs()).anySatisfy(run -> assertThat(run.getItemsSkipped()).isEqualTo(1));
    }

    @Test
    @DisplayName("only a scrobble counts; play, pause and stop do not")
    void ignoresEverythingButScrobble() {
        for (String event : List.of("media.play", "media.pause", "media.resume", "media.stop", "media.rate")) {
            PlexWebhookService.Outcome outcome = webhooks.receive(token,
                    episodeScrobble("denis").replace("media.scrobble", event));

            assertThat(outcome).isEqualTo(PlexWebhookService.Outcome.IGNORED);
        }
        assertThat(eventsOf(user)).isEmpty();
        // Not even a sync run: a play event is not a failed import, it is not
        // an import at all, and a row per pause would bury the ones that matter.
        assertThat(runs()).isEmpty();
    }

    @Test
    @DisplayName("a housemate's viewing on the same server is not this user's history")
    void ignoresAnotherAccountOnASharedServer() {
        PlexWebhookService.Outcome outcome = webhooks.receive(token, episodeScrobble("the housemate"));

        assertThat(outcome).isEqualTo(PlexWebhookService.Outcome.IGNORED);
        assertThat(eventsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("a title the catalogue does not hold is reported on the link, not dropped")
    void reportsWhatItCouldNotMatch() {
        PlexWebhookService.Outcome outcome = webhooks.receive(token, """
                {
                  "event": "media.scrobble",
                  "user": true,
                  "Account": {"id": 1, "title": "denis"},
                  "Metadata": {"type": "movie", "title": "A Film Nobody Has Heard Of %d",
                               "year": 1977, "ratingKey": "99"}
                }
                """.formatted(System.nanoTime()));

        assertThat(outcome).isEqualTo(PlexWebhookService.Outcome.UNMATCHED);
        assertThat(eventsOf(user)).isEmpty();
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo(SyncStatus.PARTIAL);
            assertThat(run.getItemsFailed()).isEqualTo(1);
            assertThat(run.getErrorMessage()).isNotBlank();
        });

        LinkedStreamingAccount link = accounts.findById(linkId()).orElseThrow();
        assertThat(link.getLastSyncError()).isNotBlank();
        // The connection itself is fine -- it delivered. Saying otherwise would
        // send the user to re-paste a URL that works.
        assertThat(link.getStatus()).isEqualTo(LinkStatus.CONNECTED);
        assertThat(link.getLastSyncAt()).isNotNull();
    }

    @Test
    @DisplayName("an episode the catalogue has never fetched is reported, not filed under the series")
    void doesNotRecordASeriesForAnUnknownEpisode() {
        PlexWebhookService.Outcome outcome = webhooks.receive(token,
                episodeScrobble("denis").replace("\"index\": 3", "\"index\": 99"));

        assertThat(outcome).isEqualTo(PlexWebhookService.Outcome.UNMATCHED);
        assertThat(eventsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("a token for no link, a forged secret and a disconnected link all fail the same way")
    void rejectsTokensThatOpenNothing() {
        assertThatThrownBy(() -> webhooks.receive("999999.nonsense", episodeScrobble("denis")))
                .isInstanceOf(WebhookAuthenticationException.class);

        String forged = linkId() + "." + WebhookToken.issue(linkId()).split("\\.")[1];
        assertThatThrownBy(() -> webhooks.receive(forged, episodeScrobble("denis")))
                .isInstanceOf(WebhookAuthenticationException.class);

        links.disconnect(user);
        assertThatThrownBy(() -> webhooks.receive(token, episodeScrobble("denis")))
                .isInstanceOf(WebhookAuthenticationException.class);
        assertThat(eventsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("reconnecting issues a new URL and retires the old one")
    void reconnectingRetiresThePreviousUrl() {
        String replacement = links.connect(user, "denis").token();

        assertThat(replacement).isNotEqualTo(token);
        assertThatThrownBy(() -> webhooks.receive(token, episodeScrobble("denis")))
                .isInstanceOf(WebhookAuthenticationException.class);
        assertThat(webhooks.receive(replacement, episodeScrobble("denis")))
                .isEqualTo(PlexWebhookService.Outcome.RECORDED);
    }

    @Test
    @DisplayName("a payload that is not JSON is refused rather than half-read")
    void refusesUnreadablePayloads() {
        assertThatThrownBy(() -> webhooks.receive(token, "this is not json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(eventsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("without a Plex username, only the webhook owner's own viewing counts")
    void fallsBackToThePlexUserFlag() {
        String anyAccount = links.connect(user, null).token();

        assertThat(webhooks.receive(anyAccount, episodeScrobble("whoever").replace("\"user\": true",
                "\"user\": false")))
                .isEqualTo(PlexWebhookService.Outcome.IGNORED);
        assertThat(webhooks.receive(anyAccount, episodeScrobble("whoever")))
                .isEqualTo(PlexWebhookService.Outcome.RECORDED);
    }

    private Long plexServiceId() {
        return services.findBySlug("plex").orElseThrow().getId();
    }

    private Long linkId() {
        return links.find(user).orElseThrow().getId();
    }

    private List<SyncRun> runs() {
        return syncRuns.findTop10ByLinkedAccountIdOrderByStartedAtDesc(linkId());
    }

    private List<WatchEvent> eventsOf(AppUser who) {
        return watchEvents.findByUserIdOrderByWatchedAtDesc(who.getId(), PageRequest.of(0, 50)).getContent();
    }

    private String episodeScrobble(String account) {
        return """
                {
                  "event": "media.scrobble",
                  "user": true,
                  "owner": true,
                  "Account": {"id": 1, "title": "%s"},
                  "Server": {"title": "basement", "uuid": "abc"},
                  "Metadata": {
                    "type": "episode",
                    "title": "In Perpetuity",
                    "grandparentTitle": "%s",
                    "parentIndex": 1,
                    "index": 3,
                    "year": 2022,
                    "ratingKey": "45121"
                  }
                }
                """.formatted(account, seriesName);
    }
}
