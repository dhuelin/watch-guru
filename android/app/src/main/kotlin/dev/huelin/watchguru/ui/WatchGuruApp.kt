package dev.huelin.watchguru.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.huelin.watchguru.R
import dev.huelin.watchguru.ui.navigation.TopLevelDestination
import dev.huelin.watchguru.ui.navigation.WatchGuruNavHost
import dev.huelin.watchguru.ui.signin.AuthState
import dev.huelin.watchguru.ui.signin.SignInScreen
import dev.huelin.watchguru.ui.signin.SignInViewModel
import dev.huelin.watchguru.ui.sync.SyncViewModel

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
    val sync: SyncViewModel = hiltViewModel()
    val pending by sync.pending.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // On every return to the foreground, not on a timer: the moment worth
    // trying is when the user picks the phone up, which is also when the signal
    // is most likely to have come back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) sync.syncNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        // No insets of its own. Every screen inside draws its own top bar, and
        // that bar already pads for the status bar; when this outer Scaffold
        // padded for it too, every screen opened with about 100dp of blank
        // space above its title. The bottom is covered by the NavigationBar,
        // which pads for the gesture area itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    // Works for sub-screens too, because each one sits in its
                    // tab's nested graph: title detail opened from Library is
                    // under "library" in the hierarchy.
                    val selected =
                        currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) {
                                // Reselecting the tab you are on goes to its
                                // root. Restoring saved state here would return
                                // to whatever sub-screen was last open, which
                                // is the opposite of what the tap asks for.
                                navController.popBackStack(destination.rootRoute, inclusive = false)
                            } else {
                                navController.navigate(destination.route) {
                                    // Standard bottom-bar behaviour: switching
                                    // tabs keeps each tab's own stack, and does
                                    // not grow one long back stack across them.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                // Tells the inner Scaffolds the bottom bar has already taken
                // the bottom inset, so they do not pad for it a second time.
                .consumeWindowInsets(padding),
        ) {
            // Only when there is something to say. A permanent "synced" badge
            // is noise; "3 changes waiting" is information, and it is the
            // honest answer to "did my taps on the train count?".
            if (pending > 0) {
                PendingChangesBanner(count = pending)
            }
            WatchGuruNavHost(
                navController = navController,
                signIn = signIn,
                // With the banner on top it has already cleared the status bar,
                // so the screen below must not clear it again.
                modifier = if (pending > 0) {
                    Modifier.consumeWindowInsets(WindowInsets.statusBars)
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * "Some of what you did is not on the server yet."
 *
 * Deliberately not an error. Nothing has gone wrong: the changes are stored and
 * will be sent. It exists so a user who marked three episodes underground can
 * see that the app knows about them.
 */
@Composable
private fun PendingChangesBanner(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = pluralStringResource(R.plurals.pending_changes, count, count),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                // The banner is the top of the screen when it shows, so it is
                // the one that clears the status bar.
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
