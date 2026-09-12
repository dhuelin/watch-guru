package com.dhuelin.dev.watchguru.streaming.trakt;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.SeasonRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.security.credentials.StoredCredentialRepository;
import com.dhuelin.dev.watchguru.security.oauth.OAuthStateService;
import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Connecting Trakt and pulling somebody's history, end to end.
 *
 * <p>Trakt itself is stubbed -- the real service needs an application
 * registration and a person to click Approve -- but everything on this side of
 * the boundary is real: the OAuth state, the sealed token, the matcher, the
 * unique index, and the cursor that decides what the next run reads.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
@TestPropertySource(properties = {
        "watch-guru.credentials.secret=c2l4dGVlbmJ5dGVzc2l4dGVlbmJ5dGVzc2l4dGVlbmI=",
        "watch-guru.trakt.client-id=test-client",
        "watch-guru.trakt.client-secret=test-secret",
        "watch-guru.public-base-url=https://api.watch-guru.test"
})
class TraktSyncIntegrationTest {

    @MockitoBean
    private TraktClient trakt;

    @Autowired
    private TraktConnectionService connections;
    @Autowired
    private TraktSyncService sync;
    @Autowired
    private OAuthStateService states;
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
    private StoredCredentialRepository credentials;

    private AppUser user;
    private Title film;
    private Title series;
    private long seed;

    @BeforeEach
    void setUp() {
        seed = System.nanoTime();
        user = users.save(new AppUser("trakt-" + seed + "@example.com", "Trakt watcher"));

        film = new Title(7_000_000L + seed % 100_000, TitleType.MOVIE, "Heat " + seed);
        film.setImdbId("tt" + seed);
        film.setReleaseDate(LocalDate.of(1995, 12, 15));
        film.setRuntimeMinutes(170);
        film = titles.save(film);

        series = new Title(7_100_000L + seed % 100_000, TitleType.TV_SERIES, "Severance " + seed);
        series.setImdbId("tt1" + seed);
        series = titles.save(series);
        Season season = seasons.save(new Season(series, 1));
        Episode episode = new Episode(season, 3);
        episode.setName("In Perpetuity");
        episode.setAirDate(LocalDate.of(2022, 2, 25));
        episodes.save(episode);
    }

    @Test
    @DisplayName("authorising stores a sealed token and nothing readable")
    void connectSealsTheToken() {
        LinkedStreamingAccount account = connect();

        assertThat(account.getStatus()).isEqualTo(LinkStatus.CONNECTED);
        assertThat(account.getCredentialRef()).isNotBlank();

        var row = credentials.findByCredentialRef(account.getCredentialRef()).orElseThrow();
        assertThat(row.getCiphertext()).doesNotContain("access-token-1");
    }

    @Test
    @DisplayName("a callback whose state was never issued connects nothing")
    void refusesAForgedCallback() {
        assertThatThrownBy(() -> connections.complete("not-a-real-state", "code", null))
                .isInstanceOf(TraktConnectionService.NotAvailableException.class);

        assertThat(accounts.findByUserIdAndStreamingServiceSlug(user.getId(), "trakt")).isEmpty();
    }

    @Test
    @DisplayName("a film and an episode both land in the library and the history")
    void recordsWhatTraktReports() {
        LinkedStreamingAccount account = connect();
        givenHistory(movie(11L), episode(12L));

        TraktSyncService.Result result = sync.sync(account).orElseThrow();

        assertThat(result.imported()).isEqualTo(2);
        assertThat(eventsOf(user)).hasSize(2).allSatisfy(event ->
                assertThat(event.getOrigin()).isEqualTo(WatchOrigin.STREAMING_SYNC));
        assertThat(eventsOf(user)).anySatisfy(event ->
                assertThat(event.getOriginRef()).isEqualTo("trakt:11"));
        assertThat(items.findByUserIdAndTitleId(user.getId(), film.getId())).isPresent();
        assertThat(items.findByUserIdAndTitleId(user.getId(), series.getId())).isPresent();
    }

    @Test
    @DisplayName("syncing the same window twice records nothing twice")
    void isIdempotent() {
        LinkedStreamingAccount account = connect();
        givenHistory(movie(11L), episode(12L));
        sync.sync(account).orElseThrow();

        TraktSyncService.Result second = sync.sync(reload(account)).orElseThrow();

        assertThat(second.imported()).isZero();
        assertThat(second.skipped()).isEqualTo(2);
        assertThat(eventsOf(user)).hasSize(2);
    }

    @Test
    @DisplayName("the cursor moves, so the next run asks for less")
    void advancesTheCursor() {
        LinkedStreamingAccount account = connect();
        givenHistory(movie(11L));

        sync.sync(account);

        LinkedStreamingAccount after = reload(account);
        assertThat(after.getLastSyncCursor()).isNotBlank();
        assertThat(after.getLastSyncAt()).isNotNull();
        assertThat(Instant.parse(after.getLastSyncCursor()))
                .isBefore(Instant.parse("2026-09-11T20:00:00Z"));
    }

    @Test
    @DisplayName("a title the catalogue does not hold is counted and named, not dropped")
    void reportsWhatItCouldNotMatch() {
        LinkedStreamingAccount account = connect();
        givenHistory(new TraktResponses.HistoryItem(
                21L, Instant.parse("2026-09-11T19:00:00Z"), "scrobble", "movie",
                new TraktResponses.Movie("A Film Nobody Has Heard Of " + seed, 1977,
                        new TraktResponses.Ids(1L, null, null, null)),
                null, null));

        TraktSyncService.Result result = sync.sync(account).orElseThrow();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.problems()).hasSize(1);
        assertThat(reload(account).getLastSyncError()).contains("could not be matched");
        // Still connected: the connection worked, the catalogue did not have it.
        assertThat(reload(account).getStatus()).isEqualTo(LinkStatus.CONNECTED);
    }

    @Test
    @DisplayName("an expired authorisation is reported as needing a reconnect")
    void reportsExpiredAccess() {
        LinkedStreamingAccount account = connect();
        when(trakt.history(any(), any(), anyInt()))
                .thenThrow(new TraktException("Trakt refused reading history with 401", true));

        assertThat(sync.sync(account)).isEmpty();

        LinkedStreamingAccount after = reload(account);
        assertThat(after.getStatus()).isEqualTo(LinkStatus.ERROR);
        assertThat(after.getLastSyncError()).contains("Connect Trakt again");
    }

    @Test
    @DisplayName("disconnecting revokes at Trakt first, then forgets the token")
    void disconnectRevokes() {
        LinkedStreamingAccount account = connect();
        String ref = account.getCredentialRef();

        connections.disconnect(user);

        verify(trakt).revoke("access-token-1");
        assertThat(credentials.findByCredentialRef(ref)).isEmpty();
        LinkedStreamingAccount after = reload(account);
        assertThat(after.getCredentialRef()).isNull();
        assertThat(after.getStatus()).isEqualTo(LinkStatus.DISCONNECTED);
        assertThat(after.isSyncEnabled()).isFalse();
    }

    @Test
    @DisplayName("a disconnected link is not synced, and Trakt is not called at all")
    void doesNotSyncADisconnectedLink() {
        LinkedStreamingAccount account = connect();
        connections.disconnect(user);

        assertThat(sync.sync(reload(account))).isEmpty();

        verify(trakt, never()).history(any(), any(), anyInt());
    }

    /** Authorises through the real state and credential paths, with Trakt stubbed. */
    private LinkedStreamingAccount connect() {
        when(trakt.exchange(eq("the-code"), any()))
                .thenReturn(new TraktResponses.Token("access-token-1", "refresh-token-1",
                        Duration.ofDays(90).toSeconds(), 0L, "public", "Bearer"));

        String state = states.issue(user.getId(), "trakt", Duration.ofMinutes(10));
        return connections.complete(state, "the-code", null);
    }

    private void givenHistory(TraktResponses.HistoryItem... items) {
        when(trakt.history(any(), any(), eq(1)))
                .thenReturn(new TraktResponses.HistoryPage(List.of(items), 1, 1));
    }

    private TraktResponses.HistoryItem movie(long id) {
        return new TraktResponses.HistoryItem(
                id, Instant.parse("2026-09-11T19:00:00Z"), "scrobble", "movie",
                new TraktResponses.Movie(film.getPrimaryTitle(), 1995,
                        new TraktResponses.Ids(1L, null, film.getImdbId(), null)),
                null, null);
    }

    private TraktResponses.HistoryItem episode(long id) {
        return new TraktResponses.HistoryItem(
                id, Instant.parse("2026-09-11T18:00:00Z"), "scrobble", "episode", null,
                new TraktResponses.Episode(1, 3, "In Perpetuity", null),
                new TraktResponses.Show(series.getPrimaryTitle(), null,
                        new TraktResponses.Ids(2L, null, series.getImdbId(), null)));
    }

    private LinkedStreamingAccount reload(LinkedStreamingAccount account) {
        return accounts.findWithUserAndServiceById(account.getId()).orElseThrow();
    }

    private List<WatchEvent> eventsOf(AppUser who) {
        return watchEvents.findByUserIdOrderByWatchedAtDesc(who.getId(), PageRequest.of(0, 50))
                .getContent();
    }
}
