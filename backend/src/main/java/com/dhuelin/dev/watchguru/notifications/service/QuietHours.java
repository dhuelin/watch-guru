package com.dhuelin.dev.watchguru.notifications.service;

import java.time.LocalTime;

/**
 * The hours of the day a push may be sent, in the user's own time zone.
 *
 * <p>Separate from the scan, and pure, because the interesting case is easy to
 * get wrong: a window that runs from evening to morning wraps past midnight,
 * and the naive {@code from <= t && t < until} answers "never" for it.
 *
 * @param from  first local time a push may be sent, inclusive
 * @param until first local time it may not, exclusive
 */
public record QuietHours(LocalTime from, LocalTime until) {

    /** Whether a push is allowed at this local time. */
    public boolean allows(LocalTime localTime) {
        if (from.equals(until)) {
            // A zero-width window would silence the feature completely, which
            // is never what somebody configuring one means. Read as "all day".
            return true;
        }
        if (from.isBefore(until)) {
            return !localTime.isBefore(from) && localTime.isBefore(until);
        }
        // Wraps past midnight: 21:00-09:00 means evening OR early morning.
        return !localTime.isBefore(from) || localTime.isBefore(until);
    }
}
