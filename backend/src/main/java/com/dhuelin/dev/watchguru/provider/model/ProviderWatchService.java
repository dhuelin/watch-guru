package com.dhuelin.dev.watchguru.provider.model;

/** An entry from the provider's catalogue of streaming services. */
public record ProviderWatchService(
        Long serviceProviderId,
        String name,
        String logoPath
) {
}
