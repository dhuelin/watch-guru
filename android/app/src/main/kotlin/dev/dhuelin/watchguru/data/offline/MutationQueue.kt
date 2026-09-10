package dev.dhuelin.watchguru.data.offline

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The changes waiting to reach the server, in the order they were made.
 *
 * Persisted, because the case this exists for is a user who marks three
 * episodes on a train and then closes the app. Losing those is worse than
 * never having accepted them.
 *
 * Synchronised rather than lock-free: enqueue happens on whatever thread a
 * screen is on, drain happens on a background one, and the cost of a lock on an
 * operation this rare is not worth reasoning about.
 */
class MutationQueue(
    private val files: FileStore,
    private val json: Json,
) {

    private val lock = Any()

    /**
     * Appends a mutation and returns it with its assigned id.
     *
     * Collapsing happens here rather than at drain time so the queue never
     * grows unboundedly while offline: toggling one episode watched and
     * unwatched forty times leaves one entry, not forty.
     */
    fun enqueue(build: (Long) -> PendingMutation): PendingMutation = synchronized(lock) {
        val current = load()
        val mutation = build((current.maxOfOrNull { it.id } ?: 0L) + 1)

        // A later mutation on the same target supersedes the earlier one. This
        // is only safe because these operations are absolute, not relative:
        // "unmark episode 5" does not depend on what came before it. A
        // relative operation -- "increment", say -- could never be collapsed
        // this way.
        val kept = current.filterNot { it.target == mutation.target }
        save(kept + mutation)
        mutation
    }

    fun pending(): List<PendingMutation> = synchronized(lock) { load().sortedBy { it.id } }

    fun remove(id: Long) = synchronized(lock) {
        save(load().filterNot { it.id == id })
    }

    fun clear() = synchronized(lock) { files.delete(FILE) }

    private fun load(): List<PendingMutation> {
        val raw = files.read(FILE) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(PendingMutation.serializer()), raw)
        }.getOrElse {
            // Unreadable means a queue written by a version of the app that
            // knew different mutation types. Dropping it loses work, which is
            // bad; retrying forever against a parser that cannot read it is
            // worse, and would wedge every future sync.
            files.delete(FILE)
            emptyList()
        }
    }

    private fun save(mutations: List<PendingMutation>) {
        if (mutations.isEmpty()) {
            files.delete(FILE)
        } else {
            files.write(FILE, json.encodeToString(ListSerializer(PendingMutation.serializer()), mutations))
        }
    }

    private companion object {
        const val FILE = "pending-mutations.json"
    }
}
