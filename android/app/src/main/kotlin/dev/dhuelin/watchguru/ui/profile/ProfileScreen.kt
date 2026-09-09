package dev.dhuelin.watchguru.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.signin.SignInViewModel

/**
 * The signed-in user's profile.
 *
 * Region and language are shown because they are not cosmetic -- region decides
 * which streaming offers appear, and language is passed through to TMDB.
 *
 * [signIn] is the Activity-scoped instance passed down from `WatchGuruApp`, not
 * one resolved here: signing out has to change the state the whole app is gated
 * on, which a per-destination instance would not.
 */
@Composable
fun ProfileScreen(
    onOpenHistory: () -> Unit,
    signIn: SignInViewModel,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val accountError by signIn.accountError.collectAsStateWithLifecycle()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(accountError) {
        accountError?.let {
            snackbarHostState.showSnackbar(it)
            signIn.dismissAccountError()
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
                    text = "${state.value.region} · ${state.value.language} · ${state.value.timeZone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.action_view_history))
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
