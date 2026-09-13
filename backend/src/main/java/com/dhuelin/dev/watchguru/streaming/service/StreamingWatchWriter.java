package com.dhuelin.dev.watchguru.streaming.service;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.imports.domain.MatchedRow;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchOrigin;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Writes the watch a linked service reported, in a transaction of its own.
 *
 * <p>Shared by every such service -- a Plex scrobble and a Trakt history entry
 * are the same claim once matched, and a second copy of this would be a second
 * place for the library entry, the rewatch flag and the series status to drift.
 *
 * <p>Its own bean for the same mechanical reason as {@code ImportWriter}: a
 * call between two methods of one bean never passes through the proxy, so the
 * new transaction has to start in a different object. It matters here because
 * a duplicate arrival ends in a unique-index violation, and a violation inside
 * the caller's transaction would take the sync-run record down with it -- the
 * record whose whole job is to say what happened.
 */
@Service
public class StreamingWatchWriter {

    private final WatchlistService watchlist;
    private final WatchlistItemRepository items;
    private final TitleRepository titles;
    private final EpisodeRepository episodes;

    public StreamingWatchWriter(WatchlistService watchlist,
                           WatchlistItemRepository items,
                           TitleRepository titles,
                           EpisodeRepository episodes) {
        this.watchlist = watchlist;
        this.items = items;
        this.titles = titles;
        this.episodes = episodes;
    }

    /**
     * @param serviceId the service's row in {@code streaming_service}, so the
     *                  event can say where it came from and the statistics
     *                  screen can count it
     * @return whether anything was written
     * @throws org.springframework.dao.DataIntegrityViolationException when this
     *         viewing is already recorded -- the unique index on (user, origin,
     *         origin_ref) is what makes a replayed delivery a no-op
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean write(AppUser user, MatchedRow row, Long serviceId, Instant watchedAt) {
        Title title = titles.findById(row.titleId()).orElse(null);
        if (title == null) {
            return false;
        }

        Episode episode = null;
        if (row.episodeId() != null) {
            episode = episodes.findById(row.episodeId()).orElse(null);
            if (episode == null || !episode.getTitle().getId().equals(title.getId())) {
                return false;
            }
        } else if (row.row().isEpisode()) {
            // An episode the catalogue could not place is not "watched the
            // series". Reported by the caller instead.
            return false;
        }

        // A watch event for a title that is not in the library is history with
        // no home: someone who starts a new series elsewhere should find it in
        // their library afterwards, not only in their statistics.
        items.findByUserIdAndTitleId(user.getId(), title.getId())
                .orElseGet(() -> {
                    WatchlistItem fresh = new WatchlistItem(user, title, WatchStatus.WATCHING);
                    fresh.setOrigin(WatchOrigin.STREAMING_SYNC);
                    fresh.setOriginRef(row.row().sourceRef());
                    return items.save(fresh);
                });

        if (episode != null) {
            watchlist.logEpisodeWatched(user.getId(), episode.getId(), watchedAt, serviceId,
                    WatchOrigin.STREAMING_SYNC, row.row().sourceRef());
        } else {
            watchlist.logMovieWatched(user.getId(), title.getId(), watchedAt, serviceId,
                    WatchOrigin.STREAMING_SYNC, row.row().sourceRef());
        }
        return true;
    }
}
