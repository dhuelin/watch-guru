package dev.huelin.watchguru.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import dev.huelin.watchguru.ui.detail.TitleDetailScreen
import dev.huelin.watchguru.ui.history.HistoryScreen
import dev.huelin.watchguru.ui.imports.ImportScreen
import dev.huelin.watchguru.ui.stats.StatsScreen
import dev.huelin.watchguru.ui.home.HomeScreen
import dev.huelin.watchguru.ui.library.LibraryScreen
import dev.huelin.watchguru.ui.notifications.NotificationSettingsScreen
import dev.huelin.watchguru.ui.server.MediaServerScreen
import dev.huelin.watchguru.ui.profile.ProfileScreen
import dev.huelin.watchguru.ui.services.MyServicesScreen
import dev.huelin.watchguru.ui.trakt.TraktScreen
import dev.huelin.watchguru.ui.search.SearchScreen
import dev.huelin.watchguru.ui.signin.SignInViewModel

/**
 * One nested graph per tab.
 *
 * Every screen belongs to the tab it was reached from. Flat routes left title
 * detail outside every tab, so the bar highlighted nothing on it, and a tab's
 * saved state could hold a sub-screen that reselecting the tab returned to.
 */
@Composable
fun WatchGuruNavHost(
    navController: NavHostController,
    signIn: SignInViewModel,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier,
    ) {
        navigation(
            startDestination = TopLevelDestination.HOME.rootRoute,
            route = TopLevelDestination.HOME.route,
        ) {
            composable(TopLevelDestination.HOME.rootRoute) {
                HomeScreen(onOpenTitle = { navController.openTitle(TopLevelDestination.HOME, it) })
            }
            titleDetail(TopLevelDestination.HOME, navController)
        }

        navigation(
            startDestination = TopLevelDestination.SEARCH.rootRoute,
            route = TopLevelDestination.SEARCH.route,
        ) {
            composable(TopLevelDestination.SEARCH.rootRoute) {
                SearchScreen(onOpenTitle = { navController.openTitle(TopLevelDestination.SEARCH, it) })
            }
            titleDetail(TopLevelDestination.SEARCH, navController)
        }

        navigation(
            startDestination = TopLevelDestination.LIBRARY.rootRoute,
            route = TopLevelDestination.LIBRARY.route,
        ) {
            composable(TopLevelDestination.LIBRARY.rootRoute) {
                LibraryScreen(onOpenTitle = { navController.openTitle(TopLevelDestination.LIBRARY, it) })
            }
            titleDetail(TopLevelDestination.LIBRARY, navController)
        }

        navigation(
            startDestination = TopLevelDestination.PROFILE.rootRoute,
            route = TopLevelDestination.PROFILE.route,
        ) {
            profileGraph(navController, signIn)
        }
    }
}

private fun NavHostController.openTitle(tab: TopLevelDestination, titleId: TitleId) {
    navigate(Routes.titleDetail(tab, titleId))
}

private fun NavGraphBuilder.titleDetail(tab: TopLevelDestination, navController: NavHostController) {
    composable(
        route = Routes.titleDetailPattern(tab),
        arguments = listOf(navArgument(Routes.ARG_TITLE_ID) { type = NavType.LongType }),
    ) {
        TitleDetailScreen(onBack = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.profileGraph(navController: NavHostController, signIn: SignInViewModel) {
    composable(TopLevelDestination.PROFILE.rootRoute) {
        ProfileScreen(
            onOpenHistory = { navController.navigate(Routes.HISTORY) },
            onOpenStats = { navController.navigate(Routes.STATS) },
            onOpenServer = { navController.navigate(Routes.mediaServer(it)) },
            onOpenTrakt = { navController.navigate(Routes.TRAKT) },
            onOpenImport = { navController.navigate(Routes.IMPORT) },
            onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
            onOpenMyServices = { navController.navigate(Routes.MY_SERVICES) },
            signIn = signIn,
        )
    }
    composable(Routes.HISTORY) {
        HistoryScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.STATS) {
        StatsScreen(onBack = { navController.popBackStack() })
    }
    composable(
        route = Routes.MEDIA_SERVER,
        arguments = listOf(navArgument(Routes.ARG_SERVICE) { type = NavType.StringType }),
    ) {
        MediaServerScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.TRAKT) {
        TraktScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.IMPORT) {
        ImportScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.NOTIFICATIONS) {
        NotificationSettingsScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.MY_SERVICES) {
        MyServicesScreen(onBack = { navController.popBackStack() })
    }
}
