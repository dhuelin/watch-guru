package dev.huelin.watchguru.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.huelin.watchguru.ui.theme.card

/**
 * One item in a list, as a card on the tinted background.
 *
 * Every list of titles uses this, so Up Next, Library and Search agree on the
 * colour, the corners and the gap between rows. Flat rows on the background
 * read as a page of text; a card reads as a thing you can open.
 */
@Composable
fun RowCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        // The same colour when disabled: a card is disabled only while its
        // tap is already being answered, which is not a reason to grey it.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.card,
            disabledContainerColor = MaterialTheme.colorScheme.card,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        content = content,
    )
}

/** The padding around, and the gap between, every list of [RowCard]s. */
object RowCardList {
    val contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)
    val spacing = Arrangement.spacedBy(10.dp)
}
