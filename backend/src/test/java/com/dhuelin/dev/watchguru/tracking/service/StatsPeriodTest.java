package com.dhuelin.dev.watchguru.tracking.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Where each period starts, and whose clock decides. */
class StatsPeriodTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    @Test
    @DisplayName("this month starts on the first, not thirty days ago")
    void monthIsACalendarMonth() {
        // A rolling window would quietly include half of last month, and the
        // screen would disagree with the user's own sense of "this month".
        Instant from = StatsPeriod.MONTH.from(ZURICH, LocalDate.of(2026, 3, 20));

        assertThat(from).isEqualTo(LocalDate.of(2026, 3, 1).atStartOfDay(ZURICH).toInstant());
    }

    @Test
    @DisplayName("this year starts on the first of January, where the user is")
    void yearIsACalendarYear() {
        Instant from = StatsPeriod.YEAR.from(ZURICH, LocalDate.of(2026, 3, 20));

        assertThat(from).isEqualTo(LocalDate.of(2026, 1, 1).atStartOfDay(ZURICH).toInstant());
    }

    @Test
    @DisplayName("the boundary is local, so a user in Auckland is not on Greenwich's calendar")
    void boundariesFollowTheUsersZone() {
        Instant zurich = StatsPeriod.MONTH.from(ZURICH, LocalDate.of(2026, 3, 20));
        Instant auckland = StatsPeriod.MONTH.from(ZoneId.of("Pacific/Auckland"), LocalDate.of(2026, 3, 20));

        // Auckland reaches the first of the month thirteen hours earlier.
        assertThat(auckland).isBefore(zurich);
    }

    @Test
    @DisplayName("all time has no floor at all, rather than a very old one")
    void allTimeIsUnbounded() {
        // Null lets the query drop the comparison entirely instead of testing
        // every row against 1970.
        assertThat(StatsPeriod.ALL_TIME.from(ZURICH, LocalDate.of(2026, 3, 20))).isNull();
    }
}
