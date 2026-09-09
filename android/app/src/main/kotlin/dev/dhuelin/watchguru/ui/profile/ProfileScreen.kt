package dev.dhuelin.watchguru.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.UiState

/**
 * The signed-in user's profile.
 *
 * Sign-in itself is issue #15: the app currently expects a token to be present
 * already, so every call returns 401 until that lands. Region and language are
 * shown because they are not cosmetic -- region decides which streaming offers
 * appear, and language is passed through to TMDB.
 */
@Composable
fun ProfileScreen(
    onOpenHistory: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    when (val state = profile) {
        is UiState.Content -> Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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

            Text(
                text = stringResource(R.string.tmdb_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
        }

        is UiState.Error -> ErrorView(failure = state.failure, onRetry = viewModel::load)

        else -> Unit
    }
}
