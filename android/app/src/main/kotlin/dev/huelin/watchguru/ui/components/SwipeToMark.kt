package dev.huelin.watchguru.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A row that records a viewing when swiped from the start edge.
 *
 * The swipe is a shortcut for the row's visible watched control, never the
 * only way in: nobody discovers a gesture they were not shown. So [onSwipe]
 * must be the same callback the control calls, which keeps a swipe on the
 * offline queue and the undo snackbar exactly as a tap is.
 *
 * Built on SwipeToDismissBox, as History's delete is, but the row never
 * leaves: the swipe is refused once it has fired, so the row springs back
 * showing its new state.
 *
 * TalkBack users get the same action from [markAction], which the caller
 * puts on the node TalkBack focuses.
 *
 * @param undo true when the swipe takes a viewing away, which changes the
 *   icon so the background says what is about to happen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToMark(
    enabled: Boolean,
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    undo: Boolean = false,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // The state outlives recompositions, so its callback must read the
    // current lambda rather than the one captured on the first frame: the
    // row's episode flips between watched and unwatched under it.
    val currentOnSwipe = rememberUpdatedState(onSwipe)
    var width by remember { mutableIntStateOf(0) }
    lateinit var state: SwipeToDismissBoxState
    state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // Only a drag that really crossed the row counts. The default
            // thresholds are made for dismissing with an undo: 56dp, or any
            // flick at all, which here would untick an episode from a
            // slightly diagonal scroll, with nothing to take it back.
            val far = state.requireOffset() >= width * FIRE_FRACTION
            if (value == SwipeToDismissBoxValue.StartToEnd && far) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnSwipe.value()
            }
            false
        },
        positionalThreshold = { total -> total * FIRE_FRACTION },
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier.onSizeChanged { width = it.width },
        // The end edge is left alone: it is where History puts delete, and a
        // row that does something different each way is too easy to set off
        // while scrolling.
        enableDismissFromEndToStart = false,
        gesturesEnabled = enabled,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer, shape)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = if (undo) Icons.Outlined.RemoveCircleOutline else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) {
        content()
    }
}

/** How far across the row a swipe has to travel to act. */
private const val FIRE_FRACTION = 0.4f

/**
 * The swipe as a TalkBack action, since a screen reader user cannot swipe a
 * row at all.
 *
 * Put it on the node TalkBack actually focuses: the row's clickable card, or
 * a row merged with `mergeDescendants`. On the swipe container itself it is
 * never reached, because that node is not focusable.
 */
fun Modifier.markAction(enabled: Boolean, label: String, onAction: () -> Unit): Modifier =
    semantics {
        if (enabled) {
            customActions = listOf(
                CustomAccessibilityAction(label) {
                    onAction()
                    true
                },
            )
        }
    }
