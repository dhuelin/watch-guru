package dev.dhuelin.watchguru.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import dev.dhuelin.watchguru.api.models.WatchEventResponse
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.FullScreenMessage
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.components.contentOrNull
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val content = events.contentOrNull()
        when {
            content != null -> {
                // Grouped before emitting, not while emitting. A `var lastDay`
                // carried through the item lambdas would be wrong: lazy items
                // compose on demand and out of order, so headers would appear
                // and vanish as the list scrolled.
                val byDay = remember(content) {
                    content.groupBy {
                        it.watchedAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    byDay.forEach { (day, dayEvents) ->
                        // "What did I watch last October" is the question this
                        // screen exists to answer, so the day is the anchor.
                        stickyHeader(key = "day-$day") {
                            Text(
                                text = day.format(DayFormat),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(dayEvents, key = { it.id }) { event ->
                            HistoryRow(event = event, onDelete = { viewModel.delete(event) })
                        }
                    }
                }
            }

            events is UiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            events is UiState.Error -> ErrorView(
                failure = (events as UiState.Error).failure,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::refresh,
            )

            else -> FullScreenMessage(
                icon = Icons.Outlined.History,
                message = stringResource(R.string.empty_history),
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun HistoryRow(event: WatchEventResponse, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.primaryTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    event.episodeCode?.let { append(it) }
                    event.episodeName?.let { append(" · $it") }
                    // A rewatch is marked as such: the history is a record of
                    // viewings, not of first viewings.
                    if (event.rewatch) append(" · rewatch")
                    event.streamingServiceName?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete this entry for ${event.primaryTitle}",
            )
        }
    }
}
