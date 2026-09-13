package com.dhuelin.dev.watchguru.streaming.mediaserver;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * A media server saying somebody watched something, end to end (#39).
 *
 * <p>Every rule is exercised against all three servers rather than once against
 * Plex, because the point of the shared shape is that they behave identically
 * once the payload is read -- and a claim like that is worth checking rather
 * than asserting in a comment.
 *
 * <p>The acceptance criteria on the issue are all about what happens when it
 * does not go cleanly: a replayed delivery writes nothing twice, a title the
 * catalogue does not hold is reported rather than dropped, a disconnected link
 * stops working, and somebody else's viewing on a shared server never lands in
 * this user's history.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class MediaServerWebhookIntegrationTest {

    @Autowired
    private MediaServerLinkService links;
    @Autowired
    private MediaServerWebhookService webhooks;
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
    private String seriesName;

    /** Names are unique per test: the suite may share a database. */
    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        user = users.save(new AppUser("server-" + seed + "@example.com", "Server watcher"));
        seriesName = "Severance " + seed;

        series = titles.save(new Title(8_000_000L + seed % 100_000, TitleType.TV_SERIES, seriesName));
        Season season = seasons.save(new Season(series, 1));
        Episode episode = new Episode(season, 3);
        episode.setName("In Perpetuity");
        episode.setAirDate(LocalDate.of(2022, 2, 25));
        episodes.save(episode);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"plex", "jellyfin", "emby"})
    @DisplayName("watching an episode records it without anybody opening the app")
    void recordsAnEpisode(String service) {
        String token = connect(service);

        assertThat(webhooks.receive(service, token, episodePayload(service, "denis")))
                .isEqualTo(MediaServerWebhookService.Outcome.RECORDED);

        assertThat(eventsOf(user)).singleElement().satisfies(event -> {
            assertThat(event.getOrigin()).isEqualTo(WatchOrigin.STREAMING_SYNC);
            assertThat(event.getOriginRef()).startsWith(service + ":");
            assertThat(event.getEpisode()).isNotNull();
            assertThat(event.getTitle().getId()).isEqualTo(series.getId());
            assertThat(event.getStreamingService().getId()).isEqualTo(serviceId(service));
        });
        // Started here, so it is in the library afterwards rather than only in
        // the statistics.
        assertThat(items.findByUserIdAndTitleId(user.getId(), series.getId())).isPresent();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"plex", "jellyfin", "emby"})
    @DisplayName("the same delivery twice records one viewing")
    void replayingADeliveryWritesNothingTwice(String service) {
        String token = connect(service);
        webhooks.receive(service, token, episodePayload(service, "denis"));

        assertThat(webhooks.receive(service, token, episodePayload(service, "denis")))
                .isEqualTo(MediaServerWebhookService.Outcome.DUPLICATE);
        assertThat(eventsOf(user)).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"plex", "jellyfin", "emby"})
    @DisplayName("a housemate's viewing on the same server is not this user's history")
    void ignoresAnotherAccount(String service) {
        String token = connect(service);

        assertThat(webhooks.receive(service, token, episodePayload(service, "the housemate")))
                .isEqualTo(MediaServerWebhookService.Outcome.IGNORED);
        assertThat(eventsOf(user)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"plex", "jellyfin", "emby"})
    @DisplayName("only a finished viewing counts; starting and pausing do not")
    void ignoresEverythingButAFinishedViewing(String service) {
        String token = connect(service);

        for (String payload : unfinishedPayloads(service)) {
            assertThat(webhooks.receive(service, token, payload))
                    .isEqualTo(MediaServerWebhookService.Outcome.IGNORED);
        }
        assertThat(eventsOf(user)).isEmpty();
        // Not even a sync run: a pause is not a failed import, it is not an
        // import at all, and a row per pause would bury the ones that matter.
        assertThat(runs(service)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"plex", "jellyfin", "emby"})
    @DisplayName("a title the catalogue does not hold is reported on the link, not dropped")
    void reportsWhatItCouldNotMatch(String service) {
        String token = connect(service);
        String unknown = "A Film Nobody Has Heard Of " + System.nanoTime();

        assertThat(webhooks.receive(service, token, moviePayload(service, "denis", unknown)))
                .isEqualTo(MediaServerWebhookService.Outcome.UNMATCHED);

        assertThat(eventsOf(user)).isEmpty();
        assertThat(runs(service)).singleElement().satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo(SyncStatus.PARTIAL);
            assertThat(run.getItemsFailed()).isEqualTo(1);
            assertThat(run.getErrorMessage()).isNotBlank();
        });
        LinkedStreamingAccount link = link(service);
        assertThat(link.getLastSyncError()).isNotBlank();
        // The connection itself is fine -- it delivered. Saying otherwise would
        // send the user to re-paste a URL that works.
        assertThat(link.getStatus()).isEqualTo(LinkStatus.CONNECTED);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"plex", "jellyfin", "emby"})
    @DisplayName("a forged token, and a disconnected link, both fail the same way")
    void rejectsTokensThatOpenNothing(String service) {
        String token = connect(service);
        long id = link(service).getId();
        String forged = id + "." + WebhookToken.issue(id).split("\\.")[1];

        assertThatThrownBy(() -> webhooks.receive(service, forged, episodePayload(service, "denis")))
                .isInstanceOf(WebhookAuthenticationException.class);

        links.disconnect(user, service);
        assertThatThrownBy(() -> webhooks.receive(service, token, episodePayload(service, "denis")))
                .isInstanceOf(WebhookAuthenticationException.class);
        assertThat(eventsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("a token for one server does not open another")
    void tokensAreNotInterchangeable() {
        String plexToken = connect("plex");

        // Otherwise a Jellyfin payload would be filtered against Plex's idea of
        // a username, which is a different thing wearing the same name.
        assertThatThrownBy(() ->
                webhooks.receive("jellyfin", plexToken, episodePayload("jellyfin", "denis")))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    @DisplayName("Jellyfin and Emby refuse to connect without the username to filter on")
    void insistOnAnAccountName() {
        for (String service : List.of("jellyfin", "emby")) {
            assertThatThrownBy(() -> links.connect(user, service, null))
                    .isInstanceOf(MediaServerLinkService.NotAvailableException.class)
                    .hasMessageContaining("username");
        }
        // Plex does not, because it says whose account played something.
        assertThat(links.connect(user, "plex", null).token()).isNotBlank();
    }

    @Test
    @DisplayName("without a Plex username, only the webhook owner's own viewing counts")
    void plexFallsBackToItsOwnFlag() {
        String token = links.connect(user, "plex", null).token();

        assertThat(webhooks.receive("plex", token,
                episodePayload("plex", "whoever").replace("\"user\": true", "\"user\": false")))
                .isEqualTo(MediaServerWebhookService.Outcome.IGNORED);
        assertThat(webhooks.receive("plex", token, episodePayload("plex", "whoever")))
                .isEqualTo(MediaServerWebhookService.Outcome.RECORDED);
    }

    @Test
    @DisplayName("a payload that is not JSON is refused rather than half-read")
    void refusesUnreadablePayloads() {
        String token = connect("emby");

        assertThatThrownBy(() -> webhooks.receive("emby", token, "this is not json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(eventsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("a server this build has no adapter for is not found, rather than a 500")
    void unknownServiceIsNotFound() {
        assertThatThrownBy(() -> webhooks.receive("kodi", "1.secret", "{}"))
                .isInstanceOf(com.dhuelin.dev.watchguru.common.NotFoundException.class);
    }

    private String connect(String service) {
        return links.connect(user, service, "denis").token();
    }

    private LinkedStreamingAccount link(String service) {
        return links.find(user, service).orElseThrow();
    }

    private Long serviceId(String service) {
        return services.findBySlug(service).orElseThrow().getId();
    }

    private List<SyncRun> runs(String service) {
        return syncRuns.findTop10ByLinkedAccountIdOrderByStartedAtDesc(link(service).getId());
    }

    private List<WatchEvent> eventsOf(AppUser who) {
        return watchEvents.findByUserIdOrderByWatchedAtDesc(who.getId(), PageRequest.of(0, 50))
                .getContent();
    }

    /** The same claim -- this person finished this episode -- in three dialects. */
    private String episodePayload(String service, String account) {
        return switch (service) {
            case "plex" -> """
                    {
                      "event": "media.scrobble", "user": true,
                      "Account": {"title": "%s"},
                      "Metadata": {"type": "episode", "title": "In Perpetuity",
                                   "grandparentTitle": "%s", "parentIndex": 1, "index": 3,
                                   "year": 2022, "ratingKey": "45121"}
                    }
                    """.formatted(account, seriesName);
            case "jellyfin" -> """
                    {
                      "NotificationType": "PlaybackStop", "NotificationUsername": "%s",
                      "ItemType": "Episode", "ItemId": "jf-45121", "Name": "In Perpetuity",
                      "SeriesName": "%s", "SeasonNumber": "1", "EpisodeNumber": "3",
                      "Year": "2022", "PlayedToCompletion": "True"
                    }
                    """.formatted(account, seriesName);
            case "emby" -> """
                    {
                      "Event": "playback.stop", "User": {"Name": "%s"},
                      "Item": {"Name": "In Perpetuity", "Type": "Episode", "SeriesName": "%s",
                               "ParentIndexNumber": 1, "IndexNumber": 3, "Id": "emby-45121"},
                      "PlaybackInfo": {"PlayedToCompletion": true}
                    }
                    """.formatted(account, seriesName);
            default -> throw new IllegalArgumentException(service);
        };
    }

    private String moviePayload(String service, String account, String title) {
        return switch (service) {
            case "plex" -> """
                    {"event": "media.scrobble", "user": true, "Account": {"title": "%s"},
                     "Metadata": {"type": "movie", "title": "%s", "year": 1977, "ratingKey": "99"}}
                    """.formatted(account, title);
            case "jellyfin" -> """
                    {"NotificationType": "PlaybackStop", "NotificationUsername": "%s",
                     "ItemType": "Movie", "ItemId": "jf-99", "Name": "%s", "Year": "1977",
                     "PlayedToCompletion": "True"}
                    """.formatted(account, title);
            case "emby" -> """
                    {"Event": "playback.stop", "User": {"Name": "%s"},
                     "Item": {"Name": "%s", "Type": "Movie", "ProductionYear": 1977, "Id": "emby-99"},
                     "PlaybackInfo": {"PlayedToCompletion": true}}
                    """.formatted(account, title);
            default -> throw new IllegalArgumentException(service);
        };
    }

    /** Events that are not a claim that anything was watched, per server. */
    private List<String> unfinishedPayloads(String service) {
        return switch (service) {
            case "plex" -> List.of(
                    episodePayload("plex", "denis").replace("media.scrobble", "media.play"),
                    episodePayload("plex", "denis").replace("media.scrobble", "media.pause"),
                    episodePayload("plex", "denis").replace("media.scrobble", "media.stop"));
            case "jellyfin" -> List.of(
                    episodePayload("jellyfin", "denis").replace("PlaybackStop", "PlaybackStart"),
                    episodePayload("jellyfin", "denis").replace("\"True\"", "\"False\""));
            case "emby" -> List.of(
                    episodePayload("emby", "denis").replace("playback.stop", "playback.start"),
                    episodePayload("emby", "denis").replace("\"PlayedToCompletion\": true",
                            "\"PlayedToCompletion\": false"));
            default -> throw new IllegalArgumentException(service);
        };
    }
}
