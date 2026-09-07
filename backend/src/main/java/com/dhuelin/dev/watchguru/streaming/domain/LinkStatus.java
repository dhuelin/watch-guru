package com.dhuelin.dev.watchguru.streaming.domain;

/** Lifecycle of a user's connection to a streaming account. */
public enum LinkStatus {
    /** Created, but the connection has not completed yet. */
    PENDING,
    CONNECTED,
    /** Connected previously but currently failing; credentials may need renewal. */
    ERROR,
    /** The service or user revoked our access. */
    REVOKED,
    DISCONNECTED
}
