package com.dhuelin.dev.watchguru.imports.domain;

import java.util.List;

/**
 * One export row, and what the catalogue made of it.
 *
 * @param row        what was in the file
 * @param status     whether it can be imported as it stands
 * @param titleId    the title it matched, when it matched exactly one
 * @param titleName  that title's name, so the preview can be read without a
 *                   second round of lookups
 * @param episodeId  the episode it matched, for an episode row
 * @param episodeCode e.g. {@code S05E14}, for the preview
 * @param candidates the plausible titles, when there was more than one
 * @param note       why this row is not a plain match, in the user's terms
 */
public record MatchedRow(
        ImportRow row,
        MatchStatus status,
        Long titleId,
        String titleName,
        Long episodeId,
        String episodeCode,
        List<Candidate> candidates,
        String note
) {
    public record Candidate(Long titleId, String titleName, Integer year) {
    }

    public static MatchedRow matched(ImportRow row, Long titleId, String titleName,
                                     Long episodeId, String episodeCode, String note) {
        return new MatchedRow(row, MatchStatus.MATCHED, titleId, titleName, episodeId, episodeCode, List.of(), note);
    }

    public static MatchedRow ambiguous(ImportRow row, List<Candidate> candidates) {
        return new MatchedRow(row, MatchStatus.AMBIGUOUS, null, null, null, null, candidates,
                "More than one title matches this name.");
    }

    public static MatchedRow unmatched(ImportRow row, String note) {
        return new MatchedRow(row, MatchStatus.UNMATCHED, null, null, null, null, List.of(), note);
    }
}
