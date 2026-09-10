package com.dhuelin.dev.watchguru.imdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.math.BigDecimal;

/**
 * Parses IMDb's {@code title.ratings.tsv}.
 *
 * <p>The format is three tab-separated columns after a header line:
 * {@code tconst}, {@code averageRating}, {@code numVotes}. IMDb uses the
 * literal string {@code \N} for a missing value in these datasets.
 *
 * <p>Kept free of I/O and database access so the parsing rules can be tested
 * against fixtures rather than a 25MB download.
 */
public final class ImdbRatingsParser {

    private static final Logger log = LoggerFactory.getLogger(ImdbRatingsParser.class);

    /** IMDb's null marker. */
    private static final String NULL_MARKER = "\\N";

    /** The schema's column is NUMERIC(3,1), so anything outside this cannot be stored. */
    private static final BigDecimal MIN_RATING = new BigDecimal("0.0");
    private static final BigDecimal MAX_RATING = new BigDecimal("10.0");

    private ImdbRatingsParser() {
    }

    /**
     * Parses one data line, or returns null if it cannot be used.
     *
     * <p>Returning null rather than throwing is deliberate. This runs over
     * millions of rows from a third party; one malformed line must not abort a
     * job that would otherwise have updated everything else correctly.
     */
    public static ImdbRating parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        // Split with a limit so a stray tab in trailing data cannot silently
        // shift the columns.
        String[] fields = line.split("\t", 4);
        if (fields.length < 3) {
            return null;
        }

        String tconst = fields[0].trim();
        if (tconst.isEmpty() || NULL_MARKER.equals(tconst) || !tconst.startsWith("tt")) {
            return null;
        }
        // title.imdb_id is VARCHAR(16); a longer value is not one of ours.
        if (tconst.length() > 16) {
            return null;
        }

        BigDecimal rating = parseRating(fields[1].trim());
        if (rating == null) {
            return null;
        }

        Integer votes = parseVotes(fields[2].trim());
        if (votes == null) {
            return null;
        }

        return new ImdbRating(tconst, rating, votes);
    }

    /** True for the header line IMDb puts at the top of every dataset. */
    public static boolean isHeader(String line) {
        return line != null && line.startsWith("tconst\t");
    }

    private static BigDecimal parseRating(String value) {
        if (value.isEmpty() || NULL_MARKER.equals(value)) {
            return null;
        }
        try {
            BigDecimal rating = new BigDecimal(value);
            if (rating.compareTo(MIN_RATING) < 0 || rating.compareTo(MAX_RATING) > 0) {
                // Out of range for NUMERIC(3,1); storing it would throw at the
                // database and take the whole batch with it.
                return null;
            }
            return rating.setScale(1, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.trace("Unparseable IMDb rating: {}", value);
            return null;
        }
    }

    private static Integer parseVotes(String value) {
        if (value.isEmpty() || NULL_MARKER.equals(value)) {
            return null;
        }
        try {
            int votes = Integer.parseInt(value);
            return votes < 0 ? null : votes;
        } catch (NumberFormatException e) {
            log.trace("Unparseable IMDb vote count: {}", value);
            return null;
        }
    }

    /** Convenience for tests and small inputs; the job streams instead. */
    public static java.util.List<ImdbRating> parseAll(BufferedReader reader) throws java.io.IOException {
        java.util.List<ImdbRating> ratings = new java.util.ArrayList<>();
        String line;
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (first) {
                first = false;
                if (isHeader(line)) {
                    continue;
                }
            }
            ImdbRating rating = parseLine(line);
            if (rating != null) {
                ratings.add(rating);
            }
        }
        return ratings;
    }
}
