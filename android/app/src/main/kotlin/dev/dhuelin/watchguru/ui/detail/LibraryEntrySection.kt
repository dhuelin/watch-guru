package dev.dhuelin.watchguru.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.LibraryEntry
import dev.dhuelin.watchguru.api.models.TitleResponse
import dev.dhuelin.watchguru.api.models.UpdateWatchlistItem

/**
 * What the user has recorded about this title.
 *
 * The detail screen could not previously say whether a title was in the library
 * at all, let alone add it -- which became a dead end the moment the search tab
 * started showing people things they had not gone looking for.
 *
 * Rating lives here rather than on the history entry because it is an opinion
 * about the thing, not about one viewing of it: rewatching a film does not give
 * you a second opinion of it, it gives you the same one again.
 */
@Composable
fun LibraryEntrySection(
    title: TitleResponse,
    busy: Boolean,
    onAdd: () -> Unit,
    onStatus: (Long, UpdateWatchlistItem.Status) -> Unit,
    onRating: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.library_your_entry),
            style = MaterialTheme.typography.titleMedium,
        )

        val entry = title.library
        if (entry == null) {
            Text(
                text = stringResource(R.string.library_not_tracked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(onClick = onAdd, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.action_add_to_library))
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            Statuses.forEach { status ->
                FilterChip(
                    selected = entry.status.value == status.value,
                    onClick = { onStatus(entry.itemId, status) },
                    enabled = !busy,
                    label = { Text(stringResource(status.label())) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        RatingRow(
            entry = entry,
            busy = busy,
            onRating = onRating,
            modifier = Modifier.padding(top = 12.dp),
        )

        TextButton(
            onClick = { onRemove(entry.itemId) },
            enabled = !busy,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.action_remove_from_library))
        }
    }
}

/**
 * Out of ten, committed when the finger lifts.
 *
 * A slider rather than stars: the column has been 0 to 10 since the first
 * migration, and it is the scale the TMDB and IMDb figures on this same screen
 * are already on. Five stars would mean a third scale and a conversion nobody
 * asked for.
 *
 * The value is held locally while dragging and sent once at the end -- sending
 * on every change would be nine requests on the way from 1 to 10.
 */
@Composable
private fun RatingRow(
    entry: LibraryEntry,
    busy: Boolean,
    onRating: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember(entry.itemId, entry.rating) {
        mutableFloatStateOf(entry.rating?.toFloat() ?: 0f)
    }
    val shown = dragging.toInt()
    // Hoisted for the same reason as in EpisodeList: the semantics lambda
    // cannot call a composable.
    val ratingLabel = stringResource(R.string.cd_your_rating)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.library_your_rating),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = if (shown == 0) {
                    stringResource(R.string.library_not_rated)
                } else {
                    stringResource(R.string.library_rating_value, shown)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { if (shown > 0) onRating(entry.itemId, shown) },
            valueRange = 0f..10f,
            // Nine stops between the eleven whole numbers, so every landing
            // point is a value the backend stores rather than something it
            // rounds behind the user's back.
            steps = 9,
            enabled = !busy,
            modifier = Modifier.semantics {
                contentDescription = ratingLabel
            },
        )
    }
}

/** In the order somebody moves through them, not the order the enum declares. */
private val Statuses = listOf(
    UpdateWatchlistItem.Status.WATCHLIST,
    UpdateWatchlistItem.Status.WATCHING,
    UpdateWatchlistItem.Status.COMPLETED,
    UpdateWatchlistItem.Status.ON_HOLD,
    UpdateWatchlistItem.Status.DROPPED,
)

private fun UpdateWatchlistItem.Status.label() = when (this) {
    UpdateWatchlistItem.Status.WATCHLIST -> R.string.status_watchlist
    UpdateWatchlistItem.Status.WATCHING -> R.string.status_watching
    UpdateWatchlistItem.Status.COMPLETED -> R.string.status_completed
    UpdateWatchlistItem.Status.ON_HOLD -> R.string.status_on_hold
    UpdateWatchlistItem.Status.DROPPED -> R.string.status_dropped
}
