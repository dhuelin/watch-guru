package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalTime;

/**
 * New-episode notifications (#19).
 *
 * @param enabled       master switch for the scan; off leaves every other part
 *                      of the feature in place and simply sends nothing
 * @param lookbackDays  how far back an episode may have aired and still be
 *                      announced. Not one day: a scan that did not run, or a
 *                      user whose quiet hours covered the whole window, would
 *                      otherwise silently lose the episode
 * @param quietFrom     earliest local time a push may be sent
 * @param quietUntil    latest local time a push may be sent
 * @param dailyCap      most notifications one user may receive in a local day.
 *                      Six series returning at once is a real Tuesday, and six
 *                      pushes in a minute is how an app gets muted for good
 * @param pushProvider  which sender is wired in. Only {@code log} exists --
 *                      it delivers nothing and says so in the log -- because
 *                      APNs and FCM credentials do not exist yet. Named as a
 *                      property rather than left implicit so that switching it
 *                      on later is a config change, not a code change
 */
@ConfigurationProperties(prefix = "watch-guru.notifications")
public record NotificationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3") int lookbackDays,
        @DefaultValue("09:00") LocalTime quietFrom,
        @DefaultValue("21:00") LocalTime quietUntil,
        @DefaultValue("3") int dailyCap,
        @DefaultValue("log") String pushProvider
) {
}
