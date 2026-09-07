package com.dhuelin.dev.watchguru.tracking.domain;

/**
 * How a tracking record came to exist.
 *
 * <p>Recorded so an imported history can be told apart from hand-entered data,
 * and so a re-run of a streaming-service import can skip what it already wrote.
 */
public enum WatchOrigin {
    MANUAL,
    /** Imported from a linked streaming account. */
    STREAMING_SYNC,
    /** Bulk import from a file or another tracker. */
    FILE_IMPORT
}
