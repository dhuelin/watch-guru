package dev.huelin.watchguru.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.huelin.watchguru.api.models.UserResponse
import dev.huelin.watchguru.data.AccountIdentity
import dev.huelin.watchguru.data.Appearance
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huelin.watchguru.R
import dev.huelin.watchguru.data.Regions
import dev.huelin.watchguru.ui.components.ErrorView
import dev.huelin.watchguru.ui.components.UiState
import dev.huelin.watchguru.ui.signin.SignInViewModel

/**
 * The signed-in user's profile.
 *
 * Region is editable here, and this is the only place it can be changed. It is
 * not a cosmetic preference: it decides which streaming offers the title screen
 * shows, and every account starts on the server's default, which is the wrong
 * country for most people. Language and time zone are not shown: they are
 * not editable yet, and as raw codes ("en-US · UTC") they read as debugging
 * output rather than as settings.
 *
 * [signIn] is the Activity-scoped instance passed down from `WatchGuruApp`, not
 * one resolved here: signing out has to change the state the whole app is gated
 * on, which a per-destination instance would not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenServer: (String) -> Unit,
    onOpenTrakt: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMyServices: () -> Unit,
    signIn: SignInViewModel,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val accountError by signIn.accountError.collectAsStateWithLifecycle()
    val regionError by viewModel.regionError.collectAsStateWithLifecycle()
    val savingRegion by viewModel.savingRegion.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val wallpaperColours by viewModel.wallpaperColours.collectAsStateWithLifecycle()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var choosingRegion by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Connections change on the screens this one opens, so their state is
    // re-read on the way back rather than shown as it was before.
    DisposableEffect(lifecycleOwner) {
        var first = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (first) first = false else viewModel.loadConnections()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(accountError) {
        accountError?.let {
            snackbarHostState.showSnackbar(it)
            signIn.dismissAccountError()
        }
    }

    val regionErrorMessage = stringResource(R.string.error_region_update)
    LaunchedEffect(regionError) {
        if (regionError) {
            snackbarHostState.showSnackbar(regionErrorMessage)
            viewModel.dismissRegionError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_profile)) }) },
    ) { padding ->
        when (val state = profile) {
            is UiState.Content -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                AccountHeader(state.value)

                // Settings-screen grouping rather than a column of links: each
                // header names the question its rows answer, and Region sits
                // with My services because together they decide which offers
                // a title screen shows.
                GroupHeader(stringResource(R.string.profile_group_viewing))
                NavRow(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.action_view_history),
                    supporting = stringResource(R.string.profile_history_detail),
                    onClick = onOpenHistory,
                )
                NavRow(
                    icon = Icons.Outlined.BarChart,
                    title = stringResource(R.string.action_view_stats),
                    supporting = stringResource(R.string.profile_stats_detail),
                    onClick = onOpenStats,
                )

                GroupHeader(stringResource(R.string.where_to_watch))
                // The region is the one preference here that changes what the
                // app shows, so its value is the supporting line: the row says
                // which country it is set to without being opened.
                ListItem(
                    headlineContent = { Text(stringResource(R.string.region_label)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.profile_region_value,
                                Regions.displayName(state.value.region),
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Public, contentDescription = null) },
                    trailingContent = {
                        if (savingRegion) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                        }
                    },
                    modifier = Modifier.clickable(enabled = !savingRegion) { choosingRegion = true },
                )
                NavRow(
                    icon = Icons.Outlined.Subscriptions,
                    title = stringResource(R.string.action_my_services),
                    supporting = stringResource(R.string.profile_my_services_detail),
                    onClick = onOpenMyServices,
                )

                if (choosingRegion) {
                    RegionPickerDialog(
                        currentRegion = state.value.region,
                        onDismiss = { choosingRegion = false },
                        onPick = { code ->
                            choosingRegion = false
                            viewModel.setRegion(code)
                        },
                    )
                }

                // One row per server rather than a list behind another tap:
                // somebody who runs Jellyfin does not want to read about Plex.
                GroupHeader(stringResource(R.string.profile_group_connections))
                MEDIA_SERVERS.forEach { slug ->
                    NavRow(
                        icon = Icons.Outlined.Dns,
                        title = stringResource(serverName(slug)),
                        supporting = connectionLabel(connections[slug]),
                        onClick = { onOpenServer(slug) },
                    )
                }
                NavRow(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(R.string.title_trakt),
                    supporting = connectionLabel(connections[TRAKT]),
                    onClick = onOpenTrakt,
                )

                // Below the connections rather than above them: a live
                // connection is the better answer for anyone who has one, and
                // a file import is what is left for everybody else.
                GroupHeader(stringResource(R.string.profile_group_data))
                NavRow(
                    icon = Icons.Outlined.UploadFile,
                    title = stringResource(R.string.action_import_history),
                    supporting = stringResource(R.string.profile_import_detail),
                    onClick = onOpenImport,
                )

                // Its own row rather than a switch here: there is a global
                // setting and one per series, and a list of series does not
                // belong on the profile screen.
                GroupHeader(stringResource(R.string.title_notifications))
                NavRow(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.notifications_new_episodes),
                    supporting = stringResource(R.string.profile_notifications_detail),
                    onClick = onOpenNotifications,
                )

                AppearanceSection(
                    appearance = appearance,
                    onAppearance = viewModel::setAppearance,
                    wallpaperColours = wallpaperColours,
                    onWallpaperColours = viewModel::setWallpaperColours,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_sign_out)) },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = signIn::signOut),
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.action_delete_account),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    supportingContent = { Text(stringResource(R.string.delete_account_footer)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable { confirmingDelete = true },
                )

                Text(
                    text = stringResource(R.string.tmdb_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
                )
            }

            // Appearance stays reachable when the profile cannot load: it is
            // kept on the phone, and being offline is no reason to be stuck
            // with a theme.
            is UiState.Error -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ErrorView(
                    failure = state.failure,
                    onRetry = viewModel::load,
                    modifier = Modifier.weight(1f),
                )
                AppearanceSection(
                    appearance = appearance,
                    onAppearance = viewModel::setAppearance,
                    wallpaperColours = wallpaperColours,
                    onWallpaperColours = viewModel::setWallpaperColours,
                )
            }

            else -> Unit
        }
    }

    // Irreversible and cascading, so it asks first.
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = { Text(stringResource(R.string.delete_account_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        signIn.deleteAccount()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete_account),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Who is signed in.
 *
 * The address is shown only when it is one the person would recognise. A
 * synthetic or relay address is a long string of hex under the name, which
 * reads as a bug, so it is replaced by how they signed in -- on Android that
 * is always Google, the only sign-in this app offers.
 */
@Composable
private fun AccountHeader(user: UserResponse) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(user.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = if (AccountIdentity.isRelayAddress(user.email)) {
                stringResource(R.string.profile_signed_in_with_google)
            } else {
                user.email
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/**
 * Light, dark or the phone's setting, and on Android 12+ whether to take the
 * wallpaper's colours.
 *
 * Three segments rather than a row that opens a dialog: there are only three
 * answers, and the current one should show without a tap.
 */
@Composable
private fun AppearanceSection(
    appearance: Appearance,
    onAppearance: (Appearance) -> Unit,
    wallpaperColours: Boolean,
    onWallpaperColours: (Boolean) -> Unit,
) {
    GroupHeader(stringResource(R.string.profile_group_appearance))
    AppearanceChoice(
        selected = appearance,
        onSelect = onAppearance,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.appearance_wallpaper_colours)) },
            supportingContent = { Text(stringResource(R.string.appearance_wallpaper_colours_detail)) },
            trailingContent = {
                // The row toggles, so the switch itself takes no clicks:
                // two targets for one setting would read it twice to TalkBack.
                Switch(checked = wallpaperColours, onCheckedChange = null)
            },
            modifier = Modifier.toggleable(
                value = wallpaperColours,
                role = Role.Switch,
                onValueChange = onWallpaperColours,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceChoice(
    selected: Appearance,
    onSelect: (Appearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = Appearance.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(
                    stringResource(
                        when (option) {
                            Appearance.SYSTEM -> R.string.appearance_system
                            Appearance.LIGHT -> R.string.appearance_light
                            Appearance.DARK -> R.string.appearance_dark
                        },
                    ),
                )
            }
        }
    }
}

/** A row that opens another screen: icon, title, what it is for, chevron. */
@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    supporting: String?,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@StringRes
private fun serverName(slug: String): Int = when (slug) {
    "jellyfin" -> R.string.server_name_jellyfin
    "emby" -> R.string.server_name_emby
    else -> R.string.server_name_plex
}

/** Null while unknown, so a row never claims a state it has not read. */
@Composable
private fun connectionLabel(connected: Boolean?): String? = when (connected) {
    true -> stringResource(R.string.profile_connected)
    false -> stringResource(R.string.profile_not_connected)
    null -> null
}
