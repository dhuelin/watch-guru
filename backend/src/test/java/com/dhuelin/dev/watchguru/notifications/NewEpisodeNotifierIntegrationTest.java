package com.dhuelin.dev.watchguru.notifications;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.SeasonRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.notifications.domain.DevicePlatform;
import com.dhuelin.dev.watchguru.notifications.domain.DeviceToken;
import com.dhuelin.dev.watchguru.notifications.repository.DeviceTokenRepository;
import com.dhuelin.dev.watchguru.notifications.repository.NotificationDeliveryRepository;
import com.dhuelin.dev.watchguru.notifications.service.NewEpisodeNotifier;
import com.dhuelin.dev.watchguru.notifications.service.NotificationSettingsService;
import com.dhuelin.dev.watchguru.notifications.service.PushMessage;
import com.dhuelin.dev.watchguru.notifications.service.PushSender;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The new-episode scan, against a real database.
 *
 * <p>The acceptance criteria on #19 are about restraint rather than delivery:
 * exactly one notification per episode, at a civilised local hour, capped per
 * day, silenced when asked, and stopping altogether for a device that no
 * longer exists. Each of those is a test here.
 */
@SpringBootTest
@Testcontainers
@Import({PostgresTestConfig.class,
        NewEpisodeNotifierIntegrationTest.TestClockConfig.class,
        NewEpisodeNotifierIntegrationTest.CapturingSenderConfig.class})
class NewEpisodeNotifierIntegrationTest {

    /** A clock the test moves by hand, so 3am does not require waiting for it. */
    private static final AtomicReference<Instant> NOW =
            new AtomicReference<>(Instant.parse("2026-03-10T12:00:00Z"));

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {
        // Named differently from the application's own clock bean on purpose:
        // same name would be an override rather than an alternative, and the
        // context refuses those.
        @Bean
        @Primary
        Clock movableClock() {
            return new MovableClock();
        }
    }

    /** Reads {@link #NOW} on every call, so a test can advance time mid-run. */
    static class MovableClock extends Clock {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return NOW.get();
        }
    }

    /** Records what would have been pushed, and can reject a token. */
    static class CapturingSender implements PushSender {
        final List<PushMessage> sent = new ArrayList<>();
        final List<String> tokens = new ArrayList<>();
        Result nextResult = Result.DELIVERED;

        @Override
        public Result send(DeviceToken device, PushMessage message) {
            sent.add(message);
            tokens.add(device.getToken());
            return nextResult;
        }

        void reset() {
            sent.clear();
            tokens.clear();
            nextResult = Result.DELIVERED;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CapturingSenderConfig {
        @Bean
        @Primary
        CapturingSender capturingSender() {
            return new CapturingSender();
        }
    }

    @Autowired
    private NewEpisodeNotifier notifier;
    @Autowired
    private NotificationSettingsService settings;
    @Autowired
    private CapturingSender sender;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TitleRepository titles;
    @Autowired
    private SeasonRepository seasons;
    @Autowired
    private EpisodeRepository episodes;
    @Autowired
    private WatchlistItemRepository items;
    @Autowired
    private DeviceTokenRepository devices;
    @Autowired
    private NotificationDeliveryRepository deliveries;

    private AppUser user;
    private Title series;

    /** Midday UTC: inside the quiet-hours window for a user in UTC. */
    private static final Instant MIDDAY = Instant.parse("2026-03-10T12:00:00Z");

    @BeforeEach
    void setUp() {
        NOW.set(MIDDAY);
        sender.reset();

        user = users.save(new AppUser("notify-" + System.nanoTime() + "@example.com", "Viewer"));
        series = titles.save(new Title(7_000_000L + System.nanoTime() % 100_000, TitleType.TV_SERIES, "Test Series"));
        settings.register(user, "device-" + user.getId(), DevicePlatform.IOS);
        items.save(new WatchlistItem(user, series, WatchStatus.WATCHING));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(NOW.get(), ZoneOffset.UTC);
    }

    private Episode episode(Title title, int seasonNumber, int number, LocalDate airDate) {
        Season season = seasons.findByTitleIdAndSeasonNumber(title.getId(), seasonNumber)
                .orElseGet(() -> seasons.save(new Season(title, seasonNumber)));
        Episode episode = new Episode(season, number);
        episode.setName("Episode " + number);
        episode.setAirDate(airDate);
        return episodes.save(episode);
    }

    private Title followedSeries(String name) {
        Title other = titles.save(new Title(7_500_000L + System.nanoTime() % 100_000, TitleType.TV_SERIES, name));
        items.save(new WatchlistItem(user, other, WatchStatus.WATCHING));
        return other;
    }

    @Test
    @DisplayName("a new episode produces exactly one notification, however often the scan runs")
    void exactlyOnce() {
        Episode aired = episode(series, 1, 1, today());

        assertThat(notifier.scanUser(user.getId())).isEqualTo(1);
        assertThat(sender.sent).hasSize(1);
        assertThat(sender.sent.getFirst().title()).isEqualTo("Test Series");
        assertThat(sender.sent.getFirst().body()).isEqualTo("S01E01 · Episode 1");

        assertThat(deliveries.existsByUserIdAndEpisodeId(user.getId(), aired.getId())).isTrue();

        sender.reset();
        assertThat(notifier.scanUser(user.getId())).isZero();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    @DisplayName("several episodes of one series are one notification, not several")
    void groupedPerSeries() {
        episode(series, 1, 1, today().minusDays(1));
        episode(series, 1, 2, today());

        assertThat(notifier.scanUser(user.getId())).isEqualTo(1);
        assertThat(sender.sent).hasSize(1);
        assertThat(sender.sent.getFirst().body()).isEqualTo("2 new episodes, from S01E01");
    }

    @Test
    @DisplayName("an episode that has not aired yet is not announced")
    void unairedIsNotNews() {
        episode(series, 1, 1, today().plusDays(7));

        assertThat(notifier.scanUser(user.getId())).isZero();
    }

    @Test
    @DisplayName("an episode that aired before the lookback window is not dredged up")
    void oldEpisodesAreNotNews() {
        // Adding a series you are ten episodes behind on must not push ten
        // announcements about episodes from last year.
        episode(series, 1, 1, today().minusDays(30));

        assertThat(notifier.scanUser(user.getId())).isZero();
    }

    @Test
    @DisplayName("a special is not the event anybody asked to hear about")
    void season0IsSkipped() {
        episode(series, 0, 1, today());

        assertThat(notifier.scanUser(user.getId())).isZero();
    }

    @Test
    @DisplayName("3am where the user is means nothing is sent, and midday still works")
    void quietHoursFollowTheUsersOwnZone() {
        user.setTimeZone("Australia/Sydney");
        user = users.save(user);
        episode(series, 1, 1, today());

        // Midday UTC is 11pm in Sydney -- outside the window.
        assertThat(notifier.scanUser(user.getId())).isZero();
        assertThat(sender.sent).isEmpty();

        // Twelve hours on it is midnight UTC and 11am the next day in Sydney,
        // which is inside the window. The episode aired yesterday by then and
        // is still announced, which is the point of the lookback.
        NOW.set(MIDDAY.plusSeconds(12 * 3600));
        assertThat(notifier.scanUser(user.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("the global switch stops everything")
    void globalSwitch() {
        episode(series, 1, 1, today());
        settings.setEnabled(user, false);

        assertThat(notifier.scanUser(user.getId())).isZero();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    @DisplayName("a muted series stays muted while the others still arrive")
    void perSeriesMute() {
        Title other = followedSeries("Another Series");
        episode(series, 1, 1, today());
        episode(other, 1, 1, today());

        settings.setSeries(user, series.getId(), false);

        assertThat(notifier.scanUser(user.getId())).isEqualTo(1);
        assertThat(sender.sent.getFirst().title()).isEqualTo("Another Series");
    }

    @Test
    @DisplayName("un-muting a series lets it through again")
    void unmuting() {
        episode(series, 1, 1, today());
        settings.setSeries(user, series.getId(), false);
        assertThat(notifier.scanUser(user.getId())).isZero();

        settings.setSeries(user, series.getId(), true);
        assertThat(notifier.scanUser(user.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("six series returning at once do not become six pushes")
    void dailyCap() {
        episode(series, 1, 1, today());
        for (int i = 0; i < 5; i++) {
            episode(followedSeries("Series " + i), 1, 1, today());
        }

        // The configured cap is three.
        assertThat(notifier.scanUser(user.getId())).isEqualTo(3);
        assertThat(sender.sent).hasSize(3);

        // And the rest are not simply lost: nothing was claimed for them, so
        // they are still due when the cap resets.
        sender.reset();
        assertThat(notifier.scanUser(user.getId())).isZero();

        NOW.set(MIDDAY.plusSeconds(24 * 3600));
        assertThat(notifier.scanUser(user.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("a three-episode announcement costs one of the day's notifications, not three")
    void capCountsNotificationsNotEpisodes() {
        // The delivery log has a row per episode; the person has one buzz.
        // Counting rows would spend three days of allowance on it.
        episode(series, 1, 1, today().minusDays(2));
        episode(series, 1, 2, today().minusDays(1));
        episode(series, 1, 3, today());
        Title second = followedSeries("Second Series");
        episode(second, 1, 1, today());
        Title third = followedSeries("Third Series");
        episode(third, 1, 1, today());

        assertThat(notifier.scanUser(user.getId())).isEqualTo(3);
        assertThat(sender.sent).hasSize(3);
    }

    @Test
    @DisplayName("a push nobody accepted is announced again, not lost")
    void transientFailureReleasesTheClaim() {
        episode(series, 1, 1, today());
        sender.nextResult = PushSender.Result.TEMPORARY_FAILURE;

        assertThat(notifier.scanUser(user.getId())).isZero();

        // Without releasing the claim, the delivery row would say "already
        // announced" forever and this episode would never be mentioned again.
        sender.reset();
        assertThat(notifier.scanUser(user.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("the cap goes to whatever aired first, not to the lowest title id")
    void capFollowsAirDate() {
        // series was created first, so it has the lower id; its episode airs
        // last. Ordering by id would announce it and drop the older one.
        Title second = followedSeries("Second Series");
        Title third = followedSeries("Third Series");
        Title fourth = followedSeries("Fourth Series");
        episode(series, 1, 1, today());
        episode(second, 1, 1, today().minusDays(3));
        episode(third, 1, 1, today().minusDays(2));
        episode(fourth, 1, 1, today().minusDays(1));

        assertThat(notifier.scanUser(user.getId())).isEqualTo(3);
        assertThat(sender.sent.stream().map(PushMessage::title))
                .containsExactly("Second Series", "Third Series", "Fourth Series");
    }

    @Test
    @DisplayName("registering a device is how the server learns what time it is where the user is")
    void deviceRegistrationCarriesTheTimeZone() {
        settings.register(user, "device-zoned-" + user.getId(), DevicePlatform.ANDROID, "Pacific/Auckland");

        assertThat(users.findById(user.getId()).orElseThrow().getTimeZone()).isEqualTo("Pacific/Auckland");
    }

    @Test
    @DisplayName("a time zone the platform does not know is ignored, not fatal")
    void unknownTimeZoneIsIgnored() {
        // The registration is the point of the call; a bad zone leaves the
        // previous one, which is no worse than not having called at all.
        settings.register(user, "device-odd-" + user.getId(), DevicePlatform.ANDROID, "Mars/Olympus_Mons");

        assertThat(users.findById(user.getId()).orElseThrow().getTimeZone()).isEqualTo("UTC");
        assertThat(devices.findByToken("device-odd-" + user.getId())).isPresent();
    }

    @Test
    @DisplayName("a device the push service rejects is forgotten")
    void deadTokensAreCleanedUp() {
        episode(series, 1, 1, today());
        sender.nextResult = PushSender.Result.TOKEN_INVALID;

        notifier.scanUser(user.getId());

        assertThat(devices.findByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("a user with no registered device is not scanned at all")
    void noDeviceNoScan() {
        episode(series, 1, 1, today());
        devices.findByUserId(user.getId()).forEach(devices::delete);

        assertThat(notifier.scanUser(user.getId())).isZero();
    }

    @Test
    @DisplayName("a series in the library but not followed does not notify")
    void onlyFollowedSeries() {
        Title dropped = titles.save(
                new Title(7_900_000L + System.nanoTime() % 100_000, TitleType.TV_SERIES, "Dropped Series"));
        items.save(new WatchlistItem(user, dropped, WatchStatus.DROPPED));
        episode(dropped, 1, 1, today());

        assertThat(notifier.scanUser(user.getId())).isZero();
    }
}
