package com.dhuelin.dev.watchguru.streaming.domain;

/** How a title is offered on a service, mirroring TMDB's watch-provider buckets. */
public enum OfferType {
    /** Included with a subscription. */
    FLATRATE,
    RENT,
    BUY,
    /** Free with advertising. */
    ADS,
    FREE
}
