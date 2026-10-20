package dev.huelin.watchguru.data

import dev.huelin.watchguru.data.offline.InMemoryFileStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The services the user says they pay for, and the three things that must hold
 * about where they are kept: it survives a restart, a damaged file does not
 * take a screen down with it, and signing out leaves nothing behind.
 */
class MyServicesTest {

    private val json = Json

    private fun services(store: InMemoryFileStore) = MyServices(store, json)

    @Test
    fun `a selection survives a new instance`() {
        // Two instances over one store is what a restart looks like from here:
        // the second reads what the first wrote, through the same file.
        val store = InMemoryFileStore()
        services(store).save(setOf(3L, 1L))

        assertEquals(setOf(1L, 3L), services(store).selected.value)
    }

    @Test
    fun `nothing chosen reads as an empty set, not an error`() {
        assertEquals(emptySet<Long>(), services(InMemoryFileStore()).selected.value)
    }

    @Test
    fun `a file that cannot be parsed reads as no selection`() {
        // The worst case is an ordering the user sets again. Throwing here
        // would take down "Where to watch", which is far worse than a list in
        // the wrong order.
        val store = InMemoryFileStore()
        store.write("my-services.json", "not json")

        assertEquals(emptySet<Long>(), services(store).selected.value)
    }

    @Test
    fun `clearing removes the file, not just the value in memory`() {
        val store = InMemoryFileStore()
        val subject = services(store)
        subject.save(setOf(5L))

        subject.clear()

        assertEquals(emptySet<Long>(), subject.selected.value)
        // And a fresh instance agrees, which is the part that matters on a
        // shared device: the next user must not inherit this.
        assertEquals(emptySet<Long>(), services(store).selected.value)
    }

    @Test
    fun `the flow reports a change without a reload`() {
        // What makes an open title screen re-order the moment the picker is
        // closed, rather than on its next load.
        val subject = services(InMemoryFileStore())

        subject.save(setOf(2L))

        assertEquals(setOf(2L), subject.selected.value)
    }

    @Test
    fun `saving an empty set is a selection, not a clear`() {
        // Un-ticking the last service writes [], and reading it back must stay
        // empty rather than falling through to "no file, so no opinion".
        val store = InMemoryFileStore()
        services(store).save(setOf(1L))
        services(store).save(emptySet())

        assertEquals(emptySet<Long>(), services(store).selected.value)
        assertEquals("[]", store.read("my-services.json"))
    }
}
