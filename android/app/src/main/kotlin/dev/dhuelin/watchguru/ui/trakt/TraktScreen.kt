package dev.dhuelin.watchguru.ui.trakt

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.SyncResultResponse
import dev.dhuelin.watchguru.api.models.SyncRunResponse
import dev.dhuelin.watchguru.api.models.TraktStatusResponse
import dev.dhuelin.watchguru.data.ConnectionActivity
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.components.contentOrNull

/**
 * Connecting Trakt.
 *
 * The authorisation happens in a browser rather than a web view, so the user
 * can see the address bar says trakt.tv before typing a password there. That
 * also means this screen loses sight of them for a moment: it refreshes on
 * resume, because coming back to a screen still saying "not connected" after
 * connecting is the failure worth designing against.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraktScreen(
    onBack: () -> Unit,
    viewModel: TraktViewModel = hiltViewModel(),
) {
    val state by viewModel.status.collectAsStateWithLifecycle()
    val authorizeUrl by viewModel.authorizeUrl.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSync.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val noBrowser = stringResource(R.string.trakt_no_browser)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    LaunchedEffect(authorizeUrl) {
        val url = authorizeUrl ?: return@LaunchedEffect
        viewModel.authorizeUrlOpened()
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            // A device with no browser at all. Rare, and silently doing
            // nothing would look like the button is broken.
            snackbarHostState.showSnackbar(noBrowser)
        }
    }

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
                title = { Text(stringResource(R.string.title_trakt)) },
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

            else -> TraktContent(
                status = current.contentOrNull(),
                lastSync = lastSync,
                busy = busy,
                onConnect = viewModel::connect,
                onSync = viewModel::syncNow,
                onDisconnect = viewModel::disconnect,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun TraktContent(
    status: TraktStatusResponse?,
    lastSync: SyncResultResponse?,
    busy: Boolean,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = status?.connected == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.trakt_explainer),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (status != null) {
            StatusLine(status = status, modifier = Modifier.padding(top = 16.dp))
        }

        if (lastSync != null) {
            LastSync(result = lastSync, modifier = Modifier.padding(top = 12.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            if (connected) {
                Button(onClick = onSync, enabled = !busy) {
                    Text(stringResource(R.string.action_trakt_sync))
                }
            } else {
                Button(onClick = onConnect, enabled = !busy) {
                    Text(stringResource(R.string.action_trakt_connect))
                }
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
            TextButton(onClick = onDisconnect, enabled = !busy) {
                Text(
                    text = stringResource(R.string.action_trakt_disconnect),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        val runs = status?.recentRuns.orEmpty()
        if (runs.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = stringResource(R.string.trakt_recent_syncs),
                style = MaterialTheme.typography.titleMedium,
            )
            runs.forEach { SyncRun(it) }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(
            text = stringResource(R.string.trakt_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusLine(status: TraktStatusResponse, modifier: Modifier = Modifier) {
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
                    waiting = stringResource(R.string.trakt_waiting),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (lastSyncAt != null) {
                Text(
                    text = stringResource(
                        R.string.trakt_last_synced,
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
 * What the sync the user just asked for did.
 *
 * Named rather than counted where something could not be matched: "3 items
 * could not be matched" is not something a person can act on, and a title they
 * recognise is.
 */
@Composable
private fun LastSync(result: SyncResultResponse, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = pluralStringResource(
                R.plurals.trakt_sync_result,
                result.imported,
                result.imported,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (result.problems.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.trakt_sync_problems,
                    result.problems.joinToString(", "),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncRun(run: SyncRunResponse) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
