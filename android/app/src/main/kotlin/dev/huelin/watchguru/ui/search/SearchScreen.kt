package dev.huelin.watchguru.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huelin.watchguru.R
import dev.huelin.watchguru.api.models.SearchHit
import dev.huelin.watchguru.api.models.WatchlistItemResponse
import dev.huelin.watchguru.ui.components.ErrorView
import dev.huelin.watchguru.ui.components.FullScreenMessage
import dev.huelin.watchguru.ui.components.Poster
import dev.huelin.watchguru.ui.components.UiState
import dev.huelin.watchguru.ui.components.contentOrNull
import dev.huelin.watchguru.ui.components.failureMessage
import dev.huelin.watchguru.ui.navigation.TitleId
import dev.huelin.watchguru.ui.theme.style

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenTitle: (TitleId) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val trending by viewModel.trending.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val added by viewModel.added.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    val opening by viewModel.opening.collectAsStateWithLifecycle()
    val actionFailure by viewModel.actionFailure.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collected here rather than handed down as state: each id is one
    // navigation, and the channel guarantees it happens exactly once.
    LaunchedEffect(viewModel) {
        viewModel.openTitle.collect { onOpenTitle(it) }
    }

    val failureText = actionFailure?.let { failureMessage(it) }
    LaunchedEffect(actionFailure) {
        if (failureText != null) {
            snackbarHostState.showSnackbar(failureText)
            viewModel.dismissActionFailure()
        }
    }

    // One list, two sources. Which one is showing is decided by the box rather
    // than by whichever finished loading last.
    val state = if (isSearching) results else trending

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = viewModel::onQueryChanged,
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        // Only once there is something to clear: an x in an
                        // empty box is a control that does nothing.
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = viewModel::clearQuery) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.action_clear_search),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                },
                expanded = false,
                onExpandedChange = {},
                // The screen's Scaffold has already cleared the status bar.
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {}

            val content = state.contentOrNull()
            when {
                content != null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (!isSearching) {
                        // Labelled, because an unlabelled list of films on a
                        // search screen reads as results for something the
                        // user did not type.
                        item(key = "trending-header") {
                            Text(
                                text = stringResource(R.string.trending_this_week),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(content, key = { it.providerId }) { hit ->
                        SearchResultRow(
                            hit = hit,
                            addedAs = added[hit.providerId],
                            adding = hit.providerId in adding,
                            opening = opening == hit.providerId,
                            onOpen = { viewModel.open(hit) },
                            onAdd = { viewModel.addToLibrary(hit) },
                        )
                    }
                }

                // Near the top rather than centred: with the keyboard up, the
                // middle of the screen is behind it.
                state is UiState.Loading -> Column(
                    modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }

                state is UiState.Error -> ErrorView(
                    failure = (state as UiState.Error).failure,
                    onRetry = viewModel::retry,
                    verticalArrangement = Arrangement.Top,
                )

                // Only reachable now when a search found nothing, or when the
                // trending shelf itself came back empty -- the "type
                // something" prompt was what the shelf replaced.
                isSearching -> FullScreenMessage(
                    icon = Icons.Outlined.SearchOff,
                    message = stringResource(R.string.empty_search, query),
                    verticalArrangement = Arrangement.Top,
                )

                else -> FullScreenMessage(
                    icon = Icons.Outlined.Search,
                    message = stringResource(R.string.search_prompt),
                    verticalArrangement = Arrangement.Top,
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    hit: SearchHit,
    addedAs: WatchlistItemResponse.Status?,
    adding: Boolean,
    opening: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !opening, onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Poster(url = hit.posterUrl, title = hit.title, modifier = Modifier.width(56.dp))
            // Over the poster, because opening a result now waits on an import
            // and a tap with no visible answer gets tapped again.
            if (opening) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = hit.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Read before buildString, not inside it: stringResource is a
            // composable and buildString's lambda is not one, so the call has
            // to happen out here.
            val type = stringResource(
                when (hit.titleType) {
                    SearchHit.TitleType.MOVIE -> R.string.title_type_film
                    SearchHit.TitleType.TV_SERIES -> R.string.title_type_series
                },
            )
            Text(
                text = buildString {
                    append(type)
                    hit.releaseDate?.let { append(" · ${it.year}") }
                    // A null rating is omitted rather than shown as 0.0: most
                    // titles genuinely have no rating yet.
                    hit.providerRating?.let { append(" · ${it.stripTrailingZeros().toPlainString()}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (addedAs != null) {
                AddedBadge(addedAs)
            }
        }

        AddControl(
            title = hit.title,
            added = addedAs != null,
            adding = adding,
            onAdd = onAdd,
        )
    }
}

/**
 * The status a result was added with, drawn the way every other status is.
 *
 * Icon and label together: a bare check mark said "something happened" but
 * not what, and the library uses the same pair, so the two screens agree.
 */
@Composable
private fun AddedBadge(status: WatchlistItemResponse.Status) {
    val style = status.style()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(style.label),
            style = MaterialTheme.typography.labelMedium,
            color = style.color,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * The visible way to add a result, 48dp like every other row control.
 *
 * Once added it stays in place as a check rather than vanishing, so the row
 * does not reflow under the user's thumb; the badge beside the title says
 * which status it went in with.
 */
@Composable
private fun AddControl(
    title: String,
    added: Boolean,
    adding: Boolean,
    onAdd: () -> Unit,
) {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when {
            adding -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            added -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.cd_in_library, title),
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> FilledTonalIconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.cd_add_title_to_library, title),
                )
            }
        }
    }
}
