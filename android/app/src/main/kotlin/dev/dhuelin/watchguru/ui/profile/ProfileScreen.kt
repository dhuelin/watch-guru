package dev.dhuelin.watchguru.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.data.Regions
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.signin.SignInViewModel

/**
 * The signed-in user's profile.
 *
 * Region is editable here, and this is the only place it can be changed. It is
 * not a cosmetic preference: it decides which streaming offers the title screen
 * shows, and every account starts on the server's default, which is the wrong
 * country for most people. Language and time zone are shown but not yet
 * editable -- language is passed through to TMDB.
 *
 * [signIn] is the Activity-scoped instance passed down from `WatchGuruApp`, not
 * one resolved here: signing out has to change the state the whole app is gated
 * on, which a per-destination instance would not.
 */
@Composable
fun ProfileScreen(
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    signIn: SignInViewModel,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val accountError by signIn.accountError.collectAsStateWithLifecycle()
    val regionError by viewModel.regionError.collectAsStateWithLifecycle()
    val savingRegion by viewModel.savingRegion.collectAsStateWithLifecycle()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var choosingRegion by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(accountError) {
        accountError?.let {
            snackbarHostState.showSnackbar(it)
            signIn.dismissAccountError()
        }
    }

    val regionErrorMessage = stringResource(R.string.error_region_update)
    LaunchedEffect(regionError) {
        if (regionError) {
            snackbarHostState.showSnackbar(regionErrorMessage)
            viewModel.dismissRegionError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when (val state = profile) {
            is UiState.Content -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(state.value.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(state.value.email, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${state.value.language} · ${state.value.timeZone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                // The region is the one preference here that changes what the
                // app shows, so it is a control rather than a line of text.
                // Everything else on this screen is read-only for now.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !savingRegion) { choosingRegion = true }
                        .padding(vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.region_label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.region_explainer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (savingRegion) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            text = Regions.displayName(state.value.region),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (choosingRegion) {
                    RegionPickerDialog(
                        currentRegion = state.value.region,
                        onDismiss = { choosingRegion = false },
                        onPick = { code ->
                            choosingRegion = false
                            viewModel.setRegion(code)
                        },
                    )
                }
                TextButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.action_view_history))
                }
                TextButton(onClick = onOpenStats) {
                    Text(stringResource(R.string.action_view_stats))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                TextButton(onClick = signIn::signOut) {
                    Text(stringResource(R.string.action_sign_out))
                }
                TextButton(onClick = { confirmingDelete = true }) {
                    Text(
                        text = stringResource(R.string.action_delete_account),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(R.string.delete_account_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(R.string.tmdb_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }

            is UiState.Error -> ErrorView(
                failure = state.failure,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )

            else -> Unit
        }
    }

    // Irreversible and cascading, so it asks first.
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = { Text(stringResource(R.string.delete_account_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        signIn.deleteAccount()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete_account),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
