package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.WatchEventResponse
import java.time.LocalDate
import java.time.ZoneId

/**
 * The history as days rather than as a list of rows.
 *
 * "What did I watch last October" is a question about days, and a flat list of
 * timestamps does not answer it. Here rather than in the screen because the
 * grouping is the part that can be wrong: a day boundary is local, and a
 * viewing at 00:30 belongs to the night before as far as the server is
 * concerned but to the new day as far as a calendar is. The calendar wins,
 * because that is what the user's own history screen elsewhere will agree with.
 *
 * Holds no Android types, so it runs on a plain JVM.
 */
object HistoryDays {

    /** One day's viewing, newest first within the day. */
    data class Day(val date: LocalDate, val events: List<WatchEventResponse>)

    /**
     * Groups events into days, preserving the order the server sent.
     *
     * The server orders by date and then id, which matters for the dozens of
     * events a "mark watched up to here" writes at the same instant: without a
     * tiebreak they would shuffle between refreshes, and an episode list that
     * reorders itself looks broken.
     */
    fun group(events: List<WatchEventResponse>, zone: ZoneId): List<Day> =
        events
            .groupBy { it.watchedAt.atZoneSameInstant(zone).toLocalDate() }
            .map { (date, sameDay) -> Day(date, sameDay) }
            .sortedByDescending { it.date }

    /**
     * How many separate viewings a day holds, said in one line.
     *
     * A count rather than a list of titles: the titles are directly underneath,
     * and repeating them in the header would be noise a screen reader has to
     * hear twice.
     */
    fun spokenSummary(day: Day): String =
        if (day.events.size == 1) "1 viewing" else "${day.events.size} viewings"
}
