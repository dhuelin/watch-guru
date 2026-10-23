package dev.huelin.watchguru.ui.home

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huelin.watchguru.R
import dev.huelin.watchguru.api.models.UpNextResponse
import dev.huelin.watchguru.ui.components.ErrorView
import dev.huelin.watchguru.ui.components.FullScreenMessage
import dev.huelin.watchguru.ui.components.Poster
import dev.huelin.watchguru.ui.components.UiState
import dev.huelin.watchguru.ui.components.contentOrNull
import dev.huelin.watchguru.ui.components.failureMessage
import dev.huelin.watchguru.ui.navigation.TitleId

/**
 * Up Next: what to put on now.
 *
 * One card per series in progress, most recently watched first, each with the
 * episode to play and a button that records it. The point is that logging an
 * episode never needs navigation — this is where most marking happens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTitle: (TitleId) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val marking by viewModel.marking.collectAsStateWithLifecycle()
    val lastMarked by viewModel.lastMarked.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // ON_RESUME covers both ways back: from another screen in the app and
    // from another app. Either way something may have been marked meanwhile.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val markedText = lastMarked?.let {
        stringResource(R.string.snackbar_marked_watched, it.episodeCode, it.title)
    }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(lastMarked) {
        if (markedText == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = markedText,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoLastMark()
            SnackbarResult.Dismissed -> viewModel.dismissLastMark()
        }
    }

    val failureText = failure?.let { failureMessage(it) }
    LaunchedEffect(failure) {
        if (failureText != null) {
            snackbarHostState.showSnackbar(failureText)
            viewModel.dismissFailure()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Named for what it shows rather than for the tab: "Home" says
            // where you are, "Up Next" says what the list is.
            TopAppBar(title = { Text(stringResource(R.string.title_up_next)) })
        },
    ) { padding ->
        val content = upNext.contentOrNull()
        when {
            content != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(content, key = { it.titleId }) { entry ->
                    UpNextCard(
                        entry = entry,
                        busy = entry.titleId in marking,
                        onOpen = { onOpenTitle(TitleId(entry.titleId)) },
                        onMarkWatched = { viewModel.markWatched(entry) },
                    )
                }
            }

            upNext is UiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            upNext is UiState.Error -> ErrorView(
                failure = (upNext as UiState.Error).failure,
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(padding),
            )

            else -> FullScreenMessage(
                icon = Icons.Outlined.PlayCircle,
                message = stringResource(R.string.empty_up_next),
                modifier = Modifier.padding(padding),
            )
        }
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
                    val progressLabel = pluralStringResource(
                        R.plurals.cd_progress,
                        entry.airedEpisodes,
                        entry.watchedEpisodes,
                        entry.airedEpisodes,
                    )
                    LinearProgressIndicator(
                        progress = { entry.watchedEpisodes.toFloat() / entry.airedEpisodes },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .semantics { contentDescription = progressLabel },
                    )
                }

                val markLabel = stringResource(
                    R.string.cd_mark_next_episode,
                    entry.nextEpisodeCode,
                    entry.primaryTitle,
                )
                FilledTonalButton(
                    onClick = onMarkWatched,
                    enabled = !busy,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { contentDescription = markLabel },
                ) {
                    // A check, not a play arrow: the button records a viewing, and
                    // play would promise it starts one.
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Text(
                        text = stringResource(R.string.action_mark_watched),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
