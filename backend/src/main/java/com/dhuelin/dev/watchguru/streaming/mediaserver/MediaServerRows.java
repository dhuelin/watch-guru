package com.dhuelin.dev.watchguru.streaming.mediaserver;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.imports.domain.ImportRow;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The rules every media server's playback event obeys once read.
 *
 * <p>Here rather than in each adapter because the rules are the same for all
 * three and getting one of them wrong is silent: the wrong title quietly marked
 * watched, which is the failure this whole area is built to avoid.
 */
public final class MediaServerRows {

    private MediaServerRows() {
    }

    /**
     * How a viewing is identified for the unique index on (user, origin,
     * origin_ref), which is what makes a repeated delivery a no-op.
     *
     * <p>The item's id on that server and the day it was watched. Neither alone
     * would do: the id on its own would swallow a genuine rewatch next year,
     * and none of these three sends a session id, so there is nothing that
     * distinguishes one sitting from the next. Replays and duplicate deliveries
     * within a day therefore write nothing, and watching the same episode twice
     * on one day is recorded once -- the one case this trades away knowingly.
     */
    public static String originRef(String slug, String itemKey, LocalDate watchedOn) {
        return slug + ":" + (itemKey == null || itemKey.isBlank() ? "unknown" : itemKey)
                + ":" + watchedOn;
    }

    /** A film, which may carry its own IMDb id and its own year. */
    public static Optional<ImportRow> movie(String slug, String itemKey, String title,
                                            Integer year, String imdbId, LocalDate watchedOn) {
        if (isBlank(title)) {
            return Optional.empty();
        }
        return Optional.of(new ImportRow(
                originRef(slug, itemKey, watchedOn), title.trim(), year, imdbId(imdbId),
                TitleType.MOVIE, null, null, null, watchedOn, null));
    }

    /**
     * An episode, identified by its series and its numbers -- and deliberately
     * by nothing else.
     *
     * <p>The two fields dropped here are the ones that look helpful and are
     * wrong. A media server's {@code year} on an episode is that episode's air
     * year: Breaking Bad's Ozymandias says 2013, and the series began in 2008,
     * so passing it through asks the catalogue for a series that does not
     * exist. Its IMDb id is the episode's, and {@code title.imdb_id} holds the
     * series' -- an episode's tt-id looked up as a series' is not a near miss,
     * it is a different record.
     */
    public static Optional<ImportRow> episode(String slug, String itemKey, String seriesName,
                                              Integer season, Integer number, String episodeName,
                                              LocalDate watchedOn) {
        if (isBlank(seriesName) || season == null) {
            return Optional.empty();
        }
        return Optional.of(new ImportRow(
                originRef(slug, itemKey, watchedOn), seriesName.trim(), null, null,
                TitleType.TV_SERIES, season, number, blankToNull(episodeName), watchedOn, null));
    }

    /** An IMDb id only where it is one. */
    private static String imdbId(String value) {
        return value != null && value.startsWith("tt") ? value : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
