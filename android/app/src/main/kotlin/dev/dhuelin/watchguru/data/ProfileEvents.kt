package dev.dhuelin.watchguru.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one-way channel from "the user changed their region" to "every screen
 * showing regional data is now wrong".
 *
 * Streaming offers are per country, and a title screen loads them once when it
 * is created. The navigation host keeps destinations on the back stack, so
 * without this, changing region in Profile and going back to an open title
 * still shows the offers for the country the user just left -- which is
 * precisely the thing they went to Profile to fix.
 *
 * A counter rather than the region itself: what a screen needs to know is
 * "something about the profile changed, reload", and a counter cannot be
 * misread as the current value of anything.
 *
 * No Android types, so it is testable on a plain JVM.
 */
class ProfileEvents {

    private val _changes = MutableStateFlow(0)

    /** Increments whenever the profile changes in a way screens must reflect. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun profileChanged() {
        _changes.value += 1
    }
}
