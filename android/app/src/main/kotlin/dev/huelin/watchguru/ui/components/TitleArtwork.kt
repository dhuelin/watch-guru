package dev.huelin.watchguru.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.huelin.watchguru.R

/** The universal film-poster ratio. Never crop a poster to a square. */
const val POSTER_ASPECT_RATIO = 2f / 3f
const val BACKDROP_ASPECT_RATIO = 16f / 9f

/** How tall a title screen's backdrop is, whatever the width. */
val BACKDROP_HEIGHT = 240.dp

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

/**
 * A title's backdrop, fading into the screen behind it.
 *
 * A fixed height rather than 16:9 of the width: on a phone in portrait the two
 * are about the same, and in landscape or on a tablet 16:9 would be a whole
 * screen of artwork before anything the user came for. The image is cropped
 * to fill it.
 *
 * Decoration under the header rather than content, so it has no description
 * for TalkBack and no placeholder of its own: without a backdrop the header
 * is simply the background colour, which is what a title with no artwork
 * should look like.
 */
@Composable
fun Backdrop(
    url: String?,
    modifier: Modifier = Modifier,
) {
    val fadeTo = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BACKDROP_HEIGHT),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // From clear at the top to the background at the bottom, so the
        // poster laid over its lower edge sits on the screen rather than on a
        // hard line where the image stops.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.3f to Color.Transparent,
                        1f to fadeTo,
                    ),
                ),
        )
    }
}
