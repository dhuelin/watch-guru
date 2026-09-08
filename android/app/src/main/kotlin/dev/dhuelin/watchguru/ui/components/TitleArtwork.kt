package dev.dhuelin.watchguru.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.dhuelin.watchguru.R

/** The universal film-poster ratio. Never crop a poster to a square. */
const val POSTER_ASPECT_RATIO = 2f / 3f
const val BACKDROP_ASPECT_RATIO = 16f / 9f

/**
 * A poster, with the placeholder that stands in when there isn't one.
 *
 * TMDB does not have artwork for everything, so a missing poster is normal
 * rather than exceptional. The placeholder carries the title's initials: a bare
 * grey rectangle is indistinguishable from a failed load, which is
 * indistinguishable from a bug.
 *
 * The box reserves space at the correct ratio before the image arrives, so a
 * list does not reflow as its rows load.
 */
@Composable
fun Poster(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(POSTER_ASPECT_RATIO)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = initialsOf(title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.cd_poster, title),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun initialsOf(title: String): String = title.trim()
    .split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { "?" }
