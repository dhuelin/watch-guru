package dev.dhuelin.watchguru.ui.server

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
import dev.dhuelin.watchguru.api.models.MediaServerStatusResponse
import dev.dhuelin.watchguru.api.models.SyncRunResponse
import dev.dhuelin.watchguru.data.ConnectionActivity
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.components.contentOrNull

/**
 * Connecting a media server.
 *
 * One screen for Plex, Jellyfin and Emby. The setup is genuinely all the same:
 * the server calls us, so there is no login here and nothing to authorise. What
 * differs -- its name, where its webhook settings live, whether it needs a
 * username -- comes back from the server with the connection, so this screen
 * does not carry three sets of menu directions that would go stale.
 *
 * What it has to do well is the part after setup: saying whether deliveries are
 * arriving, and what happened to the last one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaServerScreen(
    onBack: () -> Unit,
    viewModel: MediaServerViewModel = hiltViewModel(),
) {
    val state by viewModel.status.collectAsStateWithLifecycle()
    val issuedUrl by viewModel.issuedUrl.collectAsStateWithLifecycle()
    val setUpHint by viewModel.setUpHint.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    val title = state.contentOrNull()?.serviceName
        ?: viewModel.service.replaceFirstChar { it.uppercase() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
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

            // Content or Refreshing: the figures stay on screen while the
            // next answer arrives. This view model never produces Empty --
            // "not connected" is a status, not an absence of one.
            else -> current.contentOrNull()?.let { status ->
                MediaServerContent(
                    status = status,
                    issuedUrl = issuedUrl,
                    setUpHint = setUpHint,
                    busy = busy,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onDismissUrl = viewModel::dismissIssuedUrl,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun MediaServerContent(
    status: MediaServerStatusResponse,
    issuedUrl: String?,
    setUpHint: String?,
    busy: Boolean,
    onConnect: (String?) -> Unit,
    onDisconnect: () -> Unit,
    onDismissUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var accountName by rememberSaveable(status.service) {
        mutableStateOf(status.account?.accountLabel.orEmpty())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.server_explainer, status.serviceName),
            style = MaterialTheme.typography.bodyMedium,
        )

        StatusLine(status = status, modifier = Modifier.padding(top = 16.dp))

        if (issuedUrl != null) {
            WebhookUrlCard(
                url = issuedUrl,
                hint = setUpHint,
                onDone = onDismissUrl,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        if (!status.connected) {
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                singleLine = true,
                isError = status.requiresAccountName && accountName.isBlank(),
                label = {
                    Text(
                        stringResource(
                            if (status.requiresAccountName) R.string.server_username_required_label
                            else R.string.server_username_optional_label,
                            status.serviceName,
                        ),
                    )
                },
                supportingText = {
                    Text(
                        stringResource(
                            // Jellyfin and Emby fire their webhook for everybody
                            // on the server, so the name is the only thing
                            // keeping a housemate's evening out of this library.
                            if (status.requiresAccountName) R.string.server_username_required_explainer
                            else R.string.server_username_optional_explainer,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Button(
                onClick = { onConnect(accountName) },
                enabled = !busy && !(status.requiresAccountName && accountName.isBlank()),
            ) {
                Text(
                    stringResource(
                        if (status.connected) R.string.action_server_reconnect
                        else R.string.action_server_connect,
                    ),
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(start = 12.dp).size(20.dp),
                )
            }
        }

        if (status.connected) {
            Text(
                text = stringResource(R.string.server_reconnect_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onDisconnect, enabled = !busy) {
                Text(
                    text = stringResource(R.string.action_server_disconnect),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        val runs = status.recentRuns
        if (runs.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = stringResource(R.string.server_recent_deliveries),
                style = MaterialTheme.typography.titleMedium,
            )
            runs.forEach { Delivery(it) }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(
            text = stringResource(R.string.server_other_services),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Whether deliveries are arriving, in one sentence and one timestamp. */
@Composable
private fun StatusLine(status: MediaServerStatusResponse, modifier: Modifier = Modifier) {
    val health = ConnectionActivity.health(status.connected, status.account)
    val lastSyncAt = status.account?.lastSyncAt

    Surface(
        color = when (health) {
            ConnectionActivity.Health.NEEDS_ATTENTION -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = ConnectionActivity.summary(
                    connected = status.connected,
                    account = status.account,
                    waiting = stringResource(R.string.server_waiting, status.serviceName),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (lastSyncAt != null) {
                Text(
                    text = stringResource(
                        R.string.server_last_heard,
                        relativeTime(lastSyncAt.toEpochSecond()),
                    ),
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
 * exists anywhere but in the server's settings, because we keep a hash of it
 * and nothing else. The instructions come from the server, so they name that
 * server's own menus rather than a generic "your webhook settings".
 */
@Composable
private fun WebhookUrlCard(
    url: String,
    hint: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.server_url_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = ConnectionActivity.describe(run),
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
