package dev.huelin.watchguru.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.huelin.watchguru.R

/**
 * Placeholder rows shown while a list loads for the first time.
 *
 * Shaped like the rows they stand in for, so the screen does not jump when the
 * real ones arrive, and so a slow first load reads as "your list is on its way"
 * rather than a spinner in the middle of nothing. Only for a first load: a
 * refresh keeps the rows the user is already looking at.
 *
 * @param posterWidth the width of the real rows' poster, or null for lists
 *   whose rows carry no artwork, such as History
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 6,
    posterWidth: Dp? = 56.dp,
) {
    val description = stringResource(R.string.cd_loading)
    // A slow pulse says "still working". Compose skips it when the system's
    // animator scale is off, which is how Remove animations reaches apps.
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .alpha(pulse)
            // One node for TalkBack, not a dozen unlabelled shapes.
            .clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(rows) {
            SkeletonRow(posterWidth)
        }
    }
}

@Composable
private fun SkeletonRow(posterWidth: Dp?) {
    val fill = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (posterWidth != null) {
            Box(
                modifier = Modifier
                    .width(posterWidth)
                    .aspectRatio(POSTER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(8.dp))
                    .background(fill),
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = if (posterWidth != null) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill),
            )
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill),
            )
        }
    }
}
