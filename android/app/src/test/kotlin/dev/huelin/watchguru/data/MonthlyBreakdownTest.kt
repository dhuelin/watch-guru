package dev.huelin.watchguru.data

import dev.huelin.watchguru.api.models.MonthBucket
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
    fun `the summary reads every month in order, with its value in hours`() {
        val spoken = MonthlyBreakdown.spokenSummary(
            listOf(month(2026, 1, 120), month(2026, 2, 45)),
            english,
        )

        assertEquals(
            "Hours watched by month: Jan 2026, 2 hours, Feb 2026, 0.8 hours",
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

    @Test
    fun `months with nothing watched are filled in, so the axis is continuous`() {
        // The server leaves out empty months; July vanished between June and
        // August and the bars looked like a continuous run.
        val filled = MonthlyBreakdown.filled(listOf(month(2026, 8, 30), month(2026, 6, 90)))

        assertEquals(listOf(6, 7, 8), filled.map { it.month })
        assertEquals(listOf(90L, 0L, 30L), filled.map { it.minutes })
    }

    @Test
    fun `filling crosses the new year`() {
        val filled = MonthlyBreakdown.filled(listOf(month(2025, 11, 10), month(2026, 2, 10)))

        assertEquals(
            listOf(2025 to 11, 2025 to 12, 2026 to 1, 2026 to 2),
            filled.map { it.year to it.month },
        )
    }

    @Test
    fun `nothing to fill stays nothing`() {
        assertTrue(MonthlyBreakdown.filled(emptyList()).isEmpty())
    }

    @Test
    fun `axis labels are short month names, not single letters`() {
        assertEquals("Jun", MonthlyBreakdown.shortMonth(month(2026, 6, 0), english))
        assertEquals("Jul", MonthlyBreakdown.shortMonth(month(2026, 7, 0), english))
    }

    @Test
    fun `hours are whole when whole and one decimal otherwise`() {
        assertEquals("3", MonthlyBreakdown.hours(180, english))
        assertEquals("1.5", MonthlyBreakdown.hours(90, english))
        assertEquals("1,5", MonthlyBreakdown.hours(90, Locale.GERMAN))
    }
}
