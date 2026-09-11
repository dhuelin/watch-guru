package com.dhuelin.dev.watchguru.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Reads a date that arrives as a date, or as a date-time.
 *
 * <p>Not leniency for its own sake. The Swift client is generated from this
 * project's own OpenAPI document, and the generator maps every date to
 * {@code Date} and encodes all of them with an ISO-8601 date-time formatter --
 * so an import row dated {@code 2024-01-15} leaves the phone as
 * {@code 2024-01-15T00:00:00Z} and a strict {@code LocalDate} parser answers
 * 400. The Kotlin client sends a plain date. Both are correct clients of the
 * same document, so the server takes both rather than making one of them wrong.
 *
 * <p>The time is discarded, deliberately: it is an artefact of the encoding,
 * not something the user typed. Which day they meant is already the only
 * question a date-only field asks.
 */
public class LenientLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String text = parser.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException notADate) {
            try {
                return OffsetDateTime.parse(trimmed).toLocalDate();
            } catch (DateTimeParseException notADateTimeEither) {
                throw new IOException("Not a date: \"" + trimmed + "\"", notADateTimeEither);
            }
        }
    }
}
