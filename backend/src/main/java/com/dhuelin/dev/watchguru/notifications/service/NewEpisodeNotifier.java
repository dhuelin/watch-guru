package com.dhuelin.dev.watchguru.notifications.service;

import com.dhuelin.dev.watchguru.api.dto.ImageUrls;
import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.config.NotificationProperties;
import com.dhuelin.dev.watchguru.notifications.domain.DeviceToken;
import com.dhuelin.dev.watchguru.notifications.repository.DeviceTokenRepository;
import com.dhuelin.dev.watchguru.notifications.repository.NotificationDeliveryRepository;
import com.dhuelin.dev.watchguru.notifications.repository.NotificationPreferenceRepository;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tells people about episodes of series they follow.
 *
 * <p>The scan runs often and sends rarely. Everything that decides whether a
 * particular user hears anything -- their global switch, their per-series
 * mutes, the hour where they are, how many pushes they have already had today,
 * and what they have already been told -- is checked here rather than at the
 * point of sending, so a user in the wrong time zone costs one cheap query
 * instead of a push.
 */
@Service
public class NewEpisodeNotifier {

    private static final Logger log = LoggerFactory.getLogger(NewEpisodeNotifier.class);

    /** The statuses that mean "I am following this". */
    private static final List<WatchStatus> FOLLOWED = List.of(WatchStatus.WATCHING, WatchStatus.WATCHLIST);

    private final AppUserRepository users;
    private final WatchlistItemRepository watchlist;
    private final EpisodeRepository episodes;
    private final DeviceTokenRepository devices;
    private final NotificationPreferenceRepository preferences;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationDeliveryRecorder recorder;
    private final ImageUrls images;
    private final PushSender push;
    private final NotificationProperties properties;
    private final Clock clock;

    public NewEpisodeNotifier(AppUserRepository users,
                              WatchlistItemRepository watchlist,
                              EpisodeRepository episodes,
                              DeviceTokenRepository devices,
                              NotificationPreferenceRepository preferences,
                              NotificationDeliveryRepository deliveries,
                              NotificationDeliveryRecorder recorder,
                              ImageUrls images,
                              PushSender push,
                              NotificationProperties properties,
                              Clock clock) {
        this.users = users;
        this.watchlist = watchlist;
        this.episodes = episodes;
        this.devices = devices;
        this.preferences = preferences;
        this.deliveries = deliveries;
        this.recorder = recorder;
        this.images = images;
        this.push = push;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Scans one user and sends what is due.
     *
     * @return how many notifications were sent
     */
    @Transactional
    public int scanUser(Long userId) {
        if (!properties.enabled()) {
            return 0;
        }
        // Locked, so two workers cannot both find the same room under the
        // daily cap and both fill it. The lock is held for the whole scan,
        // sending included: the scan is hourly and per user, so the only
        // thing it ever waits for is another run of itself.
        AppUser user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null || !user.isNotificationsEnabled()) {
            return 0;
        }

        ZonedDateTime local = ZonedDateTime.now(clock).withZoneSameInstant(user.zone());
        if (!quietHours().allows(local.toLocalTime())) {
            return 0;
        }

        int remaining = properties.dailyCap()
                - (int) deliveries.countNotificationsSince(
                        userId, local.toLocalDate().atStartOfDay(user.zone()).toInstant());
        if (remaining <= 0) {
            return 0;
        }

        List<EpisodeAnnouncement> due = announcementsFor(user, local.toLocalDate());
        if (due.isEmpty()) {
            return 0;
        }

        List<DeviceToken> registered = devices.findByUserId(userId);
        if (registered.isEmpty()) {
            return 0;
        }

        int sent = 0;
        for (EpisodeAnnouncement announcement : due) {
            if (sent >= remaining) {
                // Over the cap. Nothing is claimed for what is left, so it is
                // announced tomorrow rather than lost -- as long as it is
                // still inside the lookback window, which is what that window
                // is for.
                break;
            }
            UUID batch;
            try {
                batch = recorder.claim(user, announcement.episodes());
            } catch (DataIntegrityViolationException e) {
                // Another scan claimed these first: already announced.
                log.debug("Already announced {} to user {}", announcement.titleName(), userId);
                continue;
            }

            if (deliver(registered, announcement)) {
                sent++;
            } else {
                // Nothing accepted it. Give the claim back, or this episode is
                // marked announced forever and the user never hears about it.
                recorder.release(batch);
            }
        }
        return sent;
    }

    /**
     * Sends to every device, and says whether any of them took it.
     *
     * <p>A dead token is not a failure to deliver -- that device is gone, and
     * the announcement is not owed to it. Only a transient failure on every
     * remaining device means nobody was told.
     */
    private boolean deliver(List<DeviceToken> registered, EpisodeAnnouncement announcement) {
        PushMessage message = announcement.toMessage();
        boolean delivered = false;

        for (DeviceToken device : registered) {
            PushSender.Result result = push.send(device, message);
            switch (result) {
                case DELIVERED -> delivered = true;
                case TOKEN_INVALID ->
                    // The app is gone from that device. Keeping the row would
                    // mean calling the push service about it forever.
                        recorder.forget(device);
                case TEMPORARY_FAILURE -> {
                    // Nothing to do per device; the caller decides once it
                    // knows whether any other device took it.
                }
            }
        }
        return delivered;
    }

    /** What this user should be told about today, one entry per series. */
    private List<EpisodeAnnouncement> announcementsFor(AppUser user, LocalDate today) {
        List<Long> followed = new ArrayList<>();
        for (WatchStatus status : FOLLOWED) {
            for (WatchlistItem item : watchlist.findByUserIdAndStatus(user.getId(), status)) {
                if (item.getTitle().getTitleType() == TitleType.TV_SERIES) {
                    followed.add(item.getTitle().getId());
                }
            }
        }
        if (followed.isEmpty()) {
            return List.of();
        }

        Set<Long> muted = preferences.findMutedTitleIds(user.getId());
        followed.removeAll(muted);
        if (followed.isEmpty()) {
            return List.of();
        }

        List<Episode> aired = episodes.findAiredBetween(
                followed, today.minusDays(properties.lookbackDays()), today);
        if (aired.isEmpty()) {
            return List.of();
        }

        Set<Long> alreadyTold = deliveries.findNotifiedEpisodeIds(
                user.getId(), aired.stream().map(Episode::getId).toList());

        // LinkedHashMap: the repository returns air-date order, so the series
        // whose episode landed first is the first one announced -- which is
        // what the daily cap should be spent on.
        Map<Long, List<Episode>> byTitle = new LinkedHashMap<>();
        for (Episode episode : aired) {
            if (!alreadyTold.contains(episode.getId())) {
                byTitle.computeIfAbsent(episode.getTitle().getId(), id -> new ArrayList<>()).add(episode);
            }
        }

        List<EpisodeAnnouncement> announcements = new ArrayList<>();
        for (Map.Entry<Long, List<Episode>> entry : byTitle.entrySet()) {
            List<Episode> group = entry.getValue();
            announcements.add(new EpisodeAnnouncement(
                    entry.getKey(),
                    group.getFirst().getTitle().getPrimaryTitle(),
                    group,
                    images.still(group.getFirst().getStillPath())));
        }
        return announcements;
    }

    private QuietHours quietHours() {
        return new QuietHours(properties.quietFrom(), properties.quietUntil());
    }
}
