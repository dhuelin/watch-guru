package com.dhuelin.dev.watchguru.notifications.service;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.notifications.domain.DeviceToken;
import com.dhuelin.dev.watchguru.notifications.repository.DeviceTokenRepository;
import com.dhuelin.dev.watchguru.notifications.repository.NotificationDeliveryRepository;
import com.dhuelin.dev.watchguru.notifications.domain.NotificationDelivery;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The two writes the scan makes, each in a transaction of its own.
 *
 * <p>Separate from {@link NewEpisodeNotifier} for a mechanical reason: the
 * scan reads in one long read-only transaction, and these have to commit
 * independently of it. A claim that commits is what stops the next scan
 * repeating an announcement, and it must survive a later failure in the same
 * scan; a device the push service has rejected must be forgotten even though
 * the surrounding transaction may write nothing at all.
 *
 * <p>{@code REQUIRES_NEW} also puts the unique-constraint violation in
 * {@link #claim} on its own transaction boundary, so a race that loses rolls
 * back only its own insert instead of poisoning the caller's.
 */
@Service
public class NotificationDeliveryRecorder {

    private final NotificationDeliveryRepository deliveries;
    private final DeviceTokenRepository devices;
    private final Clock clock;

    public NotificationDeliveryRecorder(NotificationDeliveryRepository deliveries,
                                        DeviceTokenRepository devices,
                                        Clock clock) {
        this.deliveries = deliveries;
        this.devices = devices;
        this.clock = clock;
    }

    /**
     * Claims these episodes for this user, before anything is sent.
     *
     * <p>Written first, not after. A push that goes out without its row would
     * be sent again on the next scan; a row written for a push that then fails
     * costs one missed announcement. Of the two, users punish the duplicate.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException if
     *         another scan claimed any of these first -- meaning the
     *         announcement has already gone out and must not be repeated
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(AppUser user, List<Episode> episodes) {
        Instant now = Instant.now(clock);
        for (Episode episode : episodes) {
            NotificationDelivery delivery = new NotificationDelivery(user, episode);
            // From the clock rather than from wall-clock time, because this
            // timestamp is what the daily cap counts: a row stamped by
            // Instant.now() while everything else reasons about the injected
            // clock makes the cap untestable, and the test that caught it
            // could not otherwise have told a fresh day from the same one.
            delivery.setCreatedAt(now);
            deliveries.save(delivery);
        }
        // Forces the constraint to speak now, inside this transaction, rather
        // than at some later commit the caller cannot attribute.
        deliveries.flush();
    }

    /** Drops a device the push service says no longer exists. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forget(DeviceToken device) {
        devices.deleteById(device.getId());
    }
}
