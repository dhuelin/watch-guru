package com.dhuelin.dev.watchguru.notifications;

import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.notifications.domain.DevicePlatform;
import com.dhuelin.dev.watchguru.notifications.domain.DeviceToken;
import com.dhuelin.dev.watchguru.notifications.repository.DeviceTokenRepository;
import com.dhuelin.dev.watchguru.notifications.service.NotificationSettingsService;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Device registration, and who is allowed to undo it. */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class NotificationSettingsServiceTest {

    @Autowired
    private NotificationSettingsService settings;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TitleRepository titles;
    @Autowired
    private DeviceTokenRepository devices;

    private AppUser alice;
    private AppUser bob;
    private String token;

    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        alice = users.save(new AppUser("alice-" + seed + "@example.com", "Alice"));
        bob = users.save(new AppUser("bob-" + seed + "@example.com", "Bob"));
        token = "device-token-" + seed;
    }

    @Test
    @DisplayName("registering twice refreshes the row rather than duplicating it")
    void registrationIsIdempotent() {
        // The app calls this on every launch, because push services reissue
        // tokens without telling it which launch was the one that changed.
        settings.register(alice, token, DevicePlatform.IOS);
        settings.register(alice, token, DevicePlatform.IOS);

        assertThat(devices.findByUserId(alice.getId())).hasSize(1);
    }

    @Test
    @DisplayName("a device that signs in as somebody else moves to them")
    void tokenFollowsTheCurrentUser() {
        // A phone handed on, or a shared tablet. Leaving the old row would
        // push one person's viewing to another person's lock screen.
        settings.register(alice, token, DevicePlatform.ANDROID);
        settings.register(bob, token, DevicePlatform.ANDROID);

        assertThat(devices.findByUserId(alice.getId())).isEmpty();
        assertThat(devices.findByUserId(bob.getId())).hasSize(1);
    }

    @Test
    @DisplayName("one user cannot unregister another user's device")
    void unregisterIsScopedToTheOwner() {
        // A device token is a long string, not a secret, and somebody who
        // learned one must not be able to silence that phone.
        settings.register(alice, token, DevicePlatform.IOS);

        settings.unregister(bob, token);

        assertThat(devices.findByToken(token)).isPresent();

        settings.unregister(alice, token);
        assertThat(devices.findByToken(token)).isEmpty();
    }

    @Test
    @DisplayName("the settings response says what is on and how many devices are registered")
    void settingsResponse() {
        settings.register(alice, token, DevicePlatform.IOS);
        settings.register(alice, token + "-tablet", DevicePlatform.IOS);

        Responses.NotificationSettingsResponse response = settings.settings(alice);

        assertThat(response.enabled()).isTrue();
        assertThat(response.registeredDevices()).isEqualTo(2);
        assertThat(response.series()).isEmpty();
    }

    @Test
    @DisplayName("muting a series is stored; un-muting removes the row rather than storing a yes")
    void mutingAndUnmuting() {
        Title series = titles.save(
                new Title(6_000_000L + System.nanoTime() % 100_000, TitleType.TV_SERIES, "Muted Series"));

        Responses.NotificationSettingsResponse muted = settings.setSeries(alice, series.getId(), false);
        assertThat(muted.series()).singleElement()
                .satisfies(s -> {
                    assertThat(s.titleId()).isEqualTo(series.getId());
                    assertThat(s.newEpisodes()).isFalse();
                });

        // The default is to notify, so an explicit yes and no row at all are
        // the same state; storing both would be two ways to say one thing.
        Responses.NotificationSettingsResponse unmuted = settings.setSeries(alice, series.getId(), true);
        assertThat(unmuted.series()).isEmpty();
    }

    @Test
    @DisplayName("a series that does not exist is a 404, not a stored preference")
    void unknownTitle() {
        assertThatThrownBy(() -> settings.setSeries(alice, 999_999_999L, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("the global switch survives a round trip")
    void globalSwitch() {
        assertThat(settings.setEnabled(alice, false).enabled()).isFalse();
        assertThat(settings.settings(users.findById(alice.getId()).orElseThrow()).enabled()).isFalse();

        assertThat(settings.setEnabled(users.findById(alice.getId()).orElseThrow(), true).enabled()).isTrue();
    }
}
