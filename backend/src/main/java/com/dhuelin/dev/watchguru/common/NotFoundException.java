package com.dhuelin.dev.watchguru.common;

/** Requested entity does not exist, or does not belong to the calling user. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String what, Object id) {
        return new NotFoundException(what + " " + id + " was not found");
    }
}
