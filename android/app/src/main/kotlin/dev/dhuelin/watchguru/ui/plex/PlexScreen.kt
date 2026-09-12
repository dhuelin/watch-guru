package dev.dhuelin.watchguru.ui.plex

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.PlexStatusResponse
import dev.dhuelin.watchguru.api.models.SyncRunResponse
import dev.dhuelin.watchguru.data.PlexActivity
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.components.contentOrNull

/**
 * Connecting a Plex server.
 *
 * The screen is mostly one instruction and one piece of text to copy, because
 * that is genuinely all the setup is: Plex calls us, so there is no login here
 * and nothing to authorise. What the screen has to do well is the part after
 * setup -- saying whether deliveries are arriving, and what happened to the
 * last one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlexScreen(
    onBack: () -> Unit,
    viewModel: PlexViewModel = hiltViewModel(),
) {
    val state by viewModel.status.collectAsStateWithLifecycle()
    val issuedUrl by viewModel.issuedUrl.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_plex)) },
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
        when (val current = state) {
            is UiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            is UiState.Error -> ErrorView(
                failure = current.failure,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )

            else -> PlexContent(
                status = current.contentOrNull(),
                issuedUrl = issuedUrl,
                busy = busy,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onDismissUrl = viewModel::dismissIssuedUrl,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun PlexContent(
    status: PlexStatusResponse?,
    issuedUrl: String?,
    busy: Boolean,
    onConnect: (String?) -> Unit,
    onDisconnect: () -> Unit,
    onDismissUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var plexUsername by rememberSaveable { mutableStateOf("") }
    val connected = status?.connected == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.plex_explainer),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (status != null) {
            StatusLine(status = status, modifier = Modifier.padding(top = 16.dp))
        }

        if (issuedUrl != null) {
            WebhookUrlCard(
                url = issuedUrl,
                onDone = onDismissUrl,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        if (!connected) {
            OutlinedTextField(
                value = plexUsername,
                onValueChange = { plexUsername = it },
                singleLine = true,
                label = { Text(stringResource(R.string.plex_username_label)) },
                supportingText = { Text(stringResource(R.string.plex_username_explainer)) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Button(onClick = { onConnect(plexUsername) }, enabled = !busy) {
                Text(
                    stringResource(
                        if (connected) R.string.action_plex_reconnect else R.string.action_plex_connect,
                    ),
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(20.dp),
                )
            }
        }
        if (connected) {
            Text(
                text = stringResource(R.string.plex_reconnect_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onDisconnect, enabled = !busy) {
                Text(
                    text = stringResource(R.string.action_plex_disconnect),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        val runs = status?.recentRuns.orEmpty()
        if (runs.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = stringResource(R.string.plex_recent_deliveries),
                style = MaterialTheme.typography.titleMedium,
            )
            runs.forEach { Delivery(it) }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(
            text = stringResource(R.string.plex_other_services),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Whether deliveries are arriving, in one sentence and one timestamp. */
@Composable
private fun StatusLine(status: PlexStatusResponse, modifier: Modifier = Modifier) {
    val health = PlexActivity.health(status)
    val lastSyncAt = status.account?.lastSyncAt

    Surface(
        color = when (health) {
            PlexActivity.Health.NEEDS_ATTENTION -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = PlexActivity.summary(status),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (lastSyncAt != null) {
                Text(
                    text = stringResource(R.string.plex_last_heard, relativeTime(lastSyncAt.toEpochSecond())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * The webhook URL, once.
 *
 * Shown with the warning rather than beside it: this is the only time the URL
 * exists anywhere but in Plex, because the server keeps a hash of it and
 * nothing else.
 */
@Composable
private fun WebhookUrlCard(url: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.plex_url_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.plex_url_instructions),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                    Text(stringResource(R.string.action_copy))
                }
                TextButton(onClick = onDone, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}

@Composable
private fun Delivery(run: SyncRunResponse) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = PlexActivity.describe(run),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = relativeTime(run.startedAt.toEpochSecond()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "2 hours ago", in the device's own language and format. */
private fun relativeTime(epochSeconds: Long): String = DateUtils.getRelativeTimeSpanString(
    epochSeconds * 1000L,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()
