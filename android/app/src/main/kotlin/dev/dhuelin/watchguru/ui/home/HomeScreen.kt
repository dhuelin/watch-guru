package dev.dhuelin.watchguru.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.dhuelin.watchguru.ui.components.FullScreenMessage

/**
 * Up Next: the screen that answers "what do I put on now".
 *
 * Not built yet, and deliberately left as a placeholder rather than assembled
 * from the endpoints that exist. Doing it properly needs a single
 * GET /api/v1/me/up-next returning the next unwatched episode per in-progress
 * series; today that would be one /progress call per series, on the screen the
 * app opens to. The rule for what counts as "next" also belongs on the server,
 * where both apps share it, rather than being implemented twice.
 *
 * Tracked by issue #13, which proposes exactly that endpoint.
 */
@Composable
fun HomeScreen(
    @Suppress("UNUSED_PARAMETER") onOpenTitle: (Long) -> Unit,
) {
    FullScreenMessage(
        icon = Icons.Outlined.Home,
        message = "Up Next is coming soon. In the meantime, your library has everything you're tracking.",
        modifier = Modifier.fillMaxSize(),
    )
}
