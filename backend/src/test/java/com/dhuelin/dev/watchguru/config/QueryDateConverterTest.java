package com.dhuelin.dev.watchguru.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How a day arrives on the wire.
 *
 * <p>Two clients spell the same bound differently -- Android sends a date, iOS
 * sends an instant because its generated client has no other shape for one --
 * and both have to mean the same day. The instant is read in UTC so that it
 * does, for every user in every zone.
 */
class QueryDateConverterTest {

    private final QueryDateConverter converter = new QueryDateConverter();

    @Test
    @DisplayName("a plain date is the date")
    void plainDate() {
        assertThat(converter.convert("2026-09-13")).isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    @DisplayName("an instant is the day it names in UTC")
    void instantAtMidnight() {
        // Exactly what the iOS client sends for the 13th.
        assertThat(converter.convert("2026-09-13T00:00:00.000Z"))
                .isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    @DisplayName("an offset is honoured rather than truncated")
    void instantWithAnOffset() {
        // 00:30 on the 14th in Zurich is still the 13th in UTC. Reading the
        // text rather than the moment would silently move the bound a day for
        // anyone who sends a local offset.
        assertThat(converter.convert("2026-09-14T00:30:00+02:00"))
                .isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    @DisplayName("a timestamp without an offset is taken at face value")
    void localDateTime() {
        assertThat(converter.convert("2026-09-13T21:00:00"))
                .isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    @DisplayName("nonsense is refused as a bad date, not accepted as something else")
    void refusesNonsense() {
        assertThatThrownBy(() -> converter.convert("last tuesday"))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    @DisplayName("an empty parameter is no bound at all")
    void emptyIsNoBound() {
        // Spring hands through an empty string for `?from=`; a 400 there would
        // make clearing a filter an error.
        assertThat(converter.convert("")).isNull();
        assertThat(converter.convert("  ")).isNull();
    }
}
