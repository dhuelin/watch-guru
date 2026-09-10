package com.dhuelin.dev.watchguru.notifications.service;

import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.notifications.domain.DevicePlatform;
import com.dhuelin.dev.watchguru.notifications.domain.DeviceToken;
import com.dhuelin.dev.watchguru.notifications.domain.NotificationPreference;
import com.dhuelin.dev.watchguru.notifications.repository.DeviceTokenRepository;
import com.dhuelin.dev.watchguru.notifications.repository.NotificationPreferenceRepository;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Device registration and the notification settings screen. */
@Service
public class NotificationSettingsService {

    private final DeviceTokenRepository devices;
    private final NotificationPreferenceRepository preferences;
    private final TitleRepository titles;
    private final AppUserRepository users;

    public NotificationSettingsService(DeviceTokenRepository devices,
                                       NotificationPreferenceRepository preferences,
                                       TitleRepository titles,
                                       AppUserRepository users) {
        this.devices = devices;
        this.preferences = preferences;
        this.titles = titles;
        this.users = users;
    }

    /**
     * Records where to push to, or refreshes a token already known.
     *
     * <p>A token already registered to somebody else moves to this user rather
     * than being rejected or duplicated: that is a phone handed on, or a shared
     * tablet, and the person who signed in last is the one whose episodes
     * should appear on the lock screen. Leaving the old row would push one
     * user's viewing habits to another's device.
     */
    @Transactional
    public void register(AppUser user, String token, DevicePlatform platform) {
        DeviceToken existing = devices.findByToken(token).orElse(null);
        if (existing == null) {
            devices.save(new DeviceToken(user, token, platform));
            return;
        }
        existing.setUser(user);
        existing.setPlatform(platform);
        existing.setLastSeenAt(Instant.now());
        devices.save(existing);
    }

    /**
     * Forgets one of this user's devices, on sign-out or when they turn
     * notifications off on that device.
     *
     * <p>Scoped to the caller. A token is a bearer-ish string that another
     * user could plausibly learn, and deleting by token alone would let them
     * silence somebody else's phone.
     */
    @Transactional
    public void unregister(AppUser user, String token) {
        devices.deleteByUserIdAndToken(user.getId(), token);
    }

    @Transactional(readOnly = true)
    public Responses.NotificationSettingsResponse settings(AppUser user) {
        List<Responses.SeriesNotificationResponse> series = preferences.findByUserId(user.getId()).stream()
                .map(p -> new Responses.SeriesNotificationResponse(
                        p.getTitle().getId(), p.getTitle().getPrimaryTitle(), p.isNewEpisodes()))
                .toList();
        return new Responses.NotificationSettingsResponse(
                user.isNotificationsEnabled(), series, devices.findByUserId(user.getId()).size());
    }

    @Transactional
    public Responses.NotificationSettingsResponse setEnabled(AppUser user, boolean enabled) {
        user.setNotificationsEnabled(enabled);
        return settings(users.save(user));
    }

    /**
     * Sets, or clears, what this user has said about one series.
     *
     * <p>Turning a series back on deletes the row rather than storing true:
     * the default is to notify, so an explicit "yes" and no row at all mean
     * the same thing, and keeping both would leave two ways to express one
     * state.
     */
    @Transactional
    public Responses.NotificationSettingsResponse setSeries(AppUser user, Long titleId, boolean newEpisodes) {
        Title title = titles.findById(titleId)
                .orElseThrow(() -> NotFoundException.of("Title", titleId));

        NotificationPreference existing = preferences.findByUserIdAndTitleId(user.getId(), titleId).orElse(null);
        if (newEpisodes) {
            if (existing != null) {
                preferences.delete(existing);
            }
        } else if (existing == null) {
            preferences.save(new NotificationPreference(user, title, false));
        } else {
            existing.setNewEpisodes(false);
            preferences.save(existing);
        }
        return settings(user);
    }
}
