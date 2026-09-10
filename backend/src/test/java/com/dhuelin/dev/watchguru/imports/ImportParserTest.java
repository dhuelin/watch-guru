package com.dhuelin.dev.watchguru.imports;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import com.dhuelin.dev.watchguru.imports.domain.ImportSource;
import com.dhuelin.dev.watchguru.imports.service.ImportParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading the four exports people actually have. */
class ImportParserTest {

    @Test
    @DisplayName("an IMDb export is recognised by its columns, not by being asked about")
    void imdbExport() {
        String csv = """
                Position,Const,Created,Description,Title,URL,Title Type,IMDb Rating,Year,Your Rating,Date Rated
                1,tt0903747,2021-03-04,,Breaking Bad,https://www.imdb.com/title/tt0903747/,TV Series,9.5,2008,10,2021-03-04
                """;

        ImportParser.Parsed parsed = ImportParser.parse(csv);

        assertThat(parsed.source()).isEqualTo(ImportSource.IMDB);
        ImportRow row = parsed.rows().getFirst();
        assertThat(row.imdbId()).isEqualTo("tt0903747");
        assertThat(row.titleText()).isEqualTo("Breaking Bad");
        assertThat(row.year()).isEqualTo(2008);
        assertThat(row.titleType()).isEqualTo(TitleType.TV_SERIES);
        assertThat(row.rating()).isEqualByComparingTo("10");
        assertThat(row.watchedAt()).isEqualTo(LocalDate.of(2021, 3, 4));
        // The id, not the line number: the same export re-uploaded must land
        // on the same origin_ref and write nothing the second time.
        assertThat(row.sourceRef()).isEqualTo("imdb:tt0903747");
    }

    @Test
    @DisplayName("an IMDb episode row is left untyped rather than guessed at")
    void imdbEpisodeRow() {
        // An episode row names the episode, not the series, so calling it
        // either a film or a series matches the wrong thing outright.
        String csv = """
                Const,Title,Title Type,Year,Your Rating,Date Rated
                tt2301451,Ozymandias,TV Episode,2013,10,2021-03-04
                """;

        assertThat(ImportParser.parse(csv).rows().getFirst().titleType()).isNull();
    }

    @Test
    @DisplayName("a Letterboxd diary keeps the watch date, not the date the entry was made")
    void letterboxdDiary() {
        String csv = """
                Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date
                2024-02-02,Fargo,1996,https://boxd.it/1a2b,4.5,No,,2024-01-30
                """;

        ImportRow row = ImportParser.parse(csv).rows().getFirst();

        assertThat(row.watchedAt()).isEqualTo(LocalDate.of(2024, 1, 30));
        // Five stars there, ten points here.
        assertThat(row.rating()).isEqualByComparingTo("9.0");
        // Letterboxd is films only; saying so keeps Fargo the film from
        // matching Fargo the series.
        assertThat(row.titleType()).isEqualTo(TitleType.MOVIE);
    }

    @Test
    @DisplayName("a Letterboxd watched export has only one date, and it is used")
    void letterboxdWatched() {
        String csv = """
                Date,Name,Year,Letterboxd URI
                2024-02-02,Heat,1995,https://boxd.it/9z8y
                """;

        assertThat(ImportParser.parse(csv).rows().getFirst().watchedAt())
                .isEqualTo(LocalDate.of(2024, 2, 2));
    }

    @Test
    @DisplayName("Netflix viewing activity splits into series, season and episode name")
    void netflixEpisode() {
        String csv = """
                Title,Date
                "Breaking Bad: Season 5: Ozymandias",3/4/21
                """;

        ImportParser.Parsed parsed = ImportParser.parse(csv);
        assertThat(parsed.source()).isEqualTo(ImportSource.NETFLIX);

        ImportRow row = parsed.rows().getFirst();
        assertThat(row.titleText()).isEqualTo("Breaking Bad");
        assertThat(row.seasonNumber()).isEqualTo(5);
        assertThat(row.episodeName()).isEqualTo("Ozymandias");
        // Netflix numbers nothing, so this stays null and the episode is
        // matched by name.
        assertThat(row.episodeNumber()).isNull();
        // US short dates: the fourth of March, not the third of April.
        assertThat(row.watchedAt()).isEqualTo(LocalDate.of(2021, 3, 4));
    }

    @Test
    @DisplayName("a Netflix episode name containing a colon survives the split")
    void netflixEpisodeNameWithColon() {
        String csv = """
                Title,Date
                "Star Trek: Season 1: Where No Man Has Gone Before: Part 2",1/2/22
                """;

        ImportRow row = ImportParser.parse(csv).rows().getFirst();

        assertThat(row.titleText()).isEqualTo("Star Trek");
        assertThat(row.episodeName()).isEqualTo("Where No Man Has Gone Before: Part 2");
    }

    @Test
    @DisplayName("a Netflix film row is a film, not a series with a strange name")
    void netflixFilm() {
        String csv = """
                Title,Date
                Heat,12/25/23
                """;

        ImportRow row = ImportParser.parse(csv).rows().getFirst();

        assertThat(row.titleText()).isEqualTo("Heat");
        assertThat(row.isEpisode()).isFalse();
        assertThat(row.watchedAt()).isEqualTo(LocalDate.of(2023, 12, 25));
    }

    @Test
    @DisplayName("a Netflix title with a colon but no season is not an episode")
    void netflixColonWithoutSeason() {
        String csv = """
                Title,Date
                "Dr. Strangelove: How I Learned to Stop Worrying: And Love the Bomb",5/6/21
                """;

        assertThat(ImportParser.parse(csv).rows().getFirst().isEpisode()).isFalse();
    }

    @Test
    @DisplayName("the documented Watch Guru format carries everything the others lose")
    void watchGuruFormat() {
        String csv = """
                title,year,type,imdb_id,season,episode,watched_at,rating
                Breaking Bad,2008,series,tt0903747,5,14,2024-05-01,9
                """;

        ImportParser.Parsed parsed = ImportParser.parse(csv);
        assertThat(parsed.source()).isEqualTo(ImportSource.WATCH_GURU);

        ImportRow row = parsed.rows().getFirst();
        assertThat(row.seasonNumber()).isEqualTo(5);
        assertThat(row.episodeNumber()).isEqualTo(14);
        assertThat(row.isEpisode()).isTrue();
        assertThat(row.rating()).isEqualByComparingTo(BigDecimal.valueOf(9));
    }

    @Test
    @DisplayName("the same row in two different files gets the same reference; different rows do not")
    void referencesComeFromContentNotLineNumber() {
        // Line numbers look stable inside one file and are not stable between
        // two. Referring to "row 2" would make the second export somebody
        // uploads collide with the first, and rows would vanish silently.
        String first = """
                title,year,watched_at
                Fargo,1996,2024-01-01
                Heat,1995,2024-01-02
                """;
        String second = """
                title,year,watched_at
                Heat,1995,2024-01-02
                """;

        List<ImportRow> firstRows = ImportParser.parse(first).rows();
        List<ImportRow> secondRows = ImportParser.parse(second).rows();

        assertThat(firstRows.get(1).sourceRef()).isEqualTo(secondRows.getFirst().sourceRef());
        assertThat(firstRows.getFirst().sourceRef()).isNotEqualTo(firstRows.get(1).sourceRef());
    }

    @Test
    @DisplayName("an unrecognised file says so, and says what it saw")
    void unknownFormat() {
        ImportParser.Parsed parsed = ImportParser.parse("Foo,Bar\n1,2\n");

        assertThat(parsed.source()).isEqualTo(ImportSource.UNKNOWN);
        assertThat(parsed.rows()).isEmpty();
        assertThat(parsed.problems()).singleElement().asString().contains("Foo, Bar");
    }

    @Test
    @DisplayName("one unreadable line is reported; the other four hundred still import")
    void badLineIsReportedNotFatal() {
        String csv = """
                title,year,watched_at,imdb_id
                Fargo,1996,2024-01-01,
                ,1997,2024-01-02,
                Heat,1995,2024-01-03,
                """;

        ImportParser.Parsed parsed = ImportParser.parse(csv);

        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.problems()).singleElement().asString().contains("Line 3");
    }

    @Test
    @DisplayName("an unparseable date is left empty rather than made up")
    void unparseableDate() {
        String csv = """
                title,watched_at
                Fargo,not-a-date
                """;

        assertThat(ImportParser.parse(csv).rows().getFirst().watchedAt()).isNull();
    }

    @Test
    @DisplayName("an empty file is not an error report about columns")
    void emptyFile() {
        ImportParser.Parsed parsed = ImportParser.parse("");

        assertThat(parsed.rows()).isEmpty();
        assertThat(parsed.problems()).singleElement().asString().contains("empty");
    }
}
