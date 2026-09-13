package com.dhuelin.dev.watchguru.tracking.service;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.tracking.domain.WatchEvent;
import com.dhuelin.dev.watchguru.tracking.repository.EpisodeWatchRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reading and correcting the viewing history (#22).
 *
 * <p>The history is the user's actual record -- "what did I watch last
 * October" -- and it is also where mistakes get corrected. Both halves live
 * here because the second one is not a field update: {@code watch_event} is
 * append-only and authoritative, and {@code episode_watch} and
 * {@code watchlist_item} are derived from it. Moving an event's date changes
 * what those derived rows should say, and doing that arithmetic in a client
 * would put two answers in the world.
 */
@Service
public class WatchHistoryService {

    private final WatchEventRepository events;
    private final EpisodeWatchRepository episodeWatches;
    private final StreamingServiceRepository services;

    public WatchHistoryService(WatchEventRepository events,
                               EpisodeWatchRepository episodeWatches,
                               StreamingServiceRepository services) {
        this.events = events;
        this.episodeWatches = episodeWatches;
        this.services = services;
    }

    /**
     * What the timeline is showing.
     *
     * @param from      inclusive, or null for no lower bound
     * @param to        inclusive as a date, exclusive as an instant -- see
     *                  {@link #endOf}
     * @param type      films only, series only, or null for both
     * @param serviceId one streaming service, or null for all
     * @param query     a substring of the title or the episode name
     */
    public record Filter(Instant from, Instant to, TitleType type, Long serviceId, String query) {

        public static Filter none() {
            return new Filter(null, null, null, null, null);
        }
    }

    /**
     * One page of history, newest first.
     *
     * <p>Ordered by date and then by id, so two viewings recorded for the same
     * instant -- which a bulk "mark watched up to here" produces by the dozen --
     * come back in a stable order rather than shuffling between pages.
     */
    @Transactional(readOnly = true)
    public Page<WatchEvent> list(Long userId, Filter filter, Pageable pageable) {
        return events.search(
                userId,
                filter.from(),
                filter.to(),
                filter.type() == TitleType.TV_SERIES,
                filter.type() == TitleType.MOVIE,
                filter.serviceId(),
                like(filter.query()),
                pageable);
    }

    /**
     * Corrects one entry.
     *
     * <p>Null means "leave it alone" rather than "clear it": a client sending a
     * partial update must not wipe the service because it only meant to change
     * the date. Clearing the service is not offered -- an event whose service
     * is wrong gets the right one.
     *
     * @throws NotFoundException if the event is not this user's. 404 rather
     *         than 403, because a 403 would confirm the event exists and ids
     *         are sequential
     */
    @Transactional
    public WatchEvent update(Long userId, Long eventId, Instant watchedAt, Long serviceId) {
        WatchEvent event = events.findById(eventId)
                .orElseThrow(() -> NotFoundException.of("Watch event", eventId));
        if (!event.getUser().getId().equals(userId)) {
            throw NotFoundException.of("Watch event", eventId);
        }

        if (serviceId != null) {
            StreamingService service = services.findById(serviceId)
                    .orElseThrow(() -> NotFoundException.of("StreamingService", serviceId));
            event.setStreamingService(service);
        }
        if (watchedAt != null) {
            event.setWatchedAt(watchedAt);
        }
        events.save(event);

        if (watchedAt != null) {
            // Only a moved date can make the derived rows wrong; changing which
            // service it was watched on cannot.
            recomputeDerived(userId, event.getTitle(), event.getEpisode());
        }
        return event;
    }

    /**
     * Puts the derived rows back in step with the history after a date moved.
     *
     * <p>Two things follow from the order of events and are therefore wrong the
     * moment one is backdated. {@code episode_watch.watched_at} is the last time
     * an episode was seen, and the rewatch flag says an event was not the first
     * viewing -- backdate the third watch to before the first and, without
     * this, the history claims somebody rewatched something before they ever
     * saw it.
     */
    private void recomputeDerived(Long userId, Title title, Episode episode) {
        List<WatchEvent> ordered = (episode == null
                ? events.findByUserIdAndTitleIdOrderByWatchedAtDesc(userId, title.getId()).stream()
                        .filter(e -> e.getEpisode() == null)
                        .toList()
                : events.findByUserIdAndEpisodeId(userId, episode.getId()))
                .stream()
                .sorted(Comparator.comparing(WatchEvent::getWatchedAt)
                        .thenComparing(WatchEvent::getId))
                .toList();

        for (int i = 0; i < ordered.size(); i++) {
            WatchEvent event = ordered.get(i);
            boolean rewatch = i > 0;
            if (event.isRewatch() != rewatch) {
                event.setRewatch(rewatch);
                events.save(event);
            }
        }

        if (episode != null && !ordered.isEmpty()) {
            Instant latest = ordered.getLast().getWatchedAt();
            episodeWatches.findByUserIdAndEpisodeId(userId, episode.getId()).ifPresent(watch -> {
                if (!latest.equals(watch.getWatchedAt())) {
                    watch.setWatchedAt(latest);
                    episodeWatches.save(watch);
                }
            });
        }
    }

    /**
     * The instant a date's day ends, in the user's own zone.
     *
     * <p>"Up to and including the 14th" is what a person means by a date range,
     * and the instant that expresses it is the start of the 15th where they
     * are. Using the date itself would silently drop everything watched on the
     * last day of the range -- a bug nobody reports because it looks like
     * having watched nothing that evening.
     */
    public static Instant endOf(LocalDate date, ZoneId zone) {
        return date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant();
    }

    /** The instant a date's day begins, in the user's own zone. */
    public static Instant startOf(LocalDate date, ZoneId zone) {
        return date == null ? null : date.atStartOfDay(zone).toInstant();
    }

    /**
     * The search term as a SQL pattern, or null for no search at all.
     *
     * <p>Lower-cased here rather than in the query so the pattern and the
     * column are folded the same way, and wildcards in what the user typed are
     * escaped -- a search for "100%" should find "100% Wolf", not everything.
     */
    private static String like(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String escaped = query.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
