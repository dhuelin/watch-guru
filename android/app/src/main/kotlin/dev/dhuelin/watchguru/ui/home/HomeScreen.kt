package dev.dhuelin.watchguru.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.UpNextResponse
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.FullScreenMessage
import dev.dhuelin.watchguru.ui.components.Poster
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.components.contentOrNull

/**
 * Up Next: what to put on now.
 *
 * One card per series in progress, most recently watched first, each with the
 * episode to play and a button that records it. The point is that logging an
 * episode never needs navigation — this is where most marking happens.
 */
@Composable
fun HomeScreen(
    onOpenTitle: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val marking by viewModel.marking.collectAsStateWithLifecycle()

    val content = upNext.contentOrNull()
    when {
        content != null -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(content, key = { it.titleId }) { entry ->
                UpNextCard(
                    entry = entry,
                    busy = entry.titleId in marking,
                    onOpen = { onOpenTitle(entry.titleId) },
                    onMarkWatched = { viewModel.markWatched(entry) },
                )
            }
        }

        upNext is UiState.Loading -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }

        upNext is UiState.Error -> ErrorView(
            failure = (upNext as UiState.Error).failure,
            onRetry = viewModel::refresh,
        )

        else -> FullScreenMessage(
            icon = Icons.Outlined.PlayCircle,
            message = stringResource(R.string.empty_up_next),
        )
    }
}

@Composable
private fun UpNextCard(
    entry: UpNextResponse,
    busy: Boolean,
    onOpen: () -> Unit,
    onMarkWatched: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(12.dp),
        ) {
            Poster(url = entry.posterUrl, title = entry.primaryTitle, modifier = Modifier.width(64.dp))

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = entry.primaryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(entry.nextEpisodeCode)
                        entry.nextEpisodeName?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (entry.airedEpisodes > 0) {
                    LinearProgressIndicator(
                        progress = { entry.watchedEpisodes.toFloat() / entry.airedEpisodes },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .semantics {
                                contentDescription = "${entry.watchedEpisodes} of " +
                                        "${entry.airedEpisodes} episodes watched"
                            },
                    )
                }

                FilledTonalButton(
                    onClick = onMarkWatched,
                    enabled = !busy,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics {
                            contentDescription = "Mark ${entry.nextEpisodeCode} of " +
                                    "${entry.primaryTitle} watched"
                        },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        text = stringResource(R.string.action_mark_watched),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
