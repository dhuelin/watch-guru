package com.dhuelin.dev.watchguru.notifications.service;

import com.dhuelin.dev.watchguru.notifications.domain.DeviceToken;

/**
 * Hands a message to the platform's push service.
 *
 * <p>An interface with one real implementation to come per platform, because
 * APNs and FCM need credentials this project does not have yet and the rest of
 * the feature should not wait for them. What ships today is
 * {@link LoggingPushSender}, which delivers nothing and says so.
 */
public interface PushSender {

    Result send(DeviceToken device, PushMessage message);

    /**
     * What the push service said.
     *
     * <p>{@link #TOKEN_INVALID} is the one that changes state: the app has
     * been uninstalled, and the row must go, or every future scan wastes a
     * call on hardware that will never answer.
     */
    enum Result {
        DELIVERED,
        TEMPORARY_FAILURE,
        TOKEN_INVALID
    }
}
