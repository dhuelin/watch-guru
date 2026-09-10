package com.dhuelin.dev.watchguru.notifications.service;

import com.dhuelin.dev.watchguru.config.NotificationProperties;
import com.dhuelin.dev.watchguru.notifications.repository.DeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the new-episode scan on a schedule.
 *
 * <p>A separate bean from {@link NewEpisodeNotifier} rather than a method on
 * it, and not for tidiness: {@code scanUser} is transactional, and a call from
 * one method of a bean to another bypasses the proxy that makes it so. The
 * loop lives here so that each user's scan actually gets its transaction.
 *
 * <p>Hourly. Air dates in the catalogue are dates, not times, so there is no
 * minute to be punctual to; what the schedule really decides is how soon after
 * a user's quiet hours open they hear about it.
 */
@Component
public class NewEpisodeScanJob {

    private static final Logger log = LoggerFactory.getLogger(NewEpisodeScanJob.class);

    private final NewEpisodeNotifier notifier;
    private final DeviceTokenRepository devices;
    private final NotificationProperties properties;

    public NewEpisodeScanJob(NewEpisodeNotifier notifier,
                             DeviceTokenRepository devices,
                             NotificationProperties properties) {
        this.notifier = notifier;
        this.devices = devices;
        this.properties = properties;
    }

    @Scheduled(cron = "${watch-guru.notifications.cron:0 5 * * * *}")
    public void run() {
        scanAll();
    }

    /** @return how many notifications went out */
    public int scanAll() {
        if (!properties.enabled()) {
            return 0;
        }
        int sent = 0;
        for (Long userId : devices.findUserIdsWithDevices()) {
            try {
                sent += notifier.scanUser(userId);
            } catch (RuntimeException e) {
                // One user's bad data must not cost everybody else their
                // episodes, so the loop carries on and the failure is logged
                // rather than thrown into the scheduler.
                log.warn("New-episode scan failed for user {}: {}", userId, e.toString());
            }
        }
        if (sent > 0) {
            log.info("Sent {} new-episode notifications", sent);
        }
        return sent;
    }
}
