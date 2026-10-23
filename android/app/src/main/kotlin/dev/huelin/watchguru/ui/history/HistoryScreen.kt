package dev.huelin.watchguru.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huelin.watchguru.R
import dev.huelin.watchguru.api.models.WatchEventResponse
import dev.huelin.watchguru.data.HistoryDays
import dev.huelin.watchguru.ui.components.ErrorView
import dev.huelin.watchguru.ui.components.FullScreenMessage
import dev.huelin.watchguru.ui.components.SkeletonList
import dev.huelin.watchguru.ui.components.UiState
import dev.huelin.watchguru.ui.components.contentOrNull
import dev.huelin.watchguru.ui.components.failureMessage
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Localised rather than patterned. ofPattern("d MMM yyyy") takes its month
// NAME from the default locale but keeps English field order and punctuation,
// so a German device read "12 Okt. 2026" where German writes "12. Okt. 2026".
// ofLocalizedDate asks the locale for the whole arrangement, which is also
// what the iOS app has always done -- there it is .formatted(date:
// .abbreviated), and the two apps were rendering the same date differently.
private val DayFormat: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val serviceId by viewModel.serviceId.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val services by viewModel.services.collectAsStateWithLifecycle()
    // The id rather than the event: an id survives rotation without a custom
    // saver, and the event it names is in the list on screen anyway.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val deleteFailure by viewModel.deleteFailure.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val deletedText = pendingDelete?.let { stringResource(R.string.snackbar_entry_deleted, it.primaryTitle) }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(pendingDelete) {
        if (deletedText == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedText,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoDelete()
            SnackbarResult.Dismissed -> viewModel.commitPendingDelete()
        }
    }

    val failureText = deleteFailure?.let { failureMessage(it) }
    LaunchedEffect(deleteFailure) {
        if (failureText != null) {
            snackbarHostState.showSnackbar(failureText)
            viewModel.dismissDeleteFailure()
        }
    }

    // Leaving the screen ends the chance to undo, so the held-back delete goes
    // now rather than never. (A rotation sends it a little early too, which is
    // harmless: the user had already asked for it.)
    DisposableEffect(viewModel) {
        onDispose { viewModel.commitPendingDelete() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val content = events.contentOrNull()
        when {
            content != null -> {
                // Grouped before emitting, not while emitting. A `var lastDay`
                // carried through the item lambdas would be wrong: lazy items
                // compose on demand and out of order, so headers would appear
                // and vanish as the list scrolled.
                val days = remember(content) {
                    HistoryDays.group(content, ZoneId.systemDefault())
                }

                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    item(key = "filters") {
                        Filters(
                            query = query,
                            type = type,
                            serviceId = serviceId,
                            range = range,
                            services = services,
                            onQuery = viewModel::setQuery,
                            onType = viewModel::setType,
                            onService = viewModel::setService,
                            onRange = viewModel::setRange,
                        )
                    }
                    days.forEach { day ->
                        // "What did I watch last October" is the question this
                        // screen exists to answer, so the day is the anchor.
                        stickyHeader(key = "day-${day.date}") {
                            Text(
                                text = day.date.format(DayFormat),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                                    .semantics {
                                        contentDescription = day.date.format(DayFormat) +
                                            ", " + HistoryDays.spokenSummary(day)
                                    },
                            )
                        }
                        items(day.events, key = { it.id }) { event ->
                            HistoryRow(
                                event = event,
                                onEdit = { editingId = event.id },
                                onDelete = { viewModel.delete(event) },
                            )
                        }
                    }
                }
            }

            events is UiState.Loading -> SkeletonList(
                modifier = Modifier.padding(padding),
                posterWidth = null,
            )

            events is UiState.Error -> ErrorView(
                failure = (events as UiState.Error).failure,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::refresh,
            )

            else -> FullScreenMessage(
                icon = Icons.Outlined.History,
                message = stringResource(R.string.empty_history),
                modifier = Modifier.padding(padding),
            )
        }
    }

    events.contentOrNull()?.firstOrNull { it.id == editingId }?.let { event ->
        EditEventSheet(
            event = event,
            services = services,
            onDismiss = { editingId = null },
            onSave = { watchedAt, service ->
                viewModel.edit(event, watchedAt, service)
                editingId = null
            },
        )
    }
}

/**
 * One viewing, with its delete behind a swipe.
 *
 * No delete icon on the row: a column of bins down the whole list is noise,
 * and puts a destructive control under the thumb on every line. Swiping is
 * the Android pattern for it, with an undo snackbar standing in for a
 * confirmation dialog. The same delete is offered from a long-press menu, for
 * anyone who does not think to swipe, and as an accessibility action, because
 * TalkBack users cannot swipe a row at all.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    event: WatchEventResponse,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            // The row leaves the list as soon as onDelete runs, so it never
            // needs to settle in the dismissed position itself.
            value == SwipeToDismissBoxValue.EndToStart
        },
    )
    var menuOpen by remember { mutableStateOf(false) }
    val deleteLabel = stringResource(R.string.cd_delete_entry, event.primaryTitle)

    SwipeToDismissBox(
        state = dismissState,
        // One direction only: a swipe either way that deletes is too easy to
        // set off while scrolling.
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Opaque, so the red background only shows where the row
                    // has been dragged away from.
                    .background(MaterialTheme.colorScheme.surface)
                    // The whole row opens the editor: this screen exists as
                    // much for correcting a wrong date as for reading the
                    // record, and a pencil icon per row would be a column of
                    // pencils.
                    .combinedClickable(onClick = onEdit, onLongClick = { menuOpen = true })
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(deleteLabel) {
                                onDelete()
                                true
                            },
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = event.primaryTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Hoisted: stringResource is composable, details() is not.
                val rewatch = stringResource(R.string.history_rewatch)
                val details = HistoryDays.details(event, rewatch)
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_entry)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete_entry)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}
