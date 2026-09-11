package com.dhuelin.dev.watchguru.imports.domain;

/** What became of one row of an export when it met the catalogue. */
public enum MatchStatus {
    /** One title, confidently. Safe to import. */
    MATCHED,
    /** Several plausible titles. The user picks, or it is left out. */
    AMBIGUOUS,
    /** Nothing found. Reported, never silently dropped. */
    UNMATCHED
}
