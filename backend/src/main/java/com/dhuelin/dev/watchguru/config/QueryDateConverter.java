package com.dhuelin.dev.watchguru.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.format.DateTimeFormatter;

/**
 * Reads a {@code LocalDate} query parameter that may arrive as a date or as an
 * instant.
 *
 * <p>The API asks for days, and Android sends days. The iOS client cannot: the
 * OpenAPI Swift generator maps {@code format: date} onto {@code Date}, which it
 * serialises as a full ISO-8601 timestamp, and there is no per-parameter hook to
 * change that. Rejecting the timestamp would mean either a date range that does
 * not work on iOS or hand-written requests beside the generated client.
 *
 * <p>An instant is read in <b>UTC</b>, which is what makes this unambiguous: the
 * iOS app sends the start of the chosen day in UTC, so the date it encoded is
 * the date read back, whatever zone either end is in. Reading it in the user's
 * zone instead would move the boundary by a day for anyone west of UTC --
 * silently, and only for some users, which is the worst kind of date bug.
 *
 * <p>This affects only how a bound is <em>spelled</em>. What a day means once
 * parsed is unchanged and still the user's own: {@code WatchHistoryService}
 * turns the last day into the instant that ends it in their zone.
 */
@Component
public class QueryDateConverter implements Converter<String, LocalDate> {

    @Override
    public LocalDate convert(@NonNull String source) {
        String value = source.trim();
        if (value.isEmpty()) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException notADate) {
            return dateOfInstant(value, notADate);
        }
    }

    private LocalDate dateOfInstant(String value, DateTimeParseException notADate) {
        try {
            // Parsed leniently over offset and zone: the generator emits
            // "...Z", but a client that sends "+02:00" means the same moment
            // and should not be told its request is malformed.
            TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(
                    value, java.time.OffsetDateTime::from, java.time.LocalDateTime::from);
            return parsed instanceof java.time.OffsetDateTime offset
                    ? offset.atZoneSameInstant(ZoneOffset.UTC).toLocalDate()
                    : ((java.time.LocalDateTime) parsed).toLocalDate();
        } catch (DateTimeParseException notAnInstantEither) {
            // Reported as the date failure, because a date is what the API
            // documents and what the message should tell the caller to send.
            throw notADate;
        }
    }
}
