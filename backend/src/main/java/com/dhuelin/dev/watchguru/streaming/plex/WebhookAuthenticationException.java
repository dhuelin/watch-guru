package com.dhuelin.dev.watchguru.streaming.plex;

/**
 * A webhook delivery whose token does not open anything.
 *
 * <p>One exception for every failure -- no such link, wrong secret, link
 * disconnected -- because a caller holding a token has no business learning
 * which of those it is.
 */
public class WebhookAuthenticationException extends RuntimeException {

    public WebhookAuthenticationException(String message) {
        super(message);
    }
}
