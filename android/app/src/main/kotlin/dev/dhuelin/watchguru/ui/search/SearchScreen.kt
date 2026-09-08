package dev.dhuelin.watchguru.ui.search

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
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
import dev.dhuelin.watchguru.api.models.SearchHit
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.FullScreenMessage
import dev.dhuelin.watchguru.ui.components.Poster
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.components.contentOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenTitle: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val added by viewModel.added.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
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
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {}

        val content = results.contentOrNull()
        when {
            content != null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(content, key = { it.providerId }) { hit ->
                    SearchResultRow(
                        hit = hit,
                        alreadyAdded = hit.providerId in added,
                        onOpen = { onOpenTitle(hit.providerId) },
                        onAdd = { viewModel.addToLibrary(hit) },
                    )
                }
            }

            results is UiState.Loading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            results is UiState.Error -> ErrorView(
                failure = (results as UiState.Error).failure,
                onRetry = viewModel::retry,
            )

            // Empty covers two situations that need different words: nothing
            // typed yet, and nothing found.
            query.isBlank() -> FullScreenMessage(
                icon = Icons.Outlined.Search,
                message = stringResource(R.string.search_prompt),
            )

            else -> FullScreenMessage(
                icon = Icons.Outlined.SearchOff,
                message = stringResource(R.string.empty_search, query),
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    hit: SearchHit,
    alreadyAdded: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(url = hit.posterUrl, title = hit.title, modifier = Modifier.width(56.dp))

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
            Text(
                text = buildString {
                    append(
                        when (hit.titleType) {
                            SearchHit.TitleType.MOVIE -> "Film"
                            SearchHit.TitleType.TV_SERIES -> "Series"
                        },
                    )
                    hit.releaseDate?.let { append(" · ${it.year}") }
                    // A null rating is omitted rather than shown as 0.0: most
                    // titles genuinely have no rating yet.
                    hit.providerRating?.let { append(" · ${it.toPlainString()}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onAdd, enabled = !alreadyAdded) {
            Icon(
                imageVector = if (alreadyAdded) Icons.Filled.Check else Icons.Outlined.Add,
                contentDescription = if (alreadyAdded) null else stringResource(R.string.action_add_to_library),
            )
        }
    }
}
