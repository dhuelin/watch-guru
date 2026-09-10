package com.dhuelin.dev.watchguru.notifications;

import com.dhuelin.dev.watchguru.notifications.service.QuietHours;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** When a push may be sent, in the user's own local time. */
class QuietHoursTest {

    private static final QuietHours DAYTIME = new QuietHours(LocalTime.of(9, 0), LocalTime.of(21, 0));

    @Test
    @DisplayName("inside the window, a push is allowed")
    void inside() {
        assertThat(DAYTIME.allows(LocalTime.of(9, 0))).isTrue();
        assertThat(DAYTIME.allows(LocalTime.of(13, 30))).isTrue();
        assertThat(DAYTIME.allows(LocalTime.of(20, 59))).isTrue();
    }

    @Test
    @DisplayName("the end of the window is exclusive, and 3am is never allowed")
    void outside() {
        assertThat(DAYTIME.allows(LocalTime.of(21, 0))).isFalse();
        assertThat(DAYTIME.allows(LocalTime.of(3, 0))).isFalse();
        assertThat(DAYTIME.allows(LocalTime.of(8, 59))).isFalse();
    }

    @Test
    @DisplayName("a window that wraps past midnight is not a window that never opens")
    void wrapsPastMidnight() {
        // The case a naive from <= t && t < until gets wrong: it answers "never"
        // for every time of day, which would silence the feature completely for
        // anyone who configured evening delivery.
        QuietHours evening = new QuietHours(LocalTime.of(21, 0), LocalTime.of(9, 0));

        assertThat(evening.allows(LocalTime.of(22, 0))).isTrue();
        assertThat(evening.allows(LocalTime.of(2, 0))).isTrue();
        assertThat(evening.allows(LocalTime.of(8, 59))).isTrue();
        assertThat(evening.allows(LocalTime.of(12, 0))).isFalse();
    }

    @Test
    @DisplayName("a zero-width window reads as all day, not as silence")
    void zeroWidth() {
        // Nobody configuring from == until means "never notify me"; the global
        // switch is how that is said, and it is one field away.
        QuietHours always = new QuietHours(LocalTime.of(9, 0), LocalTime.of(9, 0));

        assertThat(always.allows(LocalTime.of(3, 0))).isTrue();
        assertThat(always.allows(LocalTime.of(15, 0))).isTrue();
    }
}
