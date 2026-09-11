package dev.dhuelin.watchguru.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dhuelin.watchguru.api.models.MonthBucket
import dev.dhuelin.watchguru.data.MonthlyBreakdown

/**
 * Minutes watched per calendar month.
 *
 * One hue for every bar, deliberately. Colouring bars darker-where-bigger
 * double-encodes the thing the bar length already says, and spends the only
 * free channel on information the reader has; months are a sequence, not
 * categories that need telling apart.
 *
 * No number above every bar either -- that is a wall of digits nobody reads.
 * The busiest month is labelled, the axis carries the rest, and the whole
 * chart is one accessibility node that reads out every value in order, which
 * is the screen-reader equivalent of the table view a chart owes its readers.
 */
@Composable
fun MonthlyChart(
    months: List<MonthBucket>,
    modifier: Modifier = Modifier,
) {
    if (months.isEmpty()) return

    val peak = months.maxOf { it.minutes }
    val barColour = MaterialTheme.colorScheme.primary
    val gridColour = MaterialTheme.colorScheme.outlineVariant
    val busiest = MonthlyBreakdown.busiest(months)

    Column(modifier = modifier.fillMaxWidth()) {
        if (busiest != null && peak > 0) {
            Text(
                text = "Busiest month: ${MonthlyBreakdown.label(busiest)} · ${busiest.minutes / 60} h",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = MonthlyBreakdown.spokenSummary(months) },
        ) {
            drawBaseline(gridColour)
            if (peak <= 0) return@Canvas

            // 2dp of surface between bars rather than a drawn border: the gap
            // separates them without adding a second thing to look at.
            val gap = 2.dp.toPx()
            val slot = size.width / months.size
            val barWidth = (slot - gap).coerceAtLeast(1f)
            val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

            months.forEachIndexed { index, bucket ->
                val fraction = bucket.minutes.toFloat() / peak
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

        MonthAxis(months)
    }
}

/** A hairline one shade off the surface; a heavy rule would outshout the data. */
private fun DrawScope.drawBaseline(colour: androidx.compose.ui.graphics.Color) {
    drawLine(
        color = colour,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1f,
    )
}

/**
 * First letters along the bottom, thinned out when they would collide.
 *
 * Twelve months fit on a phone; twenty-four do not, so every other one is
 * dropped rather than allowed to overlap into mush.
 */
@Composable
private fun MonthAxis(months: List<MonthBucket>) {
    val stride = if (months.size > 12) 2 else 1

    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        months.forEachIndexed { index, bucket ->
            Text(
                text = if (index % stride == 0) MonthlyBreakdown.initial(bucket) else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
