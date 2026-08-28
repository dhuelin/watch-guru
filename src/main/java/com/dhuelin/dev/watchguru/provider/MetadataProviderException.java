package com.dhuelin.dev.watchguru.provider;

/** Raised when the upstream metadata source fails or is not usable. */
public class MetadataProviderException extends RuntimeException {

    public MetadataProviderException(String message) {
        super(message);
    }

    public MetadataProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
