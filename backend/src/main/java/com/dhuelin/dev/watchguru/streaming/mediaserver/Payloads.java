package com.dhuelin.dev.watchguru.streaming.mediaserver;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reading fields out of somebody else's JSON, leniently.
 *
 * <p>A tree rather than a record per service, because two of the three payloads
 * are not fixed: Jellyfin's webhook plugin renders a template the server owner
 * can edit, and both it and Emby have changed field names between releases. A
 * missing field has to mean "not this kind of event" rather than a 500, and a
 * number arriving as {@code "3"} has to read as 3 -- Jellyfin's default
 * template quotes everything.
 */
final class Payloads {

    private Payloads() {
    }

    static JsonNode parse(JsonMapper json, String payload) {
        try {
            return json.readTree(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("That payload could not be read: " + e.getMessage());
        }
    }

    /** A string field, or null when absent, null-valued or blank. */
    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.isString() ? value.stringValue() : value.toString();
        return text.isBlank() ? null : text;
    }

    /** An integer field, whether it arrived as a number or as a quoted one. */
    static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isIntegralNumber()) {
            return value.intValue();
        }
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A boolean field, true only when it really says so.
     *
     * <p>Accepts {@code true} and the strings Jellyfin's template produces
     * ({@code "True"}, {@code "true"}). Anything else is false, which is the
     * safe direction: this decides whether something counts as watched.
     */
    static boolean bool(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        String text = text(node, field);
        return text != null && text.trim().equalsIgnoreCase("true");
    }
}
