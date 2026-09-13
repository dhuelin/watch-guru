package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.WatchEventResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Grouping the history into days.
 *
 * The interesting case is not the middle of an afternoon: it is the viewing
 * just after midnight, which belongs to a different day depending on whose
 * clock you ask. The user's own is the answer, because that is the calendar
 * they are reading the screen against.
 */
class HistoryDaysTest {

    private val zurich = ZoneId.of("Europe/Zurich")

    @Test
    fun `viewings on the same local day are one section`() {
        val days = HistoryDays.group(
            listOf(
                event(1, OffsetDateTime.of(2026, 9, 13, 21, 0, 0, 0, ZoneOffset.UTC)),
                event(2, OffsetDateTime.of(2026, 9, 13, 19, 0, 0, 0, ZoneOffset.UTC)),
            ),
            zurich,
        )

        assertEquals(1, days.size)
        assertEquals(LocalDate.of(2026, 9, 13), days.first().date)
        assertEquals(2, days.first().events.size)
    }

    @Test
    fun `a late viewing is filed under the day the user was living in`() {
        // 23:30 UTC on the 13th is 01:30 on the 14th in Zurich. The section
        // header has to say the 14th, because that is the date on the user's
        // own calendar, and a history that disagrees with it looks wrong.
        val days = HistoryDays.group(
            listOf(event(1, OffsetDateTime.of(2026, 9, 13, 23, 30, 0, 0, ZoneOffset.UTC))),
            zurich,
        )

        assertEquals(LocalDate.of(2026, 9, 14), days.first().date)
    }

    @Test
    fun `days come back newest first`() {
        val days = HistoryDays.group(
            listOf(
                event(1, OffsetDateTime.of(2026, 9, 10, 19, 0, 0, 0, ZoneOffset.UTC)),
                event(2, OffsetDateTime.of(2026, 9, 13, 19, 0, 0, 0, ZoneOffset.UTC)),
            ),
            zurich,
        )

        assertEquals(listOf(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 10)), days.map { it.date })
    }

    @Test
    fun `the order within a day is the order the server sent`() {
        // Dozens of events share one instant after a "mark watched up to here",
        // and the server breaks the tie by id. Re-sorting here would undo that
        // and make the list shuffle between refreshes.
        val sameInstant = OffsetDateTime.of(2026, 9, 13, 19, 0, 0, 0, ZoneOffset.UTC)
        val days = HistoryDays.group(
            listOf(event(9, sameInstant), event(3, sameInstant), event(7, sameInstant)),
            zurich,
        )

        assertEquals(listOf(9L, 3L, 7L), days.first().events.map { it.id })
    }

    @Test
    fun `nothing watched is no days`() {
        assertEquals(emptyList<HistoryDays.Day>(), HistoryDays.group(emptyList(), zurich))
    }

    @Test
    fun `a day says how much viewing it holds`() {
        val one = HistoryDays.Day(LocalDate.of(2026, 9, 13), listOf(event(1)))
        val several = HistoryDays.Day(LocalDate.of(2026, 9, 13), listOf(event(1), event(2)))

        assertEquals("1 viewing", HistoryDays.spokenSummary(one))
        assertEquals("2 viewings", HistoryDays.spokenSummary(several))
    }

    private fun event(
        id: Long,
        watchedAt: OffsetDateTime = OffsetDateTime.of(2026, 9, 13, 19, 0, 0, 0, ZoneOffset.UTC),
    ) = WatchEventResponse(
        id = id,
        primaryTitle = "Heat",
        rewatch = false,
        titleId = 1L,
        watchedAt = watchedAt,
    )
}
