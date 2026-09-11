package com.dhuelin.dev.watchguru.notifications.service;

/**
 * What a push says and where tapping it goes.
 *
 * @param title    the series name; the notification's bold line
 * @param body     what happened, in the user's terms
 * @param deepLink {@code watchguru://titles/{id}/episodes/{id}} -- the episode
 *                 to open, ready to mark watched. Neither app handles this
 *                 scheme yet; that is the app half of #19, and the payload
 *                 carries it now so the two halves can land independently
 * @param imageUrl the episode still, for a rich notification, or null
 */
public record PushMessage(String title, String body, String deepLink, String imageUrl) {
}
