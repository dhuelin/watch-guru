package dev.dhuelin.watchguru.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import dev.dhuelin.watchguru.api.apis.WatchlistControllerApi
import dev.dhuelin.watchguru.api.models.WatchlistItemResponse
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.FullScreenMessage
import dev.dhuelin.watchguru.ui.components.Poster
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.components.contentOrNull
import dev.dhuelin.watchguru.ui.theme.style

@Composable
fun LibraryScreen(
    onOpenTitle: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { viewModel.setFilter(null) },
                label = { Text("All") },
            )
            WatchlistControllerApi.StatusListWatchlist.entries.forEach { status ->
                FilterChip(
                    selected = filter == status,
                    onClick = { viewModel.setFilter(status) },
                    label = { Text(labelFor(status)) },
                )
            }
        }

        val content = items.contentOrNull()
        when {
            content != null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(content, key = { it.id }) { item ->
                    LibraryRow(item = item, onOpen = { onOpenTitle(item.title.id) })
                }
            }

            items is UiState.Loading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            items is UiState.Error -> ErrorView(
                failure = (items as UiState.Error).failure,
                onRetry = viewModel::refresh,
            )

            else -> FullScreenMessage(
                icon = Icons.Outlined.VideoLibrary,
                message = stringResource(R.string.empty_library),
            )
        }
    }
}

@Composable
private fun LibraryRow(item: WatchlistItemResponse, onOpen: () -> Unit) {
    val style = item.status.style()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(url = item.title.posterUrl, title = item.title.primaryTitle, modifier = Modifier.width(56.dp))

        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = item.title.primaryTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon and label alongside the colour: status is never conveyed
                // by colour alone.
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.color,
                    modifier = Modifier.width(16.dp),
                )
                Text(
                    text = stringResource(style.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = style.color,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // Progress now arrives with the list itself, gathered for the
            // whole page in one query, so this costs no extra request. It is
            // null for films, which have no episode progress -- a bar there
            // would be meaningless rather than merely empty.
            item.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.watchedEpisodes.toFloat() / progress.airedEpisodes.coerceAtLeast(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .semantics {
                            // Read as a sentence rather than a percentage a
                            // screen reader has to interpret.
                            contentDescription = "${progress.watchedEpisodes} of " +
                                    "${progress.airedEpisodes} episodes watched"
                        },
                )
                Text(
                    text = "${progress.watchedEpisodes} / ${progress.airedEpisodes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun labelFor(status: WatchlistControllerApi.StatusListWatchlist): String = when (status) {
    WatchlistControllerApi.StatusListWatchlist.WATCHLIST -> "Watchlist"
    WatchlistControllerApi.StatusListWatchlist.WATCHING -> "Watching"
    WatchlistControllerApi.StatusListWatchlist.COMPLETED -> "Completed"
    WatchlistControllerApi.StatusListWatchlist.ON_HOLD -> "On hold"
    WatchlistControllerApi.StatusListWatchlist.DROPPED -> "Dropped"
}
