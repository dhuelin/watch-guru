package dev.dhuelin.watchguru.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.data.ApiResult

/**
 * A state that fills the screen: empty, or failed.
 *
 * Marked as a live region so TalkBack announces it when it replaces a list --
 * otherwise a screen that silently becomes empty says nothing at all.
 */
@Composable
fun FullScreenMessage(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Renders a failure as something the user can act on.
 *
 * The distinction the backend works hard to preserve is honoured here: an
 * unreachable TMDB is not an outage, because the user's own library is still
 * current. Telling someone their data is gone when it is not is the worst
 * available answer.
 */
@Composable
fun ErrorView(
    failure: ApiResult.Failure,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val (icon, message) = when (failure) {
        is ApiResult.Failure.Offline ->
            Icons.Outlined.CloudOff to stringResource(R.string.error_offline)
        is ApiResult.Failure.Upstream ->
            Icons.Outlined.CloudOff to stringResource(R.string.error_provider_down)
        is ApiResult.Failure.NotFound ->
            Icons.Outlined.SearchOff to stringResource(R.string.error_generic)
        is ApiResult.Failure.Unauthorised,
        is ApiResult.Failure.Unexpected ->
            Icons.Outlined.ErrorOutline to stringResource(R.string.error_generic)
    }

    FullScreenMessage(
        icon = icon,
        message = message,
        modifier = modifier,
        actionLabel = onRetry?.let { stringResource(R.string.action_retry) },
        onAction = onRetry,
    )
}
