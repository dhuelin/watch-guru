package dev.dhuelin.watchguru.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.EpisodeResponse
import dev.dhuelin.watchguru.api.models.SeasonResponse

/**
 * A season, collapsed to a header until opened.
 *
 * Long-running series have twenty seasons; rendering every episode of every one
 * at once is both slow and unreadable.
 */
@Composable
fun SeasonSection(
    season: SeasonResponse,
    expanded: Boolean,
    marking: Boolean,
    onToggle: () -> Unit,
    onToggleEpisode: (EpisodeResponse) -> Unit,
    onMarkUpTo: (EpisodeResponse) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (season.seasonNumber == 0) {
                        "Specials"
                    } else {
                        season.name ?: "Season ${season.seasonNumber}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    // Specials are shown but excluded from series progress, so
                    // saying so here avoids the counts looking wrong.
                    text = "${season.watchedEpisodes} of ${season.airedEpisodes} watched" +
                            if (season.seasonNumber == 0) " · not counted towards progress" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse season" else "Expand season",
            )
        }

        if (expanded) {
            season.episodes.forEach { episode ->
                EpisodeRow(
                    episode = episode,
                    marking = marking,
                    onToggle = { onToggleEpisode(episode) },
                    onMarkUpTo = { onMarkUpTo(episode) },
                )
            }
        }
        HorizontalDivider()
    }
}

/**
 * One episode.
 *
 * The watched control is the highest-traffic thing in the app. It is a
 * 48dp target because people press it repeatedly, on a sofa, one-handed, and
 * its accessibility label names the episode rather than announcing "button".
 */
@Composable
private fun EpisodeRow(
    episode: EpisodeResponse,
    marking: Boolean,
    onToggle: () -> Unit,
    onMarkUpTo: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggle,
            // An unaired episode cannot be watched, so the control is disabled
            // rather than offering something that would be rejected. A watched
            // one is now un-markable, which is the common correction: people
            // tick the row below the one they meant.
            enabled = episode.aired && !marking,
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = if (episode.watched) {
                        "Mark ${episode.code} unwatched"
                    } else {
                        "Mark ${episode.code} watched"
                    }
                },
        ) {
            Icon(
                imageVector = if (episode.watched) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (episode.watched) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                text = "${episode.code}${episode.name?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (episode.aired) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!episode.aired) {
                Text(
                    text = episode.airDate?.let { "Airs $it" } ?: "Not yet aired",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (episode.watchCount > 1) {
                Text(
                    text = "Watched ${episode.watchCount} times",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (episode.aired) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = "More actions for ${episode.code}",
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_mark_up_to_here)) },
                        onClick = {
                            menuOpen = false
                            onMarkUpTo()
                        },
                    )
                }
            }
        }
    }
}
