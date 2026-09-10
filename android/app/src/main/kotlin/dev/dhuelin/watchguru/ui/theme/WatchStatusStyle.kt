package dev.dhuelin.watchguru.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.WatchlistItemResponse

/**
 * How each watch status is drawn.
 *
 * One place, because the status appears on every library row, every search
 * result already in the library, and every detail screen. Colour is never the
 * only signal -- each status carries an icon and a label too, for colour-blind
 * users and because a coloured dot with no legend is a puzzle.
 */
@Immutable
data class WatchStatusStyle(
    val color: Color,
    val icon: ImageVector,
    @StringRes val label: Int,
)

/**
 * WATCHING is the only status wearing the accent colour. A library where five
 * statuses compete for attention answers nothing; the question the screen
 * exists to answer is "what am I in the middle of".
 */
@Composable
fun WatchlistItemResponse.Status.style(): WatchStatusStyle = when (this) {
    WatchlistItemResponse.Status.WATCHLIST -> WatchStatusStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        icon = Icons.Filled.Bookmark,
        label = R.string.status_watchlist,
    )
    WatchlistItemResponse.Status.WATCHING -> WatchStatusStyle(
        color = MaterialTheme.colorScheme.primary,
        icon = Icons.Filled.PlayCircle,
        label = R.string.status_watching,
    )
    WatchlistItemResponse.Status.COMPLETED -> WatchStatusStyle(
        color = MaterialTheme.colorScheme.tertiary,
        icon = Icons.Filled.CheckCircle,
        label = R.string.status_completed,
    )
    WatchlistItemResponse.Status.ON_HOLD -> WatchStatusStyle(
        color = MaterialTheme.colorScheme.secondary,
        icon = Icons.Filled.PauseCircle,
        label = R.string.status_on_hold,
    )
    WatchlistItemResponse.Status.DROPPED -> WatchStatusStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        icon = Icons.Filled.Cancel,
        label = R.string.status_dropped,
    )
}
