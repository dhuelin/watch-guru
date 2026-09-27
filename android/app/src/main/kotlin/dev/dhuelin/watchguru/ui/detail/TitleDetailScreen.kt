package dev.dhuelin.watchguru.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.EpisodeResponse
import dev.dhuelin.watchguru.api.models.SeasonsResponse
import dev.dhuelin.watchguru.api.models.TitleProgress
import dev.dhuelin.watchguru.api.models.TitleResponse
import dev.dhuelin.watchguru.api.models.UpdateWatchlistItem
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.Poster
import dev.dhuelin.watchguru.ui.components.UiState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleDetailScreen(
    onBack: () -> Unit,
    viewModel: TitleDetailViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val marking by viewModel.marking.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val expandedSeason by viewModel.expandedSeason.collectAsStateWithLifecycle()
    val filmLog by viewModel.filmLog.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((title as? UiState.Content)?.value?.primaryTitle.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val state = title) {
            is UiState.Loading, is UiState.Refreshing -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            is UiState.Error -> ErrorView(
                failure = state.failure,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            is UiState.Content -> TitleDetailContent(
                title = state.value,
                progress = progress,
                marking = marking,
                seasons = seasons,
                expandedSeason = expandedSeason,
                filmLog = filmLog,
                onLogFilm = viewModel::logFilmWatched,
                onDismissFilmLog = viewModel::clearFilmLog,
                onAddToLibrary = { viewModel.addToLibrary(state.value) },
                onStatus = viewModel::setStatus,
                onRating = viewModel::setRating,
                onRemoveFromLibrary = viewModel::removeFromLibrary,
                onMarkNext = viewModel::markNextEpisodeWatched,
                onToggleSeason = viewModel::toggleSeason,
                onToggleEpisode = { episode ->
                    viewModel.toggleEpisode(episode.id, episode.watched)
                },
                onMarkUpTo = { episode -> viewModel.markUpTo(episode.id) },
                modifier = Modifier.padding(padding),
            )

            UiState.Empty -> Unit
        }
    }
}

@Composable
private fun TitleDetailContent(
    title: TitleResponse,
    progress: TitleProgress?,
    marking: Boolean,
    seasons: SeasonsResponse?,
    expandedSeason: Int?,
    filmLog: TitleDetailViewModel.FilmLog?,
    onLogFilm: (LocalDate) -> Unit,
    onDismissFilmLog: () -> Unit,
    onAddToLibrary: () -> Unit,
    onStatus: (Long, UpdateWatchlistItem.Status) -> Unit,
    onRating: (Long, Int) -> Unit,
    onRemoveFromLibrary: (Long) -> Unit,
    onMarkNext: () -> Unit,
    onToggleSeason: (Int) -> Unit,
    onToggleEpisode: (EpisodeResponse) -> Unit,
    onMarkUpTo: (EpisodeResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row {
            Poster(
                url = title.posterUrl,
                title = title.primaryTitle,
                modifier = Modifier.heightIn(max = 180.dp),
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(title.primaryTitle, style = MaterialTheme.typography.headlineSmall)

                title.releaseDate?.let {
                    Text("${it.year}", style = MaterialTheme.typography.bodyMedium)
                }
                title.runtimeMinutes?.let {
                    Text(
                        text = stringResource(R.string.detail_runtime_minutes, it),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Two rating sources, always labelled so they cannot be
                // confused, and each omitted entirely when absent rather than
                // rendered as 0.0.
                title.providerRating?.let {
                    Text(
                        text = stringResource(R.string.detail_rating_tmdb, it.toPlainString()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                title.imdbRating?.let {
                    Text(
                        text = stringResource(R.string.detail_rating_imdb, it.toPlainString()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Before the tracking actions, because whether this is in the library
        // at all is the first thing somebody arriving from the trending shelf
        // needs to know.
        LibraryEntrySection(
            title = title,
            busy = marking,
            onAdd = onAddToLibrary,
            onStatus = onStatus,
            onRating = onRating,
            onRemove = onRemoveFromLibrary,
            modifier = Modifier.padding(top = 24.dp),
        )

        // A film is watched or it is not: there is no next episode to offer, so
        // this is the whole of its tracking, and without it a film could reach
        // the library but never the history.
        if (title.titleType == TitleResponse.TitleType.MOVIE) {
            LogFilmWatched(
                busy = marking,
                outcome = filmLog,
                onLog = onLogFilm,
                onDismissOutcome = onDismissFilmLog,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        if (progress != null && progress.airedEpisodes > 0) {
            EpisodeProgress(
                progress = progress,
                marking = marking,
                onMarkNext = onMarkNext,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        title.overview?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        // Above the episode list: for a film this is the only action the
        // screen can offer, and for a series it is what someone does before
        // they start rather than after.
        WhereToWatch(
            offers = title.availability,
            checkedAt = title.availabilityCheckedAt,
            modifier = Modifier.padding(top = 24.dp),
        )

        // The episode list. This is the screen's real content for a series --
        // everything above it is context.
        seasons?.seasons?.takeIf { it.isNotEmpty() }?.let { list ->
            Text(
                text = "Episodes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            list.forEach { season ->
                SeasonSection(
                    season = season,
                    expanded = expandedSeason == season.seasonNumber,
                    marking = marking,
                    onToggle = { onToggleSeason(season.seasonNumber) },
                    onToggleEpisode = onToggleEpisode,
                    onMarkUpTo = onMarkUpTo,
                )
            }
        }

        // Required by TMDB's terms of use wherever their data is shown.
        Text(
            text = stringResource(R.string.tmdb_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}

/**
 * Where the user is, and the single action that moves them forward.
 *
 * The whole product is judged on this button. It is full width and reachable
 * without scrolling on a detail screen, because people press it repeatedly,
 * on a sofa, one-handed.
 */
@Composable
private fun EpisodeProgress(
    progress: TitleProgress,
    marking: Boolean,
    onMarkNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "${progress.watchedEpisodes} of ${progress.airedEpisodes} episodes",
            style = MaterialTheme.typography.titleMedium,
        )

        LinearProgressIndicator(
            progress = { progress.percentComplete / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics {
                    contentDescription =
                        "${progress.watchedEpisodes} of ${progress.airedEpisodes} episodes watched"
                },
        )

        val nextCode = progress.nextEpisodeCode
        if (nextCode != null) {
            Text(
                text = buildString {
                    append("Next: ")
                    append(nextCode)
                    progress.nextEpisodeName?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )

            // The label names the episode, so TalkBack announces which one is
            // about to be marked rather than just "button". Hoisted because
            // the semantics lambda cannot call a composable.
            val markLabel = stringResource(R.string.cd_mark_episode_watched, nextCode)

            Button(
                onClick = onMarkNext,
                enabled = !marking,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .semantics { contentDescription = markLabel },
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    text = stringResource(R.string.action_mark_watched),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            // Caught up is a real state and deserves saying, not an empty gap.
            Text(
                text = "You're all caught up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
