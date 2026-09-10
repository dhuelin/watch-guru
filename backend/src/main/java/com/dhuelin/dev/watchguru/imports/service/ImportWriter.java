package com.dhuelin.dev.watchguru.imports.service;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
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
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Writes one imported row, in a transaction of its own.
 *
 * <p>A separate bean from {@link ImportService} for a mechanical reason rather
 * than a tidiness one: a call from one method of a bean to another goes
 * straight to the object and never through the proxy, so an {@code importOne}
 * living next to the loop that calls it would not be transactional at all.
 * Each row needs its own transaction so that a row which cannot be written
 * fails alone instead of taking the other three hundred with it.
 */
@Service
public class ImportWriter {

    /**
     * Import rows are dates; watch events are instants.
     *
     * <p>Midday rather than midnight. A date imported as midnight UTC lands on
     * the previous evening for anybody west of Greenwich, which would shift a
     * whole imported history back by a day for most of the Americas.
     */
    private static final LocalTime IMPORTED_TIME_OF_DAY = LocalTime.NOON;

    private final WatchlistService watchlist;
    private final WatchlistItemRepository items;
    private final TitleRepository titles;

    public ImportWriter(WatchlistService watchlist,
                        WatchlistItemRepository items,
                        TitleRepository titles) {
        this.watchlist = watchlist;
        this.items = items;
        this.titles = titles;
    }

    /**
     * @return whether anything was written
     * @throws org.springframework.dao.DataIntegrityViolationException if this
     *         row has already been imported -- the unique index on (user,
     *         origin, origin_ref) is what makes a repeated import a no-op
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean write(AppUser user, MatchedRow row) {
        Title title = titles.findById(row.titleId()).orElse(null);
        if (title == null) {
            return false;
        }

        // The library entry first: a watch event for a title that is not in
        // your library is history with no home.
        items.findByUserIdAndTitleId(user.getId(), title.getId())
                .orElseGet(() -> {
                    WatchlistItem item = new WatchlistItem(user, title, WatchStatus.WATCHLIST);
                    item.setOrigin(WatchOrigin.FILE_IMPORT);
                    item.setOriginRef(row.row().sourceRef());
                    return items.save(item);
                });

        Instant watchedAt = row.row().watchedAt() == null
                ? null
                : row.row().watchedAt().atTime(IMPORTED_TIME_OF_DAY).toInstant(ZoneOffset.UTC);

        if (row.episodeId() != null) {
            watchlist.logEpisodeWatched(user.getId(), row.episodeId(), watchedAt, null,
                    WatchOrigin.FILE_IMPORT, row.row().sourceRef());
            return true;
        }

        if (row.row().isEpisode()) {
            // An episode row whose episode was never resolved. Recording it
            // against the series would claim something the file did not say.
            return false;
        }

        watchlist.logMovieWatched(user.getId(), title.getId(), watchedAt, null,
                WatchOrigin.FILE_IMPORT, row.row().sourceRef());
        return true;
    }
}
