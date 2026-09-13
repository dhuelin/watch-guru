package com.dhuelin.dev.watchguru.streaming.trakt;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.imports.domain.ImportRow;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns a Trakt history entry into the row shape the importer already matches.
 *
 * <p>The same {@link ImportRow} a CSV line and a Plex scrobble become, for the
 * same reason: they are all one claim -- this person watched this thing -- and
 * one shape means one matcher.
 *
 * <p>Trakt is the easiest of the three to read, because it carries IMDb ids and
 * carries the right ones: a movie's own, and for an episode the *show's*, which
 * is exactly what {@code title.imdb_id} holds. Plex's episode guids are the
 * episode's and have to be dropped; these do not.
 */
public final class TraktHistory {

    private TraktHistory() {
    }

    /**
     * The row an entry means, or empty when it is not a film or an episode.
     *
     * @param zone the user's own, only for the date on the row. The event's
     *             real instant is Trakt's {@code watched_at} and is used as-is
     */
    public static Optional<ImportRow> asRow(TraktResponses.HistoryItem item, ZoneId zone) {
        if (item == null || item.type() == null || item.watchedAt() == null) {
            return Optional.empty();
        }
        String ref = originRef(item.id());

        return switch (item.type().toLowerCase(Locale.ROOT)) {
            case "movie" -> movieRow(item, ref, zone);
            case "episode" -> episodeRow(item, ref, zone);
            default -> Optional.empty();
        };
    }

    /**
     * How a viewing is identified for the unique index on (user, origin,
     * origin_ref).
     *
     * <p>Trakt's own id for the history entry, which is unique for ever and
     * distinct across rewatches. Nothing has to be inferred from dates here,
     * unlike Plex: syncing the same window twice writes nothing the second
     * time, and a genuine second viewing of the same episode an hour later is
     * still a second entry with its own id.
     */
    public static String originRef(long historyId) {
        return "trakt:" + historyId;
    }

    private static Optional<ImportRow> movieRow(TraktResponses.HistoryItem item, String ref, ZoneId zone) {
        TraktResponses.Movie movie = item.movie();
        if (movie == null || movie.title() == null || movie.title().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ImportRow(
                ref, movie.title().trim(), movie.year(), imdbId(movie.ids()), TitleType.MOVIE,
                null, null, null, item.watchedAt().atZone(zone).toLocalDate(), null));
    }

    private static Optional<ImportRow> episodeRow(TraktResponses.HistoryItem item, String ref, ZoneId zone) {
        TraktResponses.Episode episode = item.episode();
        TraktResponses.Show show = item.show();
        if (episode == null || show == null || show.title() == null || show.title().isBlank()
                || episode.season() == null) {
            return Optional.empty();
        }
        // The show's year and the show's IMDb id, both of which are the
        // series' own -- so an episode row identifies its series as exactly as
        // a movie row identifies its film.
        return Optional.of(new ImportRow(
                ref, show.title().trim(), show.year(), imdbId(show.ids()), TitleType.TV_SERIES,
                episode.season(), episode.number(), blankToNull(episode.title()),
                item.watchedAt().atZone(zone).toLocalDate(), null));
    }

    private static String imdbId(TraktResponses.Ids ids) {
        if (ids == null || ids.imdb() == null || !ids.imdb().startsWith("tt")) {
            return null;
        }
        return ids.imdb();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
