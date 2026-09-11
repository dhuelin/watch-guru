package com.dhuelin.dev.watchguru.imports;

import com.dhuelin.dev.watchguru.imports.service.CsvReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The awkward parts of real export files.
 *
 * <p>Every case here comes from something a title actually contains. A split
 * on "," passes none of them and produces an import that looks fine until
 * somebody notices which rows are wrong.
 */
class CsvReaderTest {

    @Test
    @DisplayName("a comma inside a quoted title is part of the title")
    void quotedComma() {
        List<List<String>> rows = CsvReader.parse("Title,Year\n\"Dr. Strangelove, or: How I Learned\",1964\n");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsExactly("Dr. Strangelove, or: How I Learned", "1964");
    }

    @Test
    @DisplayName("a doubled quote is one quotation mark")
    void escapedQuote() {
        List<List<String>> rows = CsvReader.parse("Title\n\"The \"\"Burbs\"\n");

        assertThat(rows.get(1)).containsExactly("The \"Burbs");
    }

    @Test
    @DisplayName("a newline inside a quoted field does not end the row")
    void embeddedNewline() {
        List<List<String>> rows = CsvReader.parse("Title,Note\n\"A Film\",\"line one\nline two\"\n");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsExactly("A Film", "line one\nline two");
    }

    @Test
    @DisplayName("Windows line endings are line endings, not content")
    void crlf() {
        List<List<String>> rows = CsvReader.parse("Title,Year\r\nFargo,1996\r\n");

        assertThat(rows.get(1)).containsExactly("Fargo", "1996");
    }

    @Test
    @DisplayName("a byte-order mark does not become part of the first header")
    void byteOrderMark() {
        // Survives every export tool there is, and without this the first
        // header never matches, so the whole file reads as an unknown format.
        List<List<String>> rows = CsvReader.parse("﻿Title,Year\nFargo,1996\n");

        assertThat(rows.getFirst()).containsExactly("Title", "Year");
    }

    @Test
    @DisplayName("a last line with no trailing newline is still a row")
    void noTrailingNewline() {
        List<List<String>> rows = CsvReader.parse("Title,Year\nFargo,1996");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsExactly("Fargo", "1996");
    }

    @Test
    @DisplayName("empty fields are kept, so the columns after them still line up")
    void emptyFields() {
        List<List<String>> rows = CsvReader.parse("A,B,C\n1,,3\n");

        assertThat(rows.get(1)).containsExactly("1", "", "3");
    }

    @Test
    @DisplayName("blank lines are dropped rather than parsed as an empty row")
    void blankLines() {
        List<List<String>> rows = CsvReader.parse("Title\nFargo\n\n\nHeat\n");

        assertThat(rows).hasSize(3);
    }

    @Test
    @DisplayName("an empty file is no rows, not one empty row")
    void empty() {
        assertThat(CsvReader.parse("")).isEmpty();
    }
}
