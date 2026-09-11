package com.dhuelin.dev.watchguru.imports.service;

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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

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
     * <p>Midday, in the user's own zone. Midnight would land on the previous
     * evening for anybody west of Greenwich; midday in UTC would land on the
     * next day for anybody at UTC+13, which is a real place with a real date
     * line. Midday where they are is the only reading that puts an imported
     * row on the day the file says.
     */
    private static final LocalTime IMPORTED_TIME_OF_DAY = LocalTime.NOON;

    private final WatchlistService watchlist;
    private final WatchlistItemRepository items;
    private final TitleRepository titles;
    private final EpisodeRepository episodes;
    private final Clock clock;

    public ImportWriter(WatchlistService watchlist,
                        WatchlistItemRepository items,
                        TitleRepository titles,
                        EpisodeRepository episodes,
                        Clock clock) {
        this.watchlist = watchlist;
        this.items = items;
        this.titles = titles;
        this.episodes = episodes;
        this.clock = clock;
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

        // Checked before anything is written. An episode row whose episode
        // was never resolved used to create the library entry and apply its
        // rating on the way to returning false, so a row the import reported
        // as skipped had still changed the library.
        if (row.episodeId() == null && row.row().isEpisode()) {
            return false;
        }

        Episode episode = null;
        if (row.episodeId() != null) {
            episode = episodes.findById(row.episodeId()).orElse(null);
            // The two ids arrive from the client independently, and nothing
            // upstream ties them together. An unchecked pair would put title A
            // in the library and a watch event on episode B -- advancing a
            // series the user never mentioned.
            if (episode == null || !episode.getTitle().getId().equals(title.getId())) {
                throw new IllegalArgumentException(
                        "episode " + row.episodeId() + " does not belong to title " + title.getId());
            }
        }

        // The library entry first: a watch event for a title that is not in
        // your library is history with no home.
        WatchlistItem item = items.findByUserIdAndTitleId(user.getId(), title.getId())
                .orElseGet(() -> {
                    WatchlistItem fresh = new WatchlistItem(user, title, WatchStatus.WATCHLIST);
                    fresh.setOrigin(WatchOrigin.FILE_IMPORT);
                    fresh.setOriginRef(row.row().sourceRef());
                    return items.save(fresh);
                });

        applyRating(item, row.row().rating());

        // A row with no date is "watched today" -- today where the user is,
        // at the same midday every other imported row gets. Passing null would
        // hand the decision to WatchlistService, whose fallback is the current
        // instant, which is a different day across a date boundary and a
        // different time of day from every dated row in the same file.
        LocalDate watchedOn = row.row().watchedAt() == null
                ? LocalDate.now(clock.withZone(user.zone()))
                : row.row().watchedAt();
        Instant watchedAt = watchedOn.atTime(IMPORTED_TIME_OF_DAY).atZone(user.zone()).toInstant();

        if (episode != null) {
            watchlist.logEpisodeWatched(user.getId(), episode.getId(), watchedAt, null,
                    WatchOrigin.FILE_IMPORT, row.row().sourceRef());
            return true;
        }

        watchlist.logMovieWatched(user.getId(), title.getId(), watchedAt, null,
                WatchOrigin.FILE_IMPORT, row.row().sourceRef());
        return true;
    }

    /**
     * Takes the rating from the file, but never over one the user gave.
     *
     * <p>A rating already on the item was typed by this person in this app.
     * An imported one is what some other service recorded, possibly years ago
     * and on a different scale; overwriting the first with the second would
     * lose the only opinion here that was expressed deliberately.
     */
    private void applyRating(WatchlistItem item, BigDecimal rating) {
        if (rating == null || item.getUserRating() != null) {
            return;
        }
        item.setUserRating(rating);
        items.save(item);
    }
}
