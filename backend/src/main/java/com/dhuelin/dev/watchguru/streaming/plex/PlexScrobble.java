package com.dhuelin.dev.watchguru.streaming.plex;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.imports.domain.ImportRow;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns what Plex played into the row shape the importer already matches.
 *
 * <p>Deliberately the same {@link ImportRow} a CSV line becomes: a scrobble and
 * an exported line are the same claim -- this person watched this thing -- and
 * giving them one shape means one matcher, which has already learned that a
 * year separates a remake from what it remade and that an ambiguous name is
 * not a match.
 */
public final class PlexScrobble {

    private PlexScrobble() {
    }

    /**
     * The row a scrobble means, or empty when it is not a film or an episode.
     *
     * <p>Plex scrobbles music and photos through the same webhook, and a
     * library section this app does not model is not an error -- it is simply
     * not ours.
     */
    public static Optional<ImportRow> asRow(PlexWebhookPayload payload, LocalDate watchedOn) {
        PlexWebhookPayload.Metadata metadata = payload.metadata();
        if (metadata == null || metadata.type() == null) {
            return Optional.empty();
        }
        String ref = originRef(metadata.ratingKey(), watchedOn);

        return switch (metadata.type().toLowerCase(Locale.ROOT)) {
            case "movie" -> blank(metadata.title()) ? Optional.empty() : Optional.of(new ImportRow(
                    ref, metadata.title().trim(), metadata.year(), imdbId(metadata.guids()),
                    TitleType.MOVIE, null, null, null, watchedOn, null));

            case "episode" -> episodeRow(metadata, ref, watchedOn);

            default -> Optional.empty();
        };
    }

    /**
     * How a viewing is identified for the unique index on (user, origin,
     * origin_ref), which is what makes a repeated delivery a no-op.
     *
     * <p>The rating key alone would be wrong in one direction and the delivery
     * alone wrong in the other. A rating key identifies the episode on that
     * server, so on its own it would swallow a genuine rewatch next year; Plex
     * sends no session id, so there is nothing that distinguishes one sitting
     * from the next. The date is the compromise: replays and duplicate
     * deliveries within a day write nothing, and watching the same episode
     * twice on one day is recorded once. That second case is rare and it is
     * the one this trades away knowingly.
     */
    public static String originRef(String ratingKey, LocalDate watchedOn) {
        return "plex:" + (ratingKey == null ? "unknown" : ratingKey) + ":" + watchedOn;
    }

    private static Optional<ImportRow> episodeRow(PlexWebhookPayload.Metadata metadata,
                                                  String ref, LocalDate watchedOn) {
        if (blank(metadata.grandparentTitle()) || metadata.parentIndex() == null) {
            return Optional.empty();
        }
        // The series' name, and deliberately no year. Plex's `year` on an
        // episode is that episode's air year, not the series' first: passing it
        // through would ask the matcher for a series called Breaking Bad that
        // began in 2013, and the catalogue holds one that began in 2008.
        //
        // The Guid list is dropped for the same reason -- those are the
        // episode's external ids, and title.imdb_id holds the series'. An
        // episode's tt-id looked up as a series' is not a near miss; it is a
        // different record.
        return Optional.of(new ImportRow(
                ref, metadata.grandparentTitle().trim(), null, null, TitleType.TV_SERIES,
                metadata.parentIndex(), metadata.index(), emptyToNull(metadata.title()),
                watchedOn, null));
    }

    /** The IMDb id among Plex's resolved guids, where it resolved one. */
    private static String imdbId(List<PlexWebhookPayload.Metadata.Guid> guids) {
        if (guids == null) {
            return null;
        }
        return guids.stream()
                .map(PlexWebhookPayload.Metadata.Guid::id)
                .filter(id -> id != null && id.startsWith("imdb://"))
                .map(id -> id.substring("imdb://".length()))
                .filter(id -> id.startsWith("tt"))
                .findFirst()
                .orElse(null);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String emptyToNull(String value) {
        return blank(value) ? null : value.trim();
    }
}
