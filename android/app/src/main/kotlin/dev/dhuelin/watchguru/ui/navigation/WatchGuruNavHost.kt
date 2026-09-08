package dev.dhuelin.watchguru.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.dhuelin.watchguru.ui.detail.TitleDetailScreen
import dev.dhuelin.watchguru.ui.home.HomeScreen
import dev.dhuelin.watchguru.ui.library.LibraryScreen
import dev.dhuelin.watchguru.ui.profile.ProfileScreen
import dev.dhuelin.watchguru.ui.search.SearchScreen

@Composable
fun WatchGuruNavHost(
    navController: NavHostController,
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
            ProfileScreen()
        }
        composable(
            route = Routes.TITLE_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_TITLE_ID) { type = NavType.LongType }),
        ) {
            TitleDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
