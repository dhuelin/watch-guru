package dev.dhuelin.watchguru.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.dhuelin.watchguru.ui.detail.TitleDetailScreen
import dev.dhuelin.watchguru.ui.history.HistoryScreen
import dev.dhuelin.watchguru.ui.stats.StatsScreen
import dev.dhuelin.watchguru.ui.home.HomeScreen
import dev.dhuelin.watchguru.ui.library.LibraryScreen
import dev.dhuelin.watchguru.ui.server.MediaServerScreen
import dev.dhuelin.watchguru.ui.profile.ProfileScreen
import dev.dhuelin.watchguru.ui.trakt.TraktScreen
import dev.dhuelin.watchguru.ui.search.SearchScreen
import dev.dhuelin.watchguru.ui.signin.SignInViewModel

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
        composable(TopLevelDestination.HOME.route) {
            HomeScreen(onOpenTitle = { navController.navigate(Routes.titleDetail(it)) })
        }
        composable(TopLevelDestination.SEARCH.route) {
            SearchScreen(onOpenTitle = { navController.navigate(Routes.titleDetail(it)) })
        }
        composable(TopLevelDestination.LIBRARY.route) {
            LibraryScreen(onOpenTitle = { navController.navigate(Routes.titleDetail(it)) })
        }
        composable(TopLevelDestination.PROFILE.route) {
            ProfileScreen(
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenStats = { navController.navigate(Routes.STATS) },
                onOpenServer = { navController.navigate(Routes.mediaServer(it)) },
                onOpenTrakt = { navController.navigate(Routes.TRAKT) },
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
        composable(
            route = Routes.TITLE_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_TITLE_ID) { type = NavType.LongType }),
        ) {
            TitleDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
