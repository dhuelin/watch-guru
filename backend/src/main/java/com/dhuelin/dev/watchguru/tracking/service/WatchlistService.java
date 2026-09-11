package com.dhuelin.dev.watchguru.tracking.service;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.catalog.service.CatalogService;
import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.EpisodeWatch;
import com.dhuelin.dev.watchguru.tracking.domain.WatchEvent;
import com.dhuelin.dev.watchguru.tracking.domain.WatchOrigin;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.EpisodeWatchRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Watchlist membership, status changes, and recording what was watched. */
@Service
public class WatchlistService {

    private final WatchlistItemRepository items;
    private final AppUserRepository users;
    private final TitleRepository titles;
    private final EpisodeRepository episodes;
    private final EpisodeWatchRepository episodeWatches;
    private final WatchEventRepository watchEvents;
    private final StreamingServiceRepository services;
    private final CatalogService catalog;

    public WatchlistService(WatchlistItemRepository items,
                            AppUserRepository users,
                            TitleRepository titles,
                            EpisodeRepository episodes,
                            EpisodeWatchRepository episodeWatches,
                            WatchEventRepository watchEvents,
                            StreamingServiceRepository services,
                            CatalogService catalog) {
        this.items = items;
        this.users = users;
        this.titles = titles;
        this.episodes = episodes;
        this.episodeWatches = episodeWatches;
        this.watchEvents = watchEvents;
        this.services = services;
        this.catalog = catalog;
    }

    public Page<WatchlistItem> list(Long userId, WatchStatus status, Pageable pageable) {
        return status == null
                ? items.findByUserId(userId, pageable)
                : items.findByUserIdAndStatus(userId, status, pageable);
    }

    /**
     * Adds a title identified by its provider id, importing the catalog entry
     * first. Re-adding an existing item is a no-op rather than an error, so the
     * endpoint is safe to retry.
     */
    @Transactional
    public WatchlistItem add(Long userId, TitleType titleType, long providerId, WatchStatus status) {
        AppUser user = requireUser(userId);
        Title title = catalog.importTitle(titleType, providerId, user.getLanguage());

        return items.findByUserIdAndTitleId(userId, title.getId())
                .orElseGet(() -> items.save(new WatchlistItem(user, title, status)));
    }

    /** Loads one item, scoped to the owning user. */
    @Transactional(readOnly = true)
    public WatchlistItem get(Long userId, Long itemId) {
        return requireItem(userId, itemId);
    }

    @Transactional
    public WatchlistItem updateStatus(Long userId, Long itemId, WatchStatus status) {
        WatchlistItem item = requireItem(userId, itemId);
        item.transitionTo(status);
        return items.save(item);
    }

    @Transactional
    public WatchlistItem rate(Long userId, Long itemId, BigDecimal rating) {
        WatchlistItem item = requireItem(userId, itemId);
        item.setUserRating(rating);
        return items.save(item);
    }

    @Transactional
    public WatchlistItem setNotes(Long userId, Long itemId, String notes) {
        WatchlistItem item = requireItem(userId, itemId);
        item.setNotes(notes);
        return items.save(item);
    }

    @Transactional
    public void remove(Long userId, Long itemId) {
        items.delete(requireItem(userId, itemId));
    }

    /**
     * Records a movie viewing. A second call for the same movie is recorded as
     * a rewatch rather than being rejected.
     */
    @Transactional
    public WatchEvent logMovieWatched(Long userId, Long titleId, Instant watchedAt, Long serviceId) {
        return logMovieWatched(userId, titleId, watchedAt, serviceId, WatchOrigin.MANUAL, null);
    }

    /**
     * As above, but recording where the record came from.
     *
     * <p>The pair matters for imports and only for imports: {@code origin_ref}
     * is covered by a unique index, so re-importing the same export writes
     * nothing the second time however carefully or carelessly the caller
     * checked first.
     */
    @Transactional
    public WatchEvent logMovieWatched(Long userId, Long titleId, Instant watchedAt, Long serviceId,
                                      WatchOrigin origin, String originRef) {
        AppUser user = requireUser(userId);
        Title title = titles.findById(titleId)
                .orElseThrow(() -> NotFoundException.of("Title", titleId));

        boolean seenBefore = watchEvents.existsByUserIdAndTitleIdAndEpisodeIsNull(userId, titleId);

        WatchEvent event = new WatchEvent(user, title, watchedAt == null ? Instant.now() : watchedAt);
        event.setMinutesWatched(title.getRuntimeMinutes());
        event.setRewatch(seenBefore);
        event.setStreamingService(resolveService(serviceId));
        event.setOrigin(origin);
        event.setOriginRef(originRef);
        watchEvents.save(event);

        items.findByUserIdAndTitleId(userId, titleId).ifPresent(item -> {
            item.transitionTo(WatchStatus.COMPLETED);
            items.save(item);
        });

        return event;
    }

    /**
     * Records an episode viewing: updates current progress, appends a history
     * event, and advances the watchlist status.
     */
    @Transactional
    public WatchEvent logEpisodeWatched(Long userId, Long episodeId, Instant watchedAt, Long serviceId) {
        return logEpisodeWatched(userId, episodeId, watchedAt, serviceId, WatchOrigin.MANUAL, null);
    }

    /** As above, recording where the record came from; see the movie variant. */
    @Transactional
    public WatchEvent logEpisodeWatched(Long userId, Long episodeId, Instant watchedAt, Long serviceId,
                                        WatchOrigin origin, String originRef) {
        AppUser user = requireUser(userId);
        Episode episode = episodes.findById(episodeId)
                .orElseThrow(() -> NotFoundException.of("Episode", episodeId));
        Instant when = watchedAt == null ? Instant.now() : watchedAt;

        EpisodeWatch existing = episodeWatches.findByUserIdAndEpisodeId(userId, episodeId).orElse(null);
        boolean rewatch = existing != null;
        if (existing == null) {
            episodeWatches.save(new EpisodeWatch(user, episode, when));
        } else {
            existing.markWatchedAgain(when);
            episodeWatches.save(existing);
        }

        WatchEvent event = WatchEvent.forEpisode(user, episode, when);
        event.setRewatch(rewatch);
        event.setStreamingService(resolveService(serviceId));
        event.setOrigin(origin);
        event.setOriginRef(originRef);
        watchEvents.save(event);

        advanceSeriesStatus(userId, episode.getTitle());
        return event;
    }

    /**
     * Moves a series to WATCHING, or to COMPLETED once every aired episode has
     * been seen. Keeps the list status truthful without the user maintaining it.
     */
    private void advanceSeriesStatus(Long userId, Title title) {
        items.findByUserIdAndTitleId(userId, title.getId()).ifPresent(item -> {
            long aired = episodes.countAiredByTitleId(title.getId());
            long watched = episodeWatches.countByUserIdAndTitleId(userId, title.getId());
            item.transitionTo(aired > 0 && watched >= aired ? WatchStatus.COMPLETED : WatchStatus.WATCHING);
            items.save(item);
        });
    }

    /**
     * Marks every aired episode up to and including the given one as watched.
     *
     * <p>The single most-used action in a tracker: someone who finished a
     * series years ago should be able to record all of it in one gesture, and
     * someone who missed logging three episodes should not have to tap three
     * times.
     *
     * <p>Idempotent by design. Episodes already watched are left completely
     * alone rather than bumped -- marking up to S03E04 twice must not turn the
     * first three seasons into rewatches, which is exactly what calling
     * {@link #logEpisodeWatched} in a loop would do. The count of what was
     * skipped is returned so a client can say something honest.
     *
     * <p>Season 0 specials are excluded: "everything up to here" means the main
     * run, and sweeping specials in would mark episodes the user may never have
     * watched.
     */
    @Transactional
    public BulkMarkResult markWatchedUpTo(Long userId, Long episodeId, Instant watchedAt) {
        AppUser user = requireUser(userId);
        Episode target = episodes.findById(episodeId)
                .orElseThrow(() -> NotFoundException.of("Episode", episodeId));
        Title title = target.getTitle();
        Instant when = watchedAt == null ? Instant.now() : watchedAt;

        List<Episode> upTo = episodes.findAiredUpTo(
                title.getId(), target.getSeasonNumber(), target.getEpisodeNumber());

        // One query for what is already watched, rather than a lookup per
        // episode: this runs over a whole series.
        Set<Long> alreadyWatched = episodeWatches.findWatchedEpisodeIdSet(userId, title.getId());

        int newlyMarked = 0;
        int skipped = 0;
        for (Episode episode : upTo) {
            if (alreadyWatched.contains(episode.getId())) {
                skipped++;
                continue;
            }
            episodeWatches.save(new EpisodeWatch(user, episode, when));

            // Still one watch event per episode: the history is the record of
            // what happened, and collapsing a catch-up into a single event
            // would lose which episodes it covered.
            WatchEvent event = WatchEvent.forEpisode(user, episode, when);
            watchEvents.save(event);
            newlyMarked++;
        }

        advanceSeriesStatus(userId, title);

        long aired = episodes.countAiredByTitleId(title.getId());
        long watched = episodeWatches.countByUserIdAndTitleId(userId, title.getId());
        return new BulkMarkResult(title.getId(), newlyMarked, skipped, (int) watched, (int) aired);
    }

    /** What a bulk mark changed. */
    public record BulkMarkResult(
            Long titleId, int newlyMarked, int alreadyWatched, int watchedEpisodes, int airedEpisodes) {
    }

    /**
     * Removes an episode from the user's watched history entirely.
     *
     * <p>People mark the wrong episode constantly -- one row down in a list of
     * near-identical titles -- so this is a routine correction rather than an
     * edge case.
     *
     * <p>Deletes the history events as well as the current-state row. The
     * append-only {@code watch_event} log is what {@code episode_watch} and
     * {@code watchlist_item} are derived from; keeping events for an episode the
     * user says they never watched would mean any recomputation silently
     * restored it.
     *
     * @return whether anything was actually watched to begin with
     */
    @Transactional
    public boolean unmarkEpisode(Long userId, Long episodeId) {
        requireUser(userId);
        Episode episode = episodes.findById(episodeId)
                .orElseThrow(() -> NotFoundException.of("Episode", episodeId));

        EpisodeWatch watch = episodeWatches.findByUserIdAndEpisodeId(userId, episodeId).orElse(null);
        List<WatchEvent> events = watchEvents.findByUserIdAndEpisodeId(userId, episodeId);

        if (watch == null && events.isEmpty()) {
            // Idempotent: unmarking something already unwatched is a no-op, not
            // an error. A client retrying after a dropped response must not see
            // a failure for work that is already done.
            return false;
        }

        if (watch != null) {
            episodeWatches.delete(watch);
        }
        watchEvents.deleteAll(events);

        recomputeSeriesStatus(userId, episode.getTitle());
        return true;
    }

    /**
     * Deletes one history entry, leaving the rest of the episode's history
     * intact.
     *
     * <p>Distinct from {@link #unmarkEpisode}: removing one of three rewatches
     * should leave the episode watched, with a lower watch count. Only when the
     * last event for an episode goes does the episode stop being watched.
     */
    @Transactional
    public void deleteWatchEvent(Long userId, Long eventId) {
        WatchEvent event = watchEvents.findById(eventId)
                .orElseThrow(() -> NotFoundException.of("Watch event", eventId));

        // 404 rather than 403 for somebody else's event: a 403 would confirm it
        // exists, and event ids are sequential.
        if (!event.getUser().getId().equals(userId)) {
            throw NotFoundException.of("Watch event", eventId);
        }

        Episode episode = event.getEpisode();
        Title title = event.getTitle();
        watchEvents.delete(event);

        if (episode != null) {
            List<WatchEvent> remaining = watchEvents.findByUserIdAndEpisodeId(userId, episode.getId());
            episodeWatches.findByUserIdAndEpisodeId(userId, episode.getId()).ifPresent(watch -> {
                if (remaining.isEmpty()) {
                    episodeWatches.delete(watch);
                } else {
                    // The count follows the history rather than being
                    // decremented blindly, so the two cannot drift.
                    watch.setWatchCount(remaining.size());
                    watch.setWatchedAt(remaining.stream()
                            .map(WatchEvent::getWatchedAt)
                            .max(java.util.Comparator.naturalOrder())
                            .orElse(watch.getWatchedAt()));
                    episodeWatches.save(watch);
                }
            });
        }

        recomputeSeriesStatus(userId, title);
    }

    /**
     * Recomputes a series' status after history was removed.
     *
     * <p>Unlike {@link #advanceSeriesStatus} this moves in both directions: a
     * series can fall out of COMPLETED, and back to WATCHLIST when nothing is
     * watched any more.
     *
     * <p>ON_HOLD and DROPPED are left alone. Those are statements the user made
     * about their intent, and unmarking an episode is not a reason to overrule
     * them.
     */
    private void recomputeSeriesStatus(Long userId, Title title) {
        items.findByUserIdAndTitleId(userId, title.getId()).ifPresent(item -> {
            if (item.getStatus() == WatchStatus.ON_HOLD || item.getStatus() == WatchStatus.DROPPED) {
                return;
            }
            long aired = episodes.countAiredByTitleId(title.getId());
            long watched = episodeWatches.countByUserIdAndTitleId(userId, title.getId());

            WatchStatus next;
            if (watched == 0) {
                next = WatchStatus.WATCHLIST;
            } else if (aired > 0 && watched >= aired) {
                next = WatchStatus.COMPLETED;
            } else {
                next = WatchStatus.WATCHING;
            }
            item.transitionTo(next);
            items.save(item);
        });
    }

    /** Progress through the aired episodes of a series, plus what to watch next. */
    @Transactional(readOnly = true)
    public TitleProgress progress(Long userId, Long titleId) {
        Title title = titles.findById(titleId)
                .orElseThrow(() -> NotFoundException.of("Title", titleId));

        // Season 0 is TMDB's specials bucket; counting it would distort progress.
        List<Episode> aired = episodes.findAiredByTitleId(titleId).stream()
                .filter(e -> e.getSeasonNumber() != null && e.getSeasonNumber() > 0)
                .toList();

        Set<Long> watched = new HashSet<>(episodeWatches.findWatchedEpisodeIds(userId, titleId));

        Episode next = aired.stream()
                .filter(e -> !watched.contains(e.getId()))
                .findFirst()
                .orElse(null);

        int watchedCount = (int) aired.stream().filter(e -> watched.contains(e.getId())).count();
        int remainingMinutes = aired.stream()
                .filter(e -> !watched.contains(e.getId()))
                .mapToInt(e -> e.getRuntimeMinutes() != null
                        ? e.getRuntimeMinutes()
                        : (title.getRuntimeMinutes() != null ? title.getRuntimeMinutes() : 0))
                .sum();
        int percent = aired.isEmpty() ? 0 : (int) Math.round(watchedCount * 100.0 / aired.size());

        return new TitleProgress(
                title.getId(),
                title.getPrimaryTitle(),
                aired.size(),
                watchedCount,
                percent,
                next == null ? null : next.getId(),
                next == null ? null : next.code(),
                next == null ? null : next.getName(),
                remainingMinutes);
    }

    private StreamingService resolveService(Long serviceId) {
        return serviceId == null ? null : services.findById(serviceId)
                .orElseThrow(() -> NotFoundException.of("Streaming service", serviceId));
    }

    private AppUser requireUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
    }

    /** Loads an item and enforces that it belongs to the calling user. */
    private WatchlistItem requireItem(Long userId, Long itemId) {
        WatchlistItem item = items.findById(itemId)
                .orElseThrow(() -> NotFoundException.of("Watchlist item", itemId));
        if (!item.getUser().getId().equals(userId)) {
            throw NotFoundException.of("Watchlist item", itemId);
        }
        return item;
    }
}
