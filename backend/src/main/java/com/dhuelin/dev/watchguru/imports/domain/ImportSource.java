package com.dhuelin.dev.watchguru.imports.domain;

/**
 * Which export a file came from.
 *
 * <p>Detected from the header row rather than asked of the user: everybody
 * knows where they downloaded a file from, nobody knows which of four radio
 * buttons matches its columns, and getting that choice wrong silently imports
 * the wrong column as a date.
 */
public enum ImportSource {
    /** The documented Watch Guru format; see docs/IMPORT.md. */
    WATCH_GURU,
    /** An IMDb list or ratings export. */
    IMDB,
    /** A Letterboxd diary or watched export. */
    LETTERBOXD,
    /** Netflix "Viewing activity", downloaded from the account page. */
    NETFLIX,
    /** Nothing recognised. */
    UNKNOWN
}
