package com.dhuelin.dev.watchguru.tracking.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * How much of the history a stats screen is asking about.
 *
 * <p>Calendar boundaries, not rolling windows. "This month" means since the
 * first of the month where the user is, which is what somebody means when they
 * pick it -- a rolling thirty days would quietly include half of last month and
 * make the same screen disagree with their own sense of the month.
 */
public enum StatsPeriod {

    MONTH,
    YEAR,
    ALL_TIME;

    /**
     * The first instant this period includes, or null for all time.
     *
     * <p>Null rather than {@code Instant.EPOCH}: the queries read it as "no
     * lower bound at all", which lets the database skip the comparison
     * entirely rather than testing every row against 1970.
     */
    public Instant from(ZoneId zone, LocalDate today) {
        return switch (this) {
            case MONTH -> today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
            case YEAR -> today.withDayOfYear(1).atStartOfDay(zone).toInstant();
            case ALL_TIME -> null;
        };
    }
}
