package com.dhuelin.dev.watchguru.imports.service;

import java.util.ArrayList;
import java.util.List;

/**
 * A small RFC 4180 reader.
 *
 * <p>Hand-written rather than a dependency, and the reason is the input: these
 * files come from Netflix, IMDb and Letterboxd, whose titles contain commas,
 * quotation marks and newlines as a matter of course. A split on "," produces
 * a plausible-looking import that is wrong in exactly the rows a user would
 * notice.
 *
 * <p>Handles quoted fields, doubled quotes inside them, embedded newlines, and
 * both line endings. It does not handle alternative delimiters, because none
 * of the four formats uses one.
 */
public final class CsvReader {

    private CsvReader() {
    }

    /** Splits a whole document into rows of fields. Blank lines are dropped. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldWasQuoted = false;

        // A byte-order mark survives every export tool and would otherwise
        // become part of the first header, so the first header never matches.
        int start = !text.isEmpty() && text.charAt(0) == '﻿' ? 1 : 0;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            switch (c) {
                case '"' -> {
                    quoted = true;
                    fieldWasQuoted = true;
                }
                case ',' -> {
                    row.add(field.toString());
                    field.setLength(0);
                    fieldWasQuoted = false;
                }
                case '\r' -> {
                    // Swallowed; the \n that follows ends the row. A lone \r
                    // as a line ending has not been seen since Mac OS 9.
                }
                case '\n' -> {
                    row.add(field.toString());
                    field.setLength(0);
                    if (!isBlank(row)) {
                        rows.add(row);
                    }
                    row = new ArrayList<>();
                    fieldWasQuoted = false;
                }
                default -> field.append(c);
            }
        }

        if (field.length() > 0 || fieldWasQuoted || !row.isEmpty()) {
            row.add(field.toString());
            if (!isBlank(row)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static boolean isBlank(List<String> row) {
        return row.stream().allMatch(f -> f == null || f.isBlank());
    }
}
