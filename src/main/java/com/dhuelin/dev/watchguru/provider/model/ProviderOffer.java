package com.dhuelin.dev.watchguru.provider.model;

import com.dhuelin.dev.watchguru.streaming.domain.OfferType;

/** One way to watch a title in a given region. */
public record ProviderOffer(
        Long serviceProviderId,
        String serviceName,
        String logoPath,
        OfferType offerType,
        String link
) {
}
