package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.MonthBucket
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * How the monthly chart is named and read aloud.
 *
 * Plain Kotlin rather than something inside the composable, because the
 * spoken summary is the whole chart as far as a screen-reader user is
 * concerned -- it is the one part of a drawing that can be tested, and the one
 * part that would otherwise be written once and never checked.
 */
object MonthlyBreakdown {

    /** "Mar 2026" -- short, and in the reader's own language. */
    fun label(bucket: MonthBucket, locale: Locale = Locale.getDefault()): String =
        "${Month.of(bucket.month).getDisplayName(TextStyle.SHORT, locale)} ${bucket.year}"

    /** The single letter under a bar. */
    fun initial(bucket: MonthBucket, locale: Locale = Locale.getDefault()): String =
        Month.of(bucket.month).getDisplayName(TextStyle.NARROW, locale)

    /**
     * Every value, in order, for a reader who cannot see the bars.
     *
     * A chart owes its readers a table; on a phone this sentence is it.
     */
    fun spokenSummary(months: List<MonthBucket>, locale: Locale = Locale.getDefault()): String {
        if (months.isEmpty()) {
            return "No viewing recorded in this period."
        }
        return months.joinToString(
            prefix = "Minutes watched by month: ",
            separator = ", ",
        ) { "${label(it, locale)}, ${it.minutes} minutes" }
    }

    /** The month with the most watched, or null when nothing was. */
    fun busiest(months: List<MonthBucket>): MonthBucket? =
        months.filter { it.minutes > 0 }.maxByOrNull { it.minutes }
}
