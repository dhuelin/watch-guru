package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.MonthBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The parts of the monthly chart that can be checked.
 *
 * The spoken summary especially: it is the entire chart as far as a
 * screen-reader user is concerned, and nothing else in this codebase would
 * notice if it were wrong.
 */
class MonthlyBreakdownTest {

    private val english = Locale.ENGLISH

    private fun month(year: Int, month: Int, minutes: Long) =
        MonthBucket(year = year, month = month, viewings = 1, minutes = minutes)

    @Test
    fun `the summary reads every month in order, with its value`() {
        val spoken = MonthlyBreakdown.spokenSummary(
            listOf(month(2026, 1, 120), month(2026, 2, 45)),
            english,
        )

        assertEquals(
            "Minutes watched by month: Jan 2026, 120 minutes, Feb 2026, 45 minutes",
            spoken,
        )
    }

    @Test
    fun `an empty period says so rather than reading an empty list`() {
        assertTrue(MonthlyBreakdown.spokenSummary(emptyList(), english).contains("No viewing"))
    }

    @Test
    fun `the busiest month is the one with the most minutes`() {
        val busiest = MonthlyBreakdown.busiest(
            listOf(month(2026, 1, 120), month(2026, 2, 300), month(2026, 3, 45)),
        )

        assertEquals(2, busiest?.month)
    }

    @Test
    fun `a period where nothing was watched has no busiest month`() {
        // Otherwise the chart would announce a "busiest month" of zero hours,
        // which is a sentence about nothing.
        assertNull(MonthlyBreakdown.busiest(listOf(month(2026, 1, 0), month(2026, 2, 0))))
    }

    @Test
    fun `labels are month and year, so December and January do not blur together`() {
        assertEquals("Dec 2025", MonthlyBreakdown.label(month(2025, 12, 10), english))
        assertEquals("Jan 2026", MonthlyBreakdown.label(month(2026, 1, 10), english))
    }
}
