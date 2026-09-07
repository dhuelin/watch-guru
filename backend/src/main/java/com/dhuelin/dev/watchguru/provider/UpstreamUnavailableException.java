package com.dhuelin.dev.watchguru.provider;

/**
 * The upstream was not called because it is already known to be failing.
 *
 * <p>Distinct from {@link MetadataProviderException} so callers can tell "the
 * provider answered badly" from "we did not ask" -- the second is the case
 * where serving stale local data is obviously right.
 */
public class UpstreamUnavailableException extends MetadataProviderException {

    public UpstreamUnavailableException(String message) {
        super(message);
    }
}
