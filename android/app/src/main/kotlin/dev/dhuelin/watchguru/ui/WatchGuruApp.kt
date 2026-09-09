package dev.dhuelin.watchguru.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.dhuelin.watchguru.ui.navigation.TopLevelDestination
import dev.dhuelin.watchguru.ui.navigation.WatchGuruNavHost
import dev.dhuelin.watchguru.ui.signin.AuthState
import dev.dhuelin.watchguru.ui.signin.SignInScreen
import dev.dhuelin.watchguru.ui.signin.SignInViewModel

/**
 * The whole app, gated on having a token.
 *
 * [SignInViewModel] is resolved here rather than inside the nav graph so that
 * it is scoped to the Activity: the navigation host and the profile screen act
 * on one instance, not one per destination.
 */
@Composable
fun WatchGuruApp() {
    val signIn: SignInViewModel = hiltViewModel()
    val authState by signIn.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val state = authState) {
        // The stored-token check is synchronous, so this is a single frame at
        // most. It exists so the sign-in screen never flashes for a user who is
        // already signed in.
        AuthState.Checking -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        AuthState.SignedIn -> SignedInScaffold(signIn)

        else -> SignInScreen(
            state = state,
            // Credential Manager presents a bottom sheet, so it needs the
            // Activity rather than the application context.
            onSignIn = { signIn.signInWithGoogle(context.findActivity()) },
        )
    }
}

/**
 * The Activity behind a Compose [Context].
 *
 * `LocalContext` is not always the Activity itself -- Compose and Material
 * hand back a [ContextWrapper] in several situations -- so a direct cast is
 * not safe.
 */
private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Expected an Activity context, got ${this::class.java.name}")
}

@Composable
private fun SignedInScaffold(signIn: SignInViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Standard bottom-bar behaviour: tapping a tab
                                // returns to its root rather than growing the
                                // back stack, and re-tapping the current tab
                                // does not push a duplicate.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        WatchGuruNavHost(
            navController = navController,
            signIn = signIn,
            modifier = Modifier.padding(padding),
        )
    }
}
