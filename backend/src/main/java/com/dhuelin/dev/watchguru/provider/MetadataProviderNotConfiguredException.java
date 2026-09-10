package com.dhuelin.dev.watchguru.provider;

/**
 * The metadata provider cannot be used because this deployment is missing
 * configuration, such as the API token.
 *
 * <p>Distinct from a general {@link MetadataProviderException} because the fault
 * is local rather than upstream, so it maps to 503 rather than 502.
 */
public class MetadataProviderNotConfiguredException extends MetadataProviderException {

    public MetadataProviderNotConfiguredException(String message) {
        super(message);
    }
}
