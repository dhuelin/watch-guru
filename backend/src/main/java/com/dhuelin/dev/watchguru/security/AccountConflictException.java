package com.dhuelin.dev.watchguru.security;

/**
 * A sign-in carried an email address that already belongs to another account,
 * and nothing about the token permitted adopting it.
 *
 * <p>Surfaced as 409 rather than silently creating a second account, because a
 * duplicate account looks to the user like their entire watch history has
 * vanished.
 */
public class AccountConflictException extends RuntimeException {

    public AccountConflictException(String message) {
        super(message);
    }
}
