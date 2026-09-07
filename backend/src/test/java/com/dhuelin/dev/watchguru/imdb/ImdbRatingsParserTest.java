package com.dhuelin.dev.watchguru.imdb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parsing rules for IMDb's title.ratings.tsv.
 *
 * <p>This runs unattended over millions of rows from a third party, so the
 * behaviour that matters most is what happens to the bad ones: a single
 * malformed line must not abort a job that would otherwise have updated
 * everything else correctly.
 */
class ImdbRatingsParserTest {

    @Test
    @DisplayName("a well-formed line parses")
    void parsesAWellFormedLine() {
        ImdbRating rating = ImdbRatingsParser.parseLine("tt0903747\t9.5\t2143567");

        assertThat(rating).isNotNull();
        assertThat(rating.imdbId()).isEqualTo("tt0903747");
        assertThat(rating.rating()).isEqualByComparingTo(new BigDecimal("9.5"));
        assertThat(rating.voteCount()).isEqualTo(2143567);
    }

    @Test
    @DisplayName("the header line is recognised")
    void recognisesTheHeader() {
        assertThat(ImdbRatingsParser.isHeader("tconst\taverageRating\tnumVotes")).isTrue();
        assertThat(ImdbRatingsParser.isHeader("tt0903747\t9.5\t2143567")).isFalse();
    }

    @Test
    @DisplayName("IMDb's \\N null marker is skipped rather than parsed as text")
    void skipsNullMarkers() {
        assertThat(ImdbRatingsParser.parseLine("tt0903747\t\\N\t100")).isNull();
        assertThat(ImdbRatingsParser.parseLine("tt0903747\t9.5\t\\N")).isNull();
        assertThat(ImdbRatingsParser.parseLine("\\N\t9.5\t100")).isNull();
    }

    @Test
    @DisplayName("malformed lines are skipped, not thrown on")
    void skipsMalformedLines() {
        assertThat(ImdbRatingsParser.parseLine("")).isNull();
        assertThat(ImdbRatingsParser.parseLine(null)).isNull();
        assertThat(ImdbRatingsParser.parseLine("tt0903747")).isNull();
        assertThat(ImdbRatingsParser.parseLine("tt0903747\t9.5")).isNull();
        assertThat(ImdbRatingsParser.parseLine("tt0903747\tnot-a-number\t100")).isNull();
        assertThat(ImdbRatingsParser.parseLine("tt0903747\t9.5\tnot-a-number")).isNull();
    }

    @Test
    @DisplayName("an id that is not a tconst is skipped")
    void skipsNonTconstIds() {
        assertThat(ImdbRatingsParser.parseLine("nm0000123\t9.5\t100")).isNull();
        assertThat(ImdbRatingsParser.parseLine("garbage\t9.5\t100")).isNull();
    }

    @Test
    @DisplayName("an id too long for the column is skipped")
    void skipsOverlongIds() {
        // title.imdb_id is VARCHAR(16); letting a longer value through would
        // fail the insert and take the whole batch with it.
        assertThat(ImdbRatingsParser.parseLine("tt" + "9".repeat(20) + "\t9.5\t100")).isNull();
    }

    @Test
    @DisplayName("a rating outside the storable range is skipped")
    void skipsOutOfRangeRatings() {
        // NUMERIC(3,1) cannot hold these, and the database would reject the
        // batch rather than the row.
        assertThat(ImdbRatingsParser.parseLine("tt0903747\t11.0\t100")).isNull();
        assertThat(ImdbRatingsParser.parseLine("tt0903747\t-1.0\t100")).isNull();
    }

    @Test
    @DisplayName("a negative vote count is skipped")
    void skipsNegativeVotes() {
        assertThat(ImdbRatingsParser.parseLine("tt0903747\t9.5\t-5")).isNull();
    }

    @Test
    @DisplayName("ratings are scaled to one decimal place to match the column")
    void scalesRatingToOneDecimal() {
        ImdbRating rating = ImdbRatingsParser.parseLine("tt0903747\t9\t100");

        assertThat(rating).isNotNull();
        assertThat(rating.rating().scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("a stray extra column does not shift the ones that matter")
    void toleratesExtraColumns() {
        ImdbRating rating = ImdbRatingsParser.parseLine("tt0903747\t9.5\t2143567\tunexpected");

        assertThat(rating).isNotNull();
        assertThat(rating.voteCount()).isEqualTo(2143567);
    }

    @Test
    @DisplayName("a file with a header and bad rows yields only the good ones")
    void parsesAWholeFileSkippingBadRows() throws Exception {
        String file = """
                tconst\taverageRating\tnumVotes
                tt0903747\t9.5\t2143567
                tt0000000\t\\N\t5
                broken line with no tabs
                tt0944947\t8.7\t2298000
                tt9999999\t99.9\t1
                """;

        List<ImdbRating> ratings = ImdbRatingsParser.parseAll(new BufferedReader(new StringReader(file)));

        // Five data lines in, two usable out, and no exception on the way.
        assertThat(ratings).hasSize(2);
        assertThat(ratings).extracting(ImdbRating::imdbId)
                .containsExactly("tt0903747", "tt0944947");
    }

    @Test
    @DisplayName("a file with no header keeps its first line")
    void keepsFirstLineWhenThereIsNoHeader() throws Exception {
        String file = "tt0903747\t9.5\t2143567\ntt0944947\t8.7\t2298000\n";

        List<ImdbRating> ratings = ImdbRatingsParser.parseAll(new BufferedReader(new StringReader(file)));

        assertThat(ratings).hasSize(2);
    }
}
