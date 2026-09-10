package com.dhuelin.dev.watchguru.imports.domain;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of somebody's export, in terms this codebase understands.
 *
 * <p>Everything is nullable except the title text, because every one of these
 * fields is absent from at least one of the four supported exports. Netflix
 * gives a display string and a date and nothing else; an IMDb ratings export
 * gives an IMDb id and a rating but no watch date at all.
 *
 * @param sourceRef     stable identity of this row within its file, used as
 *                      {@code origin_ref} so re-importing the same export
 *                      writes nothing the second time
 * @param titleText     what the export called it; matching starts here
 * @param year          release year, where the export gives one -- the single
 *                      most useful disambiguator for a remake
 * @param imdbId        {@code tt...}, where the export gives one. Worth more
 *                      than the title and year together, since it is exact
 * @param titleType     where the export distinguishes films from series
 * @param seasonNumber  for an episode row
 * @param episodeNumber for an episode row, where the export numbers episodes
 * @param episodeName   for an episode row, where it names them instead --
 *                      Netflix gives "Series: Season 5: Ozymandias" and no
 *                      number anywhere
 * @param watchedAt     the date it was watched, where the export says
 * @param rating        the user's own rating, 0-10, converted from whatever
 *                      scale the source used
 */
public record ImportRow(
        String sourceRef,
        String titleText,
        Integer year,
        String imdbId,
        TitleType titleType,
        Integer seasonNumber,
        Integer episodeNumber,
        String episodeName,
        LocalDate watchedAt,
        BigDecimal rating
) {
    /** Whether this row points at a specific episode rather than a whole title. */
    public boolean isEpisode() {
        return seasonNumber != null && (episodeNumber != null || episodeName != null);
    }
}
