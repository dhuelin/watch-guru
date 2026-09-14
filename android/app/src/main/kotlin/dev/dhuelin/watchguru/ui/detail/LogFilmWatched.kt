package dev.dhuelin.watchguru.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.ui.components.failureMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LoggedOnFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * Logging a film, today or on a day the user remembers.
 *
 * A film has no episode to tick, so until now it could reach the library but
 * never the history -- and the history is what the statistics, the streak and
 * "what did I watch last October" are all made of.
 *
 * Two actions rather than one control with a date in it. Marking something as
 * you finish it is the common case and stays one tap; naming a past date is the
 * rarer one and is allowed to cost a dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogFilmWatched(
    busy: Boolean,
    outcome: TitleDetailViewModel.FilmLog?,
    onLog: (LocalDate) -> Unit,
    onDismissOutcome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row {
            Button(onClick = { onLog(LocalDate.now(zone)) }, enabled = !busy) {
                Text(stringResource(R.string.action_log_film_today))
            }
            TextButton(
                onClick = { pickingDate = true },
                enabled = !busy,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.action_log_film_on_a_date))
            }
        }

        // Said plainly, and differently for queued: on a train the honest
        // answer is "it is on your phone", and a screen that says "watched"
        // either way is lying about where the record is.
        outcome?.let { state ->
            Text(
                text = when (state) {
                    is TitleDetailViewModel.FilmLog.Logged ->
                        stringResource(R.string.film_logged_on, state.on.format(LoggedOnFormat))
                    is TitleDetailViewModel.FilmLog.Queued ->
                        stringResource(R.string.film_log_queued, state.on.format(LoggedOnFormat))
                    is TitleDetailViewModel.FilmLog.Failed -> failureMessage(state.failure)
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (state) {
                    is TitleDetailViewModel.FilmLog.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (pickingDate) {
        val today = LocalDate.now(zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = today.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                // Nobody has watched anything tomorrow. Refusing the future
                // here is kinder than a server error after the fact, and it is
                // the only bound worth having: films can be rewatched decades
                // after they came out.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        .isAfter(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    // Read back in UTC because that is how the picker stores
                    // it; reading it in the local zone slides the date the
                    // user tapped by a day for anybody east or west of it.
                    state.selectedDateMillis?.let { millis ->
                        onDismissOutcome()
                        onLog(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
