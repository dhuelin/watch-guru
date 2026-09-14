package dev.dhuelin.watchguru.ui.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.ImportPreviewResponse
import dev.dhuelin.watchguru.api.models.ImportResultResponse
import dev.dhuelin.watchguru.api.models.ImportRowResponse
import java.time.format.DateTimeFormatter

private val WatchedOnFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * Bringing an existing history in from a file.
 *
 * The screen is built around one refusal: it will not write anything the user
 * has not looked at. A file becomes a list they can argue with, and only what
 * survives that is sent. Matched rows start selected because agreeing with
 * four hundred correct rows one at a time is not a review, it is a chore;
 * ambiguous ones start unselected, because the app guessing on their behalf is
 * the failure this two-step shape exists to avoid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val chosen by viewModel.chosen.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val unreadable = stringResource(R.string.error_import_unreadable)
    val tooLarge = stringResource(R.string.error_import_too_large)
    val previewFailed = stringResource(R.string.error_import_preview)
    val commitFailed = stringResource(R.string.error_import_commit)

    LaunchedEffect(error) {
        val message = when (error) {
            ImportViewModel.ImportError.UNREADABLE -> unreadable
            ImportViewModel.ImportError.TOO_LARGE -> tooLarge
            ImportViewModel.ImportError.PREVIEW_FAILED -> previewFailed
            ImportViewModel.ImportError.COMMIT_FAILED -> commitFailed
            null -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::fileChosen)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val current = stage) {
            is ImportStage.Choosing -> ChooseFile(
                onChoose = { picker.launch(ImportMimeTypes) },
                modifier = Modifier.padding(padding),
            )

            is ImportStage.Reading -> Busy(
                message = stringResource(R.string.import_reading),
                modifier = Modifier.padding(padding),
            )

            is ImportStage.Committing -> Busy(
                message = stringResource(R.string.import_committing),
                modifier = Modifier.padding(padding),
            )

            is ImportStage.Previewing -> Preview(
                preview = current.preview,
                chosen = chosen,
                onToggle = viewModel::toggle,
                onChoose = viewModel::choose,
                onCommit = viewModel::commit,
                modifier = Modifier.padding(padding),
            )

            is ImportStage.Finished -> Finished(
                result = current.result,
                onAnother = viewModel::startOver,
                onDone = onBack,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/**
 * What the file picker will show.
 *
 * `application/octet-stream` is in the list because it is what several
 * document providers label a `.csv` they do not recognise, and a picker that
 * greys out the file the user just downloaded is a dead end with no
 * explanation.
 */
private val ImportMimeTypes = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/csv",
    "application/vnd.ms-excel",
    "application/octet-stream",
)

@Composable
private fun ChooseFile(onChoose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.import_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onChoose, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.action_choose_file))
        }
        Text(
            text = stringResource(R.string.import_sources_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = stringResource(R.string.import_sources_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Busy(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * The file, as a list the user can disagree with.
 *
 * Rows are grouped by what they need from the reader rather than left in file
 * order: a matched row needs a glance, an ambiguous one needs a decision, and
 * an unmatched one needs nothing at all. Interleaved, the decisions hide among
 * four hundred rows that do not need any.
 */
@Composable
private fun Preview(
    preview: ImportPreviewResponse,
    chosen: Map<String, Long>,
    onToggle: (ImportRowResponse) -> Unit,
    onChoose: (ImportRowResponse, Long) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val matched = preview.rows.filter { it.status == ImportRowResponse.Status.MATCHED }
    val ambiguous = preview.rows.filter { it.status == ImportRowResponse.Status.AMBIGUOUS }
    val unmatched = preview.rows.filter { it.status == ImportRowResponse.Status.UNMATCHED }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.import_source_label, sourceName(preview.source)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (preview.alreadyImported > 0) {
                item {
                    Text(
                        text = stringResource(
                            R.string.import_already_imported, preview.alreadyImported,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (preview.warnings.isNotEmpty()) {
                item { Notes(stringResource(R.string.import_warnings_title), preview.warnings) }
            }

            if (preview.rows.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.import_nothing_found),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            if (ambiguous.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.import_section_ambiguous),
                        hint = stringResource(R.string.import_ambiguous_hint),
                    )
                }
                items(ambiguous, key = { it.sourceRef }) { row ->
                    AmbiguousRow(
                        row = row,
                        chosenTitleId = chosen[row.sourceRef],
                        onChoose = { onChoose(row, it) },
                    )
                }
            }

            if (matched.isNotEmpty()) {
                item { SectionHeader(title = stringResource(R.string.import_section_matched)) }
                items(matched, key = { it.sourceRef }) { row ->
                    MatchedRow(
                        row = row,
                        selected = chosen.containsKey(row.sourceRef),
                        onToggle = { onToggle(row) },
                    )
                }
            }

            if (unmatched.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.import_section_unmatched),
                        hint = stringResource(R.string.import_unmatched_hint),
                    )
                }
                items(unmatched, key = { it.sourceRef }) { row ->
                    Text(
                        text = rowLabel(row),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            if (preview.problems.isNotEmpty()) {
                item { Notes(stringResource(R.string.import_problems_title), preview.problems) }
            }
        }

        HorizontalDivider()
        Button(
            onClick = onCommit,
            enabled = chosen.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                if (chosen.isEmpty()) {
                    stringResource(R.string.import_none_selected)
                } else {
                    stringResource(R.string.action_import_selected, chosen.size)
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String? = null) {
    Column(modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The parser's own complaints, shown rather than swallowed. */
@Composable
private fun Notes(title: String, lines: List<String>) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MatchedRow(
    row: ImportRowResponse,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    // The row is the control, not the checkbox: that way the title is the
    // label a screen reader reads out, the whole row is the tap target, and
    // the box does not announce itself a second time. Hence
    // `onCheckedChange = null` -- a checkbox that handled its own tap here
    // would be a second, smaller control saying the same thing.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = selected,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.titleName ?: row.titleText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = rowDetail(row)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A row the server could not settle, and the candidates it found.
 *
 * Nothing is preselected. The app choosing here and the user not noticing is
 * precisely how a library ends up with the wrong *Fargo* in it.
 */
@Composable
private fun AmbiguousRow(
    row: ImportRowResponse,
    chosenTitleId: Long?,
    onChoose: (Long) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = rowLabel(row), style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.candidates.forEach { candidate ->
                FilterChip(
                    selected = chosenTitleId == candidate.titleId,
                    onClick = { onChoose(candidate.titleId) },
                    label = {
                        Text(
                            text = if (candidate.year != null) {
                                stringResource(
                                    R.string.import_candidate_year,
                                    candidate.titleName,
                                    candidate.year!!,
                                )
                            } else {
                                candidate.titleName
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun Finished(
    result: ImportResultResponse,
    onAnother: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.import_done_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(
                R.string.import_done_body, result.imported, result.skipped,
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (result.failed > 0) {
            Text(
                text = stringResource(R.string.import_done_failed, result.failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (result.problems.isNotEmpty()) {
            Notes(stringResource(R.string.import_problems_title), result.problems)
        }
        Button(onClick = onDone, modifier = Modifier.padding(top = 32.dp)) {
            Text(stringResource(R.string.action_done))
        }
        TextButton(onClick = onAnother) {
            Text(stringResource(R.string.action_import_another))
        }
    }
}

/** The file's own words for the row, which is all an unmatched row has. */
private fun rowLabel(row: ImportRowResponse): String {
    val year = row.year
    return if (year != null) "${row.titleText} ($year)" else row.titleText
}

/**
 * The second line: episode, date, rating, and anything the matcher wants to say.
 *
 * Built from locals rather than inside a builder lambda, because every piece
 * of it is a `stringResource` and composable calls want to be in the body of a
 * composable function, not in a lambda that happens to be inlined into one.
 */
@Composable
private fun rowDetail(row: ImportRowResponse): String? {
    val watchedAt = row.watchedAt
    val rating = row.rating
    val parts = mutableListOf<String>()

    if (row.episodeCode != null) parts.add(row.episodeCode!!)
    if (watchedAt != null) {
        parts.add(stringResource(R.string.import_row_watched_on, WatchedOnFormat.format(watchedAt)))
    }
    if (rating != null) {
        parts.add(
            stringResource(R.string.import_row_rating, rating.stripTrailingZeros().toPlainString()),
        )
    }
    if (row.note != null) parts.add(row.note!!)

    return parts.takeIf { it.isNotEmpty() }?.joinToString("  \u00b7  ")
}

@Composable
private fun sourceName(source: ImportPreviewResponse.Source): String = stringResource(
    when (source) {
        ImportPreviewResponse.Source.WATCH_GURU -> R.string.import_source_watch_guru
        ImportPreviewResponse.Source.IMDB -> R.string.import_source_imdb
        ImportPreviewResponse.Source.LETTERBOXD -> R.string.import_source_letterboxd
        ImportPreviewResponse.Source.NETFLIX -> R.string.import_source_netflix
        ImportPreviewResponse.Source.UNKNOWN -> R.string.import_source_unknown
    },
)
