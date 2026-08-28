package com.dhuelin.dev.watchguru.tracking.domain;

/** Where a title sits in the user's personal pipeline. */
public enum WatchStatus {
    /** Queued but not started. */
    WATCHLIST,
    /** In progress — the normal state for a series mid-run. */
    WATCHING,
    COMPLETED,
    ON_HOLD,
    DROPPED
}
