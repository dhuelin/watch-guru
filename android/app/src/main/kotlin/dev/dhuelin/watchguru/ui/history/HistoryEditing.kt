package dev.dhuelin.watchguru.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.StreamingServiceResponse
import dev.dhuelin.watchguru.api.models.WatchEventResponse
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val EditDayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * One filter row, above everything it scopes.
 *
 * Search, then what kind of thing, then where it was watched -- the same order
 * somebody would say them. Every change is a fresh request rather than a filter
 * over the page already loaded: the history is paged, and filtering a page
 * would search the last fifty viewings while calling itself a search of the
 * history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Filters(
    query: String,
    type: HistoryType,
    serviceId: Long?,
    range: DateRange,
    services: List<StreamingServiceResponse>,
    onQuery: (String) -> Unit,
    onType: (HistoryType) -> Unit,
    onService: (Long?) -> Unit,
    onRange: (DateRange) -> Unit,
) {
    var pickingRange by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            label = { Text(stringResource(R.string.history_search_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
        ) {
            HistoryType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { onType(option) },
                    label = { Text(stringResource(option.label())) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            // "What did I watch last October" is the question, so the span of
            // days is a filter like any other rather than something buried in a
            // menu.
            FilterChip(
                selected = !range.isAnyTime,
                onClick = { pickingRange = true },
                label = { Text(range.label()) },
            )
            if (!range.isAnyTime) {
                TextButton(onClick = { onRange(DateRange.any()) }) {
                    Text(stringResource(R.string.history_clear_dates))
                }
            }
        }

        if (services.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = serviceId == null,
                    onClick = { onService(null) },
                    label = { Text(stringResource(R.string.history_any_service)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
                services.forEach { service ->
                    FilterChip(
                        selected = serviceId == service.id,
                        onClick = { onService(service.id) },
                        label = { Text(service.name) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }

    if (pickingRange) {
        DateRangeDialog(
            range = range,
            onDismiss = { pickingRange = false },
            onPick = { picked ->
                onRange(picked)
                pickingRange = false
            },
        )
    }
}

/**
 * Picking the span of days, both bounds inclusive.
 *
 * One range picker rather than two date fields: the pair is a single idea, and
 * two fields invite a start after its end -- a state that has to be either
 * validated or rendered as an empty history, and neither is worth the fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    range: DateRange,
    onDismiss: () -> Unit,
    onPick: (DateRange) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = range.from?.let { utcMillis(it) },
        initialSelectedEndDateMillis = range.to?.let { utcMillis(it) },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                // An end alone is a bound too: "everything up to March" is a
                // question people ask. Only both being absent means any time.
                onPick(DateRange(state.selectedStartDateMillis?.let(::utcDate),
                        state.selectedEndDateMillis?.let(::utcDate)))
            }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DateRangePicker(state = state)
    }
}

/**
 * The picker works in UTC midnights, so both directions convert in UTC.
 *
 * Reading a selection back in the local zone is what slides the day the user
 * tapped by one for anybody east or west of it.
 */
private fun utcMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

private fun utcDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()

@Composable
private fun DateRange.label(): String = when {
    isAnyTime -> stringResource(R.string.history_any_time)
    from != null && to != null ->
        from.format(EditDayFormat) + " – " + to.format(EditDayFormat)
    from != null -> stringResource(R.string.history_since, from.format(EditDayFormat))
    else -> stringResource(R.string.history_until, to!!.format(EditDayFormat))
}

private fun HistoryType.label() = when (this) {
    HistoryType.ALL -> R.string.history_filter_all
    HistoryType.FILMS -> R.string.history_filter_films
    HistoryType.SERIES -> R.string.history_filter_series
}

/**
 * Correcting one entry: when it was watched, and where.
 *
 * A date rather than a date and a time. Nobody remembers that they started a
 * film at 20:47, and asking for a precision the user does not have invites a
 * wrong answer; the time of day is kept from the original event so a correction
 * to the date changes only the date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventSheet(
    event: WatchEventResponse,
    services: List<StreamingServiceResponse>,
    onDismiss: () -> Unit,
    onSave: (Instant?, Long?) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val originalDate = remember(event.id) { event.watchedAt.atZoneSameInstant(zone).toLocalDate() }
    val originalTime = remember(event.id) { event.watchedAt.atZoneSameInstant(zone).toLocalTime() }

    var date by rememberSaveable(event.id) { mutableStateOf(originalDate) }
    var service by rememberSaveable(event.id) { mutableStateOf(event.streamingServiceId) }
    var pickingDate by rememberSaveable(event.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(text = event.primaryTitle, style = MaterialTheme.typography.titleMedium)
            event.episodeCode?.let {
                Text(
                    text = listOfNotNull(it, event.episodeName).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.history_watched_on),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            AssistChip(
                onClick = { pickingDate = true },
                label = { Text(date.format(EditDayFormat)) },
                modifier = Modifier.padding(top = 4.dp),
            )

            if (services.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.history_watched_where),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    services.forEach { option ->
                        FilterChip(
                            selected = service == option.id,
                            onClick = { service = option.id },
                            label = { Text(option.name) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }

            Row(modifier = Modifier.padding(top = 24.dp)) {
                Button(
                    onClick = {
                        // Only what changed is sent: null leaves a field alone
                        // server-side, so an untouched service is not resent
                        // and cannot be got wrong on the way.
                        val movedTo = if (date == originalDate) null
                        else date.atTime(originalTime).atZone(zone).toInstant()
                        val movedService = if (service == event.streamingServiceId) null else service
                        onSave(movedTo, movedService)
                    },
                    enabled = date != originalDate || service != event.streamingServiceId,
                ) {
                    Text(stringResource(R.string.action_save))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker works in UTC midnights; reading it back in UTC
                    // is what keeps the date the user tapped from sliding by a
                    // day for anybody east or west of it.
                    state.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
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
