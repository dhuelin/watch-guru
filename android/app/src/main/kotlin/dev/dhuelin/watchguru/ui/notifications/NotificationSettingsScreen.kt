package dev.dhuelin.watchguru.ui.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.NotificationSettingsResponse
import dev.dhuelin.watchguru.api.models.SeriesNotificationResponse
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.FullScreenMessage
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.components.contentOrNull

/**
 * Whether to be told about new episodes, and of what.
 *
 * Three things have to be true before a notification arrives, and they fail
 * independently: Android has to permit them, the backend has to have a token
 * for this device, and the switches here have to be on. A screen that showed
 * only the switches would leave somebody toggling a setting that could never
 * do anything, so each of the other two gets said out loud when it is missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.settings.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var permitted by remember { mutableStateOf(notificationsPermitted(context)) }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permitted = granted
        // Granting is the moment the backend can usefully be told about this
        // device, rather than the next launch.
        if (granted) viewModel.registerDevice()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_notifications)) },
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

            else -> {
                val settings = current.contentOrNull()
                if (settings == null) {
                    FullScreenMessage(
                        icon = Icons.Outlined.NotificationsOff,
                        message = stringResource(R.string.notifications_unavailable),
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    Settings(
                        settings = settings,
                        busy = busy,
                        permitted = permitted,
                        onAskPermission = { request.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onSetEnabled = viewModel::setEnabled,
                        onSetSeries = viewModel::setSeries,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun Settings(
    settings: NotificationSettingsResponse,
    busy: Boolean,
    permitted: Boolean,
    onAskPermission: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetSeries: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SwitchRow(
            label = stringResource(R.string.notifications_new_episodes),
            checked = settings.enabled,
            enabled = !busy,
            onChange = onSetEnabled,
        )
        Text(
            text = stringResource(R.string.notifications_new_episodes_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Said only when it is actually the obstacle. A permission prompt on a
        // screen where notifications are switched off anyway is noise.
        if (settings.enabled && !permitted) {
            Text(
                text = stringResource(R.string.notifications_permission_needed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(onClick = onAskPermission, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.action_allow_notifications))
            }
        }

        // Permission granted and the switch on, but the server has nowhere to
        // send: today this is every install, because no FCM project exists yet
        // (#37). Saying so beats a screen that looks like it is working.
        if (settings.enabled && permitted && settings.registeredDevices == 0) {
            Text(
                text = stringResource(R.string.notifications_no_devices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Muted, not "all series". The server stores a row only when the user
        // has said no -- switching one back on deletes it -- so this list is
        // the exceptions, and every series without a row notifies by default.
        // Calling this "Series" would make an empty list read as "you have no
        // series", which is a different and untrue thing.
        Text(
            text = stringResource(R.string.notifications_series_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.notifications_series_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (settings.series.isEmpty()) {
            Text(
                text = stringResource(R.string.notifications_no_series),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            settings.series.forEach { series ->
                SeriesRow(
                    series = series,
                    // Greyed rather than hidden when the global switch is off:
                    // the per-series choices still exist and come back when it
                    // is turned on again.
                    enabled = !busy && settings.enabled,
                    onChange = { onSetSeries(series.titleId, it) },
                )
            }
        }
    }
}

@Composable
private fun SeriesRow(
    series: SeriesNotificationResponse,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SwitchRow(
        label = series.titleName,
        checked = series.newEpisodes,
        enabled = enabled,
        onChange = onChange,
    )
}

/**
 * A label and a switch.
 *
 * The row is not itself clickable: the switch carries its own label for
 * accessibility, and a row-wide tap target next to a switch produces two
 * controls that do the same thing and read as two in a screen reader.
 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/**
 * Whether this app may post notifications.
 *
 * Only Android 13 asks: below it the permission does not exist and is granted
 * by installing the app, so treating it as missing there would show a button
 * that cannot do anything.
 */
private fun notificationsPermitted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        // The framework's own method rather than ContextCompat: androidx.core
        // is not a declared dependency of this module, and reaching for a
        // transitive one is how a build breaks on an unrelated upgrade.
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
