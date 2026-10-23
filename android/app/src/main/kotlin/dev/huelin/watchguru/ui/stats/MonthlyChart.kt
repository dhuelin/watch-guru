package dev.huelin.watchguru.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.huelin.watchguru.R
import dev.huelin.watchguru.api.models.MonthBucket
import dev.huelin.watchguru.data.MonthlyBreakdown

/**
 * Hours watched per calendar month.
 *
 * One hue for every bar, deliberately. Colouring bars darker-where-bigger
 * double-encodes the thing the bar length already says, and spends the only
 * free channel on information the reader has; months are a sequence, not
 * categories that need telling apart.
 *
 * No number above every bar either -- that is a wall of digits nobody reads.
 * The busiest month is labelled, a single gridline gives the scale in hours,
 * and the whole chart is one accessibility node that reads out every value in
 * order, which is the screen-reader equivalent of the table view a chart owes
 * its readers.
 */
@Composable
fun MonthlyChart(
    months: List<MonthBucket>,
    modifier: Modifier = Modifier,
) {
    // Gaps filled before anything is measured, so an empty month takes its
    // slot on the axis instead of silently joining its neighbours.
    val filled = remember(months) { MonthlyBreakdown.filled(months) }
    if (filled.isEmpty()) return

    val peak = filled.maxOf { it.minutes }
    // The gridline sits at the next whole hour above the busiest month, so its
    // label is a round number and the tallest bar never touches the top.
    val scaleHours = ((peak + 59) / 60).coerceAtLeast(1)
    val scaleMinutes = scaleHours * 60f
    val barColour = MaterialTheme.colorScheme.primary
    val gridColour = MaterialTheme.colorScheme.outlineVariant
    val busiest = MonthlyBreakdown.busiest(filled)

    Column(modifier = modifier.fillMaxWidth()) {
        if (busiest != null && peak > 0) {
            Text(
                text = stringResource(
                    R.string.stats_busiest_month,
                    MonthlyBreakdown.label(busiest),
                    busiest.minutes / 60,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (peak > 0) {
            Text(
                text = stringResource(R.string.stats_hours, scaleHours),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // The scale is part of the drawing; the spoken summary below
                // already gives every value, so this is not read twice.
                modifier = Modifier.clearAndSetSemantics { },
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = MonthlyBreakdown.spokenSummary(filled) },
        ) {
            drawGridline(gridColour, y = 0f)
            drawGridline(gridColour, y = size.height)
            if (peak <= 0) return@Canvas

            // A visible gap between bars rather than a drawn border: it
            // separates them without adding a second thing to look at, and
            // bars that touch read as one block.
            val slot = size.width / filled.size
            val gap = (slot * 0.25f).coerceIn(2.dp.toPx(), 8.dp.toPx())
            val barWidth = (slot - gap).coerceAtLeast(1f)
            val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

            filled.forEachIndexed { index, bucket ->
                val fraction = bucket.minutes / scaleMinutes
                // Anchored to the baseline, so a short month reads as short
                // rather than as a floating block.
                val barHeight = (size.height - 1f) * fraction
                if (barHeight <= 0f) return@forEachIndexed

                drawRoundRect(
                    color = barColour,
                    topLeft = Offset(index * slot + gap / 2, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
        }

        MonthAxis(filled)
    }
}

/** A hairline one shade off the surface; a heavy rule would outshout the data. */
private fun DrawScope.drawGridline(colour: androidx.compose.ui.graphics.Color, y: Float) {
    drawLine(
        color = colour,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1f,
    )
}

/**
 * Short month names along the bottom, with the year under the first month and
 * under every January, so a run across New Year says where one year ends.
 *
 * Twelve short names fit on a phone; twenty-four do not, so every other one is
 * dropped rather than allowed to overlap into mush.
 */
@Composable
private fun MonthAxis(months: List<MonthBucket>) {
    val stride = if (months.size > 12) 2 else 1

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { }) {
        Row(modifier = Modifier.fillMaxWidth()) {
            months.forEachIndexed { index, bucket ->
                Text(
                    text = if (index % stride == 0) MonthlyBreakdown.shortMonth(bucket) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            months.forEachIndexed { index, bucket ->
                Text(
                    text = if (index == 0 || bucket.month == 1) bucket.year.toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
