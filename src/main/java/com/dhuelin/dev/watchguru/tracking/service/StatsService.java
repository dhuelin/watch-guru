package com.dhuelin.dev.watchguru.tracking.service;

import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reporting over the viewing history.
 *
 * <p>All figures come from {@code watch_event}, which is append-only, so
 * changing a title's current status never rewrites the past.
 */
@Service
public class StatsService {

    private static final int TOP_TITLE_LIMIT = 10;
    private static final Duration THIRTY_DAYS = Duration.ofDays(30);
    private static final Duration ONE_YEAR = Duration.ofDays(365);

    private final WatchEventRepository events;
    private final WatchlistItemRepository items;
    private final AppUserRepository users;

    public StatsService(WatchEventRepository events, WatchlistItemRepository items, AppUserRepository users) {
        this.events = events;
        this.items = items;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public WatchStats forUser(Long userId, int monthsOfHistory) {
        AppUser user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        Instant now = Instant.now();

        Map<WatchStatus, Long> byStatus = new EnumMap<>(WatchStatus.class);
        items.countByStatus(userId).forEach(row -> byStatus.put(row.getStatus(), row.getCount()));

        Instant monthlyFrom = now.minus(Duration.ofDays(31L * Math.max(monthsOfHistory, 1)));
        List<LocalDate> watchDays = events.distinctWatchDays(userId, user.getTimeZone());

        return new WatchStats(
                events.totalMinutesWatched(userId),
                events.countMovieViewings(userId),
                events.countEpisodeViewings(userId),
                events.countDistinctTitles(userId),
                events.minutesWatchedSince(userId, now.minus(THIRTY_DAYS)),
                events.minutesWatchedSince(userId, now.minus(ONE_YEAR)),
                currentStreak(watchDays, LocalDate.now(user.zone())),
                longestStreak(watchDays),
                events.firstWatchedAt(userId),
                events.lastWatchedAt(userId),
                byStatus,
                toBuckets(events.totalsByGenre(userId)),
                toBuckets(events.totalsByService(userId)),
                toTitleBuckets(events.topTitles(userId, PageRequest.of(0, TOP_TITLE_LIMIT))),
                toMonthBuckets(events.monthlyTotals(userId, monthlyFrom)));
    }

    /**
     * Days watched in an unbroken run ending today or yesterday.
     *
     * <p>Yesterday still counts so the streak does not appear broken simply
     * because the user has not watched anything yet today.
     */
    static int currentStreak(List<LocalDate> descendingDays, LocalDate today) {
        if (descendingDays.isEmpty()) {
            return 0;
        }
        LocalDate mostRecent = descendingDays.getFirst();
        if (mostRecent.isBefore(today.minusDays(1))) {
            return 0;
        }

        int streak = 1;
        LocalDate expected = mostRecent.minusDays(1);
        for (LocalDate day : descendingDays.subList(1, descendingDays.size())) {
            if (day.equals(expected)) {
                streak++;
                expected = day.minusDays(1);
            } else if (day.isBefore(expected)) {
                break;
            }
        }
        return streak;
    }

    /** Longest unbroken run of watch days anywhere in the history. */
    static int longestStreak(List<LocalDate> descendingDays) {
        if (descendingDays.isEmpty()) {
            return 0;
        }
        int longest = 1;
        int current = 1;
        for (int i = 1; i < descendingDays.size(); i++) {
            LocalDate previous = descendingDays.get(i - 1);
            LocalDate day = descendingDays.get(i);
            if (day.equals(previous.minusDays(1))) {
                current++;
                longest = Math.max(longest, current);
            } else if (!day.equals(previous)) {
                current = 1;
            }
        }
        return longest;
    }

    private static List<WatchStats.Bucket> toBuckets(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new WatchStats.Bucket(
                        (String) row[0], asLong(row[1]), asLong(row[2])))
                .toList();
    }

    private static List<WatchStats.Bucket> toTitleBuckets(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new WatchStats.Bucket(
                        (String) row[1], asLong(row[2]), asLong(row[3])))
                .toList();
    }

    private static List<WatchStats.MonthBucket> toMonthBuckets(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new WatchStats.MonthBucket(
                        (int) asLong(row[0]), (int) asLong(row[1]), asLong(row[2]), asLong(row[3])))
                .toList();
    }

    /** Aggregate results arrive as Integer, Long or BigDecimal depending on the function. */
    private static long asLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
