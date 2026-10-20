package dev.huelin.watchguru.data

import dev.huelin.watchguru.data.offline.FileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which streaming services the user says they pay for.
 *
 * Used to order "Where to watch": a subscription they already hold costs them
 * nothing, so it belongs above a free service they have never heard of. See
 * [WatchOffers.group], whose ordering comment promised this before it existed.
 *
 * ### Why this lives on the device
 *
 * It is not on the backend, and that is a deliberate limit rather than an
 * oversight. The region *is* server-side because the server needs it — every
 * availability query is per country. This is only ever read to arrange a list
 * the client already has, so the client can own it, and owning it there means
 * no new endpoint, no migration, and no regenerated API client.
 *
 * What that costs: the selection does not follow the user to a second device.
 * #18 asks for it to *persist*, which this does. Moving it server-side later is
 * a migration of a handful of ids, and the seam for it is this class.
 *
 * ### Why it clears on sign-out
 *
 * "I pay for Netflix and MUBI" is a fact about a person, not about a phone. On
 * a shared device it would otherwise greet the next user with the last one's
 * subscriptions — the same reason the cached library and the widget feed go.
 */
@Singleton
class MyServices @Inject constructor(
    private val store: FileStore,
    private val json: Json,
) {

    private val _selected = MutableStateFlow(read())

    /**
     * The ids, observable so an open title screen re-orders as soon as the
     * picker is closed rather than on its next load.
     */
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    fun save(ids: Set<Long>) {
        // Sorted, so the file does not churn when a set iterates differently
        // between runs; a diff of this file should mean a real change.
        store.write(FileName, json.encodeToString(Serializer, ids.sorted()))
        _selected.value = ids
    }

    fun clear() {
        store.delete(FileName)
        _selected.value = emptySet()
    }

    private fun read(): Set<Long> {
        val content = store.read(FileName) ?: return emptySet()
        // A file we cannot parse is treated as no selection rather than as an
        // error: the worst case is an ordering the user has to set again, and
        // refusing to show "Where to watch" over it would be far worse.
        return runCatching { json.decodeFromString(Serializer, content).toSet() }
            .getOrDefault(emptySet())
    }

    private companion object {
        const val FileName = "my-services.json"

        /**
         * Named explicitly rather than reified, matching
         * [SnapshotCache][dev.huelin.watchguru.data.offline.SnapshotCache]:
         * the reified `encodeToString` lives in a different package from the
         * one this file already imports, and one import either way is not
         * worth a build failure.
         */
        val Serializer = ListSerializer(Long.serializer())
    }
}
