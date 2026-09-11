package dev.dhuelin.watchguru.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The signal that tells open screens their regional data is stale. */
class ProfileEventsTest {

    @Test
    fun `each change is a new value, so a collector cannot miss two in a row`() {
        val events = ProfileEvents()
        val first = events.changes.value

        events.profileChanged()
        events.profileChanged()

        assertEquals(first + 2, events.changes.value)
    }

    @Test
    fun `nothing is signalled until something changes`() {
        assertEquals(0, ProfileEvents().changes.value)
    }
}
