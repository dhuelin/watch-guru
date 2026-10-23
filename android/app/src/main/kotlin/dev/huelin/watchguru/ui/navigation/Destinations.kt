package dev.huelin.watchguru.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import dev.huelin.watchguru.R

/**
 * The four tabs, in bar order.
 *
 * [route] names the tab's nested graph, and [rootRoute] the screen that graph
 * opens on. They are different on purpose: every screen reached from a tab
 * lives inside that tab's graph, so the bar can highlight the tab for any of
 * them by walking the destination's hierarchy, and reselecting a tab can pop
 * back to [rootRoute] without leaving the graph.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.tab_home, Icons.Outlined.Home),
    SEARCH("search", R.string.tab_search, Icons.Outlined.Search),
    LIBRARY("library", R.string.tab_library, Icons.Outlined.VideoLibrary),
    PROFILE("profile", R.string.tab_profile, Icons.Outlined.Person),
    ;

    val rootRoute: String get() = "$route/root"
}

/**
 * The backend's own id for a title, as opposed to the provider's (TMDB's).
 *
 * A type rather than a bare Long because the two used to be confused: search
 * results carry only a provider id, and passing it where a title id was
 * expected opened a 404 -- or, once ids collide, a different title entirely.
 * The only ways to make one are from a response that carries the internal id,
 * so a provider id cannot reach the detail route by accident.
 */
@JvmInline
value class TitleId(val value: Long)

/** Screens reached from within a tab rather than from the bar. */
object Routes {
    const val HISTORY = "history"
    const val STATS = "stats"
    /** One route for every media server; the slug says which. */
    const val MEDIA_SERVER = "server/{service}"
    const val TRAKT = "trakt"
    const val IMPORT = "import"
    const val NOTIFICATIONS = "notifications"
    const val MY_SERVICES = "my-services"

    /**
     * Title detail, once per tab that can open it.
     *
     * Prefixed with the tab so the same screen sits inside each tab's graph: a
     * title opened from Library stays under Library in the bar, and Back
     * returns to the list it came from.
     */
    fun titleDetailPattern(tab: TopLevelDestination) = "${tab.route}/title/{$ARG_TITLE_ID}"

    fun titleDetail(tab: TopLevelDestination, titleId: TitleId) = "${tab.route}/title/${titleId.value}"

    fun mediaServer(service: String) = "server/$service"

    const val ARG_TITLE_ID = "titleId"
    const val ARG_SERVICE = "service"
}
