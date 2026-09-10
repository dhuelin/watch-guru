package com.dhuelin.dev.watchguru.imports.service;

import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import com.dhuelin.dev.watchguru.imports.domain.ImportSource;
import com.dhuelin.dev.watchguru.imports.domain.MatchStatus;
import com.dhuelin.dev.watchguru.imports.domain.MatchedRow;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchOrigin;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reading somebody's viewing history in, in two steps.
 *
 * <p>Preview then commit, deliberately. An import that silently marks four
 * hundred titles watched -- some of them wrongly -- is worse than no import at
 * all, and the only person who can tell a right match from a plausible one is
 * the person who watched them.
 *
 * <p>No server-side state between the two calls: the preview goes back to the
 * client, the client sends back what the user accepted. A parked import
 * sitting in a table would be one more thing to expire, clean up and get
 * wrong, and the file is the user's anyway.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ImportMatcher matcher;
    private final ImportWriter writer;
    private final WatchEventRepository watchEvents;

    public ImportService(ImportMatcher matcher, ImportWriter writer, WatchEventRepository watchEvents) {
        this.matcher = matcher;
        this.writer = writer;
        this.watchEvents = watchEvents;
    }

    /** What a file would do, before it does anything. */
    public record Preview(ImportSource source,
                          List<MatchedRow> rows,
                          List<String> problems,
                          List<String> warnings,
                          int alreadyImported) {
    }

    /** What a commit actually did. */
    public record Result(int imported, int skipped, int failed, List<String> problems) {
    }

    @Transactional
    public Preview preview(AppUser user, String fileContents, int providerLookups) {
        ImportParser.Parsed parsed = ImportParser.parse(fileContents);
        List<MatchedRow> matched = matcher.match(parsed.rows(), providerLookups);

        Set<String> existing = parsed.rows().isEmpty() ? Set.of()
                : watchEvents.findExistingOriginRefs(user.getId(), WatchOrigin.FILE_IMPORT,
                        parsed.rows().stream().map(ImportRow::sourceRef).toList());

        return new Preview(parsed.source(), matched, parsed.problems(),
                warningsFor(parsed.source(), matched), existing.size());
    }

    /**
     * Writes the rows the user accepted.
     *
     * <p>Each row is its own unit of work: one row that cannot be written must
     * not cost the other three hundred, and the summary says how many did not
     * land rather than reporting a success that was partial.
     */
    public Result commit(AppUser user, List<MatchedRow> rows) {
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        List<String> problems = new ArrayList<>();

        for (MatchedRow row : rows) {
            if (row.status() != MatchStatus.MATCHED || row.titleId() == null) {
                skipped++;
                continue;
            }
            try {
                if (writer.write(user, row)) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (DataIntegrityViolationException e) {
                // The unique index on (user, origin, origin_ref) fired: this
                // row is already in, from an earlier run of the same file.
                skipped++;
            } catch (RuntimeException e) {
                failed++;
                problems.add(row.row().titleText() + ": " + e.getMessage());
                log.warn("Import row failed for user {}: {}", user.getId(), e.toString());
            }
        }
        return new Result(imported, skipped, failed, problems);
    }

    /**
     * What the user should know about this source before agreeing to it.
     *
     * <p>Each of these is a place where the export does not mean quite what it
     * appears to. Saying so in the preview is the difference between an import
     * the user consented to and one they were surprised by.
     */
    private List<String> warningsFor(ImportSource source, List<MatchedRow> rows) {
        List<String> warnings = new ArrayList<>();
        switch (source) {
            case IMDB -> warnings.add(
                    "IMDb exports carry the date you rated a title, not the date you watched it. "
                            + "Imported entries will use the rating date.");
            case NETFLIX -> warnings.add(
                    "Netflix names episodes but does not number them, so an episode is matched by "
                            + "its name. Anything it cannot find is listed below rather than guessed at.");
            case LETTERBOXD -> warnings.add(
                    "Letterboxd ratings are out of five and are doubled to this app's scale.");
            case WATCH_GURU, UNKNOWN -> {
                // Nothing surprising: it is this app's own documented format.
            }
        }
        long undated = rows.stream()
                .filter(r -> r.status() == MatchStatus.MATCHED && r.row().watchedAt() == null)
                .count();
        if (undated > 0) {
            warnings.add(undated + " row(s) have no date. They will be recorded as watched today.");
        }
        return warnings;
    }
}
