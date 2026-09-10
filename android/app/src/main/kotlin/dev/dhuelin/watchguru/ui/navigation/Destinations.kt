package dev.dhuelin.watchguru.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import dev.dhuelin.watchguru.R

/** The four tabs, in bar order. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.tab_home, Icons.Outlined.Home),
    SEARCH("search", R.string.tab_search, Icons.Outlined.Search),
    LIBRARY("library", R.string.tab_library, Icons.Outlined.VideoLibrary),
    PROFILE("profile", R.string.tab_profile, Icons.Outlined.Person),
}

/** Screens reached from within a tab rather than from the bar. */
object Routes {
    const val TITLE_DETAIL = "title/{titleId}"
    const val HISTORY = "history"

    fun titleDetail(titleId: Long) = "title/$titleId"

    const val ARG_TITLE_ID = "titleId"
}
