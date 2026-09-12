package com.dhuelin.dev.watchguru.streaming.trakt;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

/**
 * Anything Trakt could not do for us.
 *
 * <p>One distinction is carried, because it is the only one that changes what
 * a user should be told: a 401 or 403 means the authorisation is no longer
 * good and they have to reconnect, while everything else means try later.
 */
public class TraktException extends RuntimeException {

    private final boolean authFailure;

    public TraktException(String message, boolean authFailure) {
        super(message);
        this.authFailure = authFailure;
    }

    public TraktException(String message, boolean authFailure, Throwable cause) {
        super(message, cause);
        this.authFailure = authFailure;
    }

    public boolean isAuthFailure() {
        return authFailure;
    }

    static TraktException from(String what, RestClientException cause) {
        if (cause instanceof HttpStatusCodeException status) {
            int code = status.getStatusCode().value();
            boolean auth = code == 401 || code == 403;
            return new TraktException(
                    "Trakt refused " + what + " with " + code, auth, cause);
        }
        return new TraktException("Trakt could not be reached while " + what, false, cause);
    }
}
