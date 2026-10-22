package com.dhuelin.dev.watchguru.streaming.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes the streaming-service table from the provider on a schedule.
 *
 * <p>This used to be an endpoint any signed-in user could call, with any
 * region, as often as they liked. The table is shared by everyone and every
 * call spends TMDB requests, so it is now the server's job and nobody else's.
 *
 * <p>Weekly, for the provider's default region: the catalogue of services
 * barely moves, and a missed run costs nothing but a logo that is a week old.
 */
@Component
public class StreamingServiceReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(StreamingServiceReconcileJob.class);

    private final AvailabilityService availability;

    public StreamingServiceReconcileJob(AvailabilityService availability) {
        this.availability = availability;
    }

    @Scheduled(cron = "${watch-guru.streaming.reconcile-cron:0 40 3 * * MON}")
    public void run() {
        try {
            availability.reconcileServices(null);
        } catch (RuntimeException e) {
            // Includes a provider that is not configured at all. Nothing
            // depends on this run succeeding, so it is logged, not thrown.
            log.warn("Streaming-service reconcile failed: {}", e.toString());
        }
    }
}
