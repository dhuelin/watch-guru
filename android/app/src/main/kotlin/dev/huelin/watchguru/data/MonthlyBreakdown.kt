package dev.huelin.watchguru.data

import dev.huelin.watchguru.api.models.MonthBucket
import java.time.Month
import java.time.YearMonth
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

    /**
     * The month under a bar: "Jun", not "J".
     *
     * A single letter could not tell January from June from July, and those
     * three sit next to each other often enough to matter.
     */
    fun shortMonth(bucket: MonthBucket, locale: Locale = Locale.getDefault()): String =
        Month.of(bucket.month).getDisplayName(TextStyle.SHORT, locale)

    /**
     * Every month from the first bucket to the last, with the empty ones as
     * zero.
     *
     * The server sends only months that have viewing in them, so a quiet July
     * simply vanished and the axis jumped from June to August -- a gap that
     * looks like a continuous run. Time is continuous, so the chart is too.
     * Sorted here as well, because the bars are read left to right as time.
     */
    fun filled(months: List<MonthBucket>): List<MonthBucket> {
        if (months.isEmpty()) return months
        val byMonth = months.associateBy { YearMonth.of(it.year, it.month) }
        val first = byMonth.keys.min()
        val last = byMonth.keys.max()
        return generateSequence(first) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(last) }
            .map { ym ->
                byMonth[ym] ?: MonthBucket(minutes = 0, month = ym.monthValue, viewings = 0, year = ym.year)
            }
            .toList()
    }

    /**
     * Hours for display: whole when whole, otherwise one decimal.
     *
     * Hours rather than minutes because that is the unit the rest of the
     * screen speaks -- the hero figure and the busiest month are both in
     * hours, and a chart in minutes beside them reads as a different number.
     */
    fun hours(minutes: Long, locale: Locale = Locale.getDefault()): String =
        if (minutes % 60 == 0L) {
            (minutes / 60).toString()
        } else {
            String.format(locale, "%.1f", minutes / 60.0)
        }

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
            prefix = "Hours watched by month: ",
            separator = ", ",
        ) { "${label(it, locale)}, ${hours(it.minutes, locale)} hours" }
    }

    /** The month with the most watched, or null when nothing was. */
    fun busiest(months: List<MonthBucket>): MonthBucket? =
        months.filter { it.minutes > 0 }.maxByOrNull { it.minutes }
}
