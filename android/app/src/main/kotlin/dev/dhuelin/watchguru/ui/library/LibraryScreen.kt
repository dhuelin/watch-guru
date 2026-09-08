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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

            // No progress bar here yet, deliberately. GET /api/v1/me/watchlist
            // does not return per-title progress, and the only way to get it
            // today is one /progress call per row -- an N+1 against the backend
            // for a screen that scrolls. Showing a bar hardcoded to zero would
            // be worse than showing none: it reads as "you have watched nothing"
            // for a series the user is halfway through.
            //
            // The fix belongs on the server: fold watchedEpisodes/airedEpisodes
            // into the watchlist response. Tracked with the same gap that issue
            // #13 (Up Next) describes.
            val episodes = item.title.numberOfEpisodes
            if (episodes != null && episodes > 0) {
                Text(
                    text = "$episodes episodes",
                    style = MaterialTheme.typography.bodySmall,
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
