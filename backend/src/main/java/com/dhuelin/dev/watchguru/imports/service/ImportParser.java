package com.dhuelin.dev.watchguru.imports.service;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import com.dhuelin.dev.watchguru.imports.domain.ImportSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns somebody's export into rows this codebase can act on.
 *
 * <p>The format is detected from the header rather than chosen by the user.
 * Everybody knows which site they downloaded a file from; nobody knows which
 * of four radio buttons matches its columns, and picking wrong reads one
 * column as another and imports a plausible-looking lie.
 *
 * <p>Nothing here touches the database. Parsing is where the awkward input
 * lives -- four date formats, two rating scales, episodes identified by name
 * in one source and by number in another -- and it is worth being able to test
 * all of that without one.
 */
public final class ImportParser {

    /** Netflix writes US short dates: 3/4/21 is the fourth of March. */
    private static final DateTimeFormatter NETFLIX_DATE = DateTimeFormatter.ofPattern("M/d/yy", Locale.US);

    private ImportParser() {
    }

    /**
     * A stable identity for a row that carries no id of its own.
     *
     * <p>Content, not line number. Line numbers look stable within one file
     * and are not stable between two: re-importing a second export would find
     * "row:2" already used by the first and silently skip it, which is exactly
     * the kind of quiet wrongness an import must not have.
     */
    private static String contentRef(String prefix, String... parts) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(String.join("\u001f", java.util.Arrays.stream(parts)
                    .map(part -> part == null ? "" : part)
                    .toList()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return prefix + ":" + hex;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }

    /** What a file turned into, along with what could not be read. */
    public record Parsed(ImportSource source, List<ImportRow> rows, List<String> problems) {
    }

    public static Parsed parse(String text) {
        List<List<String>> csv = CsvReader.parse(text);
        if (csv.isEmpty()) {
            return new Parsed(ImportSource.UNKNOWN, List.of(), List.of("The file is empty."));
        }

        List<String> header = csv.getFirst();
        ImportSource source = detect(header);
        if (source == ImportSource.UNKNOWN) {
            return new Parsed(source, List.of(), List.of(
                    "Unrecognised columns: " + String.join(", ", header)
                            + ". See docs/IMPORT.md for the formats this reads."));
        }

        Map<String, Integer> columns = index(header);
        List<ImportRow> rows = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (int i = 1; i < csv.size(); i++) {
            List<String> row = csv.get(i);
            try {
                ImportRow parsed = switch (source) {
                    case WATCH_GURU -> watchGuru(columns, row);
                    case IMDB -> imdb(columns, row);
                    case LETTERBOXD -> letterboxd(columns, row);
                    case NETFLIX -> netflix(columns, row);
                    case UNKNOWN -> null;
                };
                if (parsed != null) {
                    rows.add(parsed);
                }
            } catch (RuntimeException e) {
                // One malformed line must not cost the user the other 400.
                // Reported rather than swallowed: a row that vanished quietly
                // is a row nobody knows to re-enter.
                problems.add("Line " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new Parsed(source, rows, problems);
    }

    static ImportSource detect(List<String> header) {
        List<String> lower = header.stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        if (lower.contains("letterboxd uri")) {
            return ImportSource.LETTERBOXD;
        }
        if (lower.contains("const") && lower.contains("title type")) {
            return ImportSource.IMDB;
        }
        if (lower.contains("watched_at") || (lower.contains("title") && lower.contains("imdb_id"))) {
            return ImportSource.WATCH_GURU;
        }
        // Last, and deliberately the narrowest test: Netflix's export is two
        // columns with the most generic names imaginable, so anything checked
        // after this would never be reached.
        if (lower.size() == 2 && lower.contains("title") && lower.contains("date")) {
            return ImportSource.NETFLIX;
        }
        return ImportSource.UNKNOWN;
    }

    private static ImportRow watchGuru(Map<String, Integer> columns, List<String> row) {
        String title = required(columns, row, "title");
        return new ImportRow(
                contentRef("wg",
                        title,
                        value(columns, row, "year"),
                        value(columns, row, "season"),
                        value(columns, row, "episode"),
                        value(columns, row, "watched_at")),
                title,
                integer(value(columns, row, "year")),
                blankToNull(value(columns, row, "imdb_id")),
                titleType(value(columns, row, "type")),
                integer(value(columns, row, "season")),
                integer(value(columns, row, "episode")),
                null,
                isoDate(value(columns, row, "watched_at")),
                decimal(value(columns, row, "rating")));
    }

    private static ImportRow imdb(Map<String, Integer> columns, List<String> row) {
        String constId = blankToNull(value(columns, row, "const"));
        String title = required(columns, row, "title");
        // "Date Rated" is when they rated it, not when they watched it. It is
        // the closest thing an IMDb export has, and the preview says so rather
        // than presenting it as a watch date the user gave us.
        LocalDate rated = isoDate(value(columns, row, "date rated"));
        return new ImportRow(
                constId != null ? "imdb:" + constId : contentRef("imdb", title, value(columns, row, "year")),
                title,
                integer(value(columns, row, "year")),
                constId,
                imdbTitleType(value(columns, row, "title type")),
                null,
                null,
                null,
                rated,
                decimal(value(columns, row, "your rating")));
    }

    private static ImportRow letterboxd(Map<String, Integer> columns, List<String> row) {
        String uri = blankToNull(value(columns, row, "letterboxd uri"));
        String title = required(columns, row, "name");
        // diary.csv has both Date (when the entry was made) and Watched Date;
        // watched.csv has only Date. The watch date wins where it exists.
        LocalDate watched = isoDate(value(columns, row, "watched date"));
        if (watched == null) {
            watched = isoDate(value(columns, row, "date"));
        }
        return new ImportRow(
                uri != null ? "letterboxd:" + uri
                        : contentRef("letterboxd", title, value(columns, row, "year"),
                                watched == null ? null : watched.toString()),
                title,
                integer(value(columns, row, "year")),
                null,
                // Letterboxd is films only, and saying so here is what keeps a
                // film called "Fargo" from matching the series.
                TitleType.MOVIE,
                null,
                null,
                null,
                watched,
                letterboxdRating(value(columns, row, "rating")));
    }

    private static ImportRow netflix(Map<String, Integer> columns, List<String> row) {
        String raw = required(columns, row, "title");
        LocalDate date = netflixDate(value(columns, row, "date"));

        // "Breaking Bad: Season 5: Ozymandias" -- series, season, episode name.
        // Netflix never gives an episode number, which is why matching one
        // means matching its name against the catalogue.
        String[] parts = raw.split(":");
        if (parts.length >= 3) {
            Integer season = seasonNumber(parts[1]);
            if (season != null) {
                String series = parts[0].trim();
                String episodeName = String.join(":", java.util.Arrays.copyOfRange(parts, 2, parts.length)).trim();
                return new ImportRow(
                        "netflix:" + raw + "@" + date,
                        series,
                        null,
                        null,
                        TitleType.TV_SERIES,
                        season,
                        null,
                        episodeName,
                        date,
                        null);
            }
        }
        return new ImportRow(
                "netflix:" + raw + "@" + date,
                raw.trim(),
                null,
                null,
                null,
                null,
                null,
                null,
                date,
                null);
    }

    /** "Season 5", "Staffel 5", "Season 5 - Part 2" -- the digits are the answer. */
    static Integer seasonNumber(String segment) {
        if (segment == null) {
            return null;
        }
        var matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(segment);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static Map<String, Integer> index(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.put(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return columns;
    }

    private static String value(Map<String, Integer> columns, List<String> row, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= row.size()) {
            return null;
        }
        String raw = row.get(index);
        return raw == null ? null : raw.trim();
    }

    private static String required(Map<String, Integer> columns, List<String> row, String name) {
        String value = blankToNull(value(columns, row, name));
        if (value == null) {
            throw new IllegalArgumentException("no " + name);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Integer integer(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Letterboxd rates out of five, in halves. Everything here is out of ten. */
    private static BigDecimal letterboxdRating(String value) {
        BigDecimal stars = decimal(value);
        return stars == null ? null : stars.multiply(BigDecimal.valueOf(2));
    }

    private static LocalDate isoDate(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate netflixDate(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed, NETFLIX_DATE);
        } catch (RuntimeException e) {
            return isoDate(trimmed);
        }
    }

    private static TitleType titleType(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        return switch (trimmed.toLowerCase(Locale.ROOT)) {
            case "movie", "film" -> TitleType.MOVIE;
            case "series", "tv", "tv series", "show" -> TitleType.TV_SERIES;
            default -> null;
        };
    }

    private static TitleType imdbTitleType(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("series") || lower.contains("mini")) {
            return TitleType.TV_SERIES;
        }
        if (lower.contains("movie") || lower.contains("video") || lower.contains("short")) {
            return TitleType.MOVIE;
        }
        // "TV Episode" lands here on purpose: an IMDb episode row names the
        // episode, not the series, so treating it as either type would match
        // the wrong thing. The matcher reports it unmatched instead.
        return null;
    }
}
