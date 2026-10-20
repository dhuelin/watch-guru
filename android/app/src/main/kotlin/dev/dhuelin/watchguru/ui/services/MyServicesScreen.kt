package dev.dhuelin.watchguru.ui.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import dev.dhuelin.watchguru.api.models.StreamingServiceResponse
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.UiState

/**
 * Which services the user pays for.
 *
 * A flat list of switches rather than a search field: there are a few dozen
 * services in a region, not a few thousand, and a list you can scan beats a
 * box you have to guess the spelling into.
 *
 * Nothing on this screen says it affects anything beyond ordering, because it
 * does not. It is deliberately not a filter: hiding titles the user cannot
 * stream today would hide the thing they were looking for the week before it
 * arrives on their service.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyServicesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyServicesViewModel = hiltViewModel(),
) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_my_services)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val current = all) {
            is UiState.Loading, is UiState.Refreshing -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            is UiState.Error -> ErrorView(
                failure = current.failure,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )

            is UiState.Empty -> Text(
                text = stringResource(R.string.my_services_none_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(16.dp),
            )

            is UiState.Content -> LazyColumn(modifier = Modifier.padding(padding)) {
                item {
                    Text(
                        text = stringResource(R.string.my_services_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(current.value, key = { it.id }) { service ->
                    ServiceRow(
                        service = service,
                        checked = service.id in selected,
                        onToggle = { viewModel.toggle(service.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceRow(
    service: StreamingServiceResponse,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    // No logo. The only artwork component here is Poster, which is fixed at a
    // 2:3 portrait ratio for exactly the thing it is named after; a service
    // logo is roughly square and would come out stretched. A name is what
    // somebody scans this list for anyway, and several seeded services carry
    // no logo at all.
    ListItem(
        headlineContent = { Text(service.name) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = service.name },
            )
        },
    )
}
