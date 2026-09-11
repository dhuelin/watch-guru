package com.dhuelin.dev.watchguru.imports.service;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.catalog.service.CatalogService;
import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import com.dhuelin.dev.watchguru.imports.domain.MatchedRow;
import com.dhuelin.dev.watchguru.provider.MetadataProviderException;
import com.dhuelin.dev.watchguru.provider.model.ProviderTitleSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

/**
 * Works out which catalogue title, if any, a row of somebody's export means.
 *
 * <p>In order of how much the evidence is worth: an IMDb id is exact, so it
 * wins outright; a name and year in the local catalogue is next; and only then
 * the metadata provider, which is a network call and is therefore budgeted.
 *
 * <p>Nothing here writes a watch. Matching produces a preview, and the user
 * agrees to it before anything is recorded, because an import that quietly
 * marks four hundred titles watched -- wrongly -- is worse than no import.
 */
@Service
public class ImportMatcher {

    private static final Logger log = LoggerFactory.getLogger(ImportMatcher.class);

    private final TitleRepository titles;
    private final EpisodeRepository episodes;
    private final CatalogService catalog;

    /**
     * Catalogue searches already made while matching the file in hand.
     *
     * <p>Held for the duration of one {@link #match} call and cleared at its
     * start: within a file the catalogue does not move, and across files it
     * might.
     */
    private final Map<String, List<Title>> searchCache = new HashMap<>();

    public ImportMatcher(TitleRepository titles, EpisodeRepository episodes, CatalogService catalog) {
        this.titles = titles;
        this.episodes = episodes;
        this.catalog = catalog;
    }

    /**
     * Matches a whole file's worth of rows.
     *
     * @param providerLookups how many rows may be looked up against the
     *                        metadata provider. A decade of Netflix history is
     *                        thousands of rows, and firing thousands of
     *                        searches at an upstream on one button press is how
     *                        an API key gets suspended. Rows past the budget
     *                        are reported as not looked up, which the user can
     *                        act on, rather than as "not found", which is a
     *                        claim this has not earned.
     */
    @Transactional
    public List<MatchedRow> match(List<ImportRow> rows, int providerLookups) {
        List<MatchedRow> matched = new ArrayList<>(rows.size());
        int budget = providerLookups;
        // A Netflix history is one row per episode, so the same series name
        // arrives hundreds of times. Without this, a file of ten thousand rows
        // is ten thousand catalogue searches for a few hundred distinct names.
        searchCache.clear();

        for (ImportRow row : rows) {
            Title local = byImdbId(row).orElse(null);

            if (local == null) {
                List<Title> exact = exactLocalMatches(row);
                if (exact.size() == 1) {
                    local = exact.getFirst();
                } else if (exact.size() > 1) {
                    matched.add(MatchedRow.ambiguous(row, candidates(exact)));
                    continue;
                }
            }

            if (local == null) {
                if (budget <= 0) {
                    // Two different situations, and saying which is the whole
                    // point: one is worth re-running the file for, the other
                    // means this deployment never looks anything up.
                    matched.add(MatchedRow.unmatched(row, providerLookups > 0
                            ? "\"" + row.titleText() + "\" is not in your catalogue yet, and this "
                                    + "import has used all its lookups. Import the rest, then run "
                                    + "this file again."
                            : "\"" + row.titleText() + "\" is not in your catalogue, and this "
                                    + "import was not allowed to search for it."));
                    continue;
                }
                budget--;
                ProviderMatch provider = fromProvider(row);
                if (provider.unavailable()) {
                    matched.add(MatchedRow.unmatched(row,
                            "The catalogue provider could not be reached for \"" + row.titleText()
                                    + "\". Nothing is wrong with this row -- try the file again later."));
                    continue;
                }
                if (provider.ambiguous()) {
                    matched.add(MatchedRow.unmatched(row,
                            "Several titles are called \"" + row.titleText() + "\". Candidates are "
                                    + "offered for titles already in your catalogue; for one that is "
                                    + "not, add it once and this file will match it next time."));
                    continue;
                }
                local = provider.title();
            }

            if (local == null) {
                matched.add(MatchedRow.unmatched(row, "No title found for \"" + row.titleText() + "\"."));
                continue;
            }
            matched.add(withEpisode(row, local));
        }
        return matched;
    }

    /** Resolves the episode as well, where the row names one. */
    private MatchedRow withEpisode(ImportRow row, Title title) {
        if (!row.isEpisode()) {
            return MatchedRow.matched(row, title.getId(), title.getPrimaryTitle(), null, null, null);
        }

        Episode episode = null;
        if (row.episodeNumber() != null) {
            episode = episodes.findByTitleSeasonAndNumber(
                    title.getId(), row.seasonNumber(), row.episodeNumber()).orElse(null);
        } else if (row.episodeName() != null) {
            List<Episode> byName = episodes.findBySeasonAndName(
                    title.getId(), row.seasonNumber(), row.episodeName());
            if (byName.size() == 1) {
                episode = byName.getFirst();
            } else if (byName.size() > 1) {
                return MatchedRow.unmatched(row,
                        "Season " + row.seasonNumber() + " has more than one episode called \""
                                + row.episodeName() + "\".");
            }
        }

        if (episode == null) {
            // The series is right and the episode is not findable. Reported
            // rather than imported as "watched the series", which would be a
            // different claim than the file made.
            return MatchedRow.unmatched(row,
                    "Found " + title.getPrimaryTitle() + ", but not the episode this row names."
                            + " Open the series once so its episodes are fetched, then try again.");
        }
        return MatchedRow.matched(row, title.getId(), title.getPrimaryTitle(),
                episode.getId(), episode.code(), null);
    }

    private Optional<Title> byImdbId(ImportRow row) {
        return row.imdbId() == null ? Optional.empty() : titles.findByImdbId(row.imdbId());
    }

    private static List<MatchedRow.Candidate> candidates(List<Title> titles) {
        return titles.stream()
                .map(t -> new MatchedRow.Candidate(t.getId(), t.getPrimaryTitle(),
                        t.primaryReleaseDate() == null ? null : t.primaryReleaseDate().getYear()))
                .toList();
    }

    /** Exact name matches in the local catalogue, narrowed by type and year. */

    private List<Title> exactLocalMatches(ImportRow row) {
        List<Title> candidates = searchCache
                .computeIfAbsent(row.titleText().trim().toLowerCase(Locale.ROOT),
                        term -> titles.searchCached(term, PageRequest.of(0, 25)).getContent())
                .stream()
                .filter(t -> equalsIgnoreCase(t.getPrimaryTitle(), row.titleText())
                        || equalsIgnoreCase(t.getOriginalTitle(), row.titleText()))
                .filter(t -> row.titleType() == null || t.getTitleType() == row.titleType())
                .toList();

        if (row.year() == null) {
            return candidates;
        }
        // The year is what separates a remake from what it remade. A candidate
        // whose year we hold and which disagrees is out -- including when it
        // is the only one, which is how a 1996 Fargo used to match a 2019 one.
        // A candidate whose year we do not hold survives: an unknown year is
        // not a contradiction.
        return candidates.stream()
                .filter(t -> t.primaryReleaseDate() == null
                        || t.primaryReleaseDate().getYear() == row.year())
                .toList();
    }

    /**
     * Last resort: ask the metadata provider.
     *
     * <p>Only an exact name match counts. The provider searches loosely and
     * returns its results by popularity, so taking the first hit would file
     * "Heat" under whatever is popular this month whenever the real title is
     * missing -- and a wrong match imported silently is the failure this whole
     * feature is built to avoid.
     *
     * <p>Several exact matches means the answer is genuinely unclear, and the
     * row is left for the user rather than decided for them.
     */
    private ProviderMatch fromProvider(ImportRow row) {
        try {
            List<ProviderTitleSummary> hits = catalog.search(row.titleText(), 1, null).results().stream()
                    .filter(hit -> row.titleType() == null || hit.titleType() == row.titleType())
                    .filter(hit -> row.year() == null || hit.releaseDate() == null
                            || hit.releaseDate().getYear() == row.year())
                    .filter(hit -> equalsIgnoreCase(hit.title(), row.titleText())
                            || equalsIgnoreCase(hit.originalTitle(), row.titleText()))
                    .toList();

            if (hits.isEmpty()) {
                return ProviderMatch.nothing();
            }
            if (hits.size() > 1) {
                return ProviderMatch.several();
            }

            ProviderTitleSummary hit = hits.getFirst();
            return ProviderMatch.one(catalog.importTitle(
                    hit.titleType() == null ? TitleType.MOVIE : hit.titleType(), hit.providerId(), null));
        } catch (MetadataProviderException e) {
            // An upstream that is down must not turn into "this title does not
            // exist", which is what a caught-and-ignored exception would say.
            log.warn("Provider lookup failed while importing \"{}\": {}", row.titleText(), e.getMessage());
            return ProviderMatch.nothing();
        }
    }

    /**
     * What the provider had to say: one title, several, nothing -- or nothing
     * because it could not be asked, which is a different row entirely.
     */
    private record ProviderMatch(Title title, boolean ambiguous, boolean unavailable) {
        static ProviderMatch one(Title title) {
            return new ProviderMatch(title, false, false);
        }

        static ProviderMatch nothing() {
            return new ProviderMatch(null, false, false);
        }

        static ProviderMatch several() {
            return new ProviderMatch(null, true, false);
        }

        static ProviderMatch couldNotAsk() {
            return new ProviderMatch(null, false, true);
        }
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }
}
