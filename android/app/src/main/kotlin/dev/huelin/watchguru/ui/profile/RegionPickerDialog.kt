package dev.huelin.watchguru.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.huelin.watchguru.R
import dev.huelin.watchguru.data.Regions

/**
 * Picks the country whose streaming offers the user sees.
 *
 * Every country rather than a shortlist of the popular ones: a shortlist is a
 * judgement about whose custom matters, and the person it excludes is left
 * with an app that is quietly wrong for them. A search field is what makes two
 * hundred rows usable.
 */
@Composable
fun RegionPickerDialog(
    currentRegion: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    // Built once: the list is the same for the life of the dialog, and
    // rebuilding it on every keystroke would sort two hundred strings through
    // a Collator for nothing.
    val regions = remember { Regions.all() }
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(regions, query) { Regions.search(regions, query) }

    // Opens on the current region rather than on Afghanistan. Otherwise the
    // one row the user most needs to see -- what it is set to now -- is two
    // hundred rows away, and the dialog reads as though nothing is selected.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = regions
            .indexOfFirst { it.code.equals(currentRegion, ignoreCase = true) }
            .coerceAtLeast(0),
    )
    // Back to the top of the matches when the query changes, so a search
    // never opens scrolled past its own first result.
    LaunchedEffect(query) {
        if (query.isNotEmpty()) listState.scrollToItem(0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.region_picker_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.region_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp)) {
                    items(matches, key = { it.code }) { region ->
                        val selected = region.code.equals(currentRegion, ignoreCase = true)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                // The whole row, not just the radio button:
                                // a 20dp target in a list of two hundred is a
                                // fiddly thing to hit.
                                .clickable { onPick(region.code) }
                                .padding(vertical = 4.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = region.name
                                },
                        ) {
                            RadioButton(selected = selected, onClick = { onPick(region.code) })
                            Text(region.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            // No "save": picking a row is the change. A confirm button here
            // would be a second thing to press for a decision already made.
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
