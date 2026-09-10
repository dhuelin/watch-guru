package com.dhuelin.dev.watchguru.streaming.domain;

/** Outcome of one import run against a linked streaming account. */
public enum SyncStatus {
    RUNNING,
    SUCCESS,
    /** Completed, but some items could not be imported. */
    PARTIAL,
    FAILED
}
