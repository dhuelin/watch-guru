package dev.dhuelin.watchguru.data.offline

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

/**
 * The last good copy of something the API returned.
 *
 * Read-through rather than read-first: a screen still asks the network, and
 * this answers only when the network cannot. That ordering matters -- showing
 * a stale library to someone with a working connection would be a bug, not a
 * feature.
 *
 * @param clock injected so staleness can be tested without waiting.
 */
class SnapshotCache(
    private val files: FileStore,
    private val json: Json,
    private val clock: () -> Instant = Instant::now,
) {

    /**
     * Stores a snapshot, replacing whatever was there.
     *
     * Failures are swallowed on purpose. A cache that cannot be written is a
     * missing convenience; turning it into a visible error would break a
     * request that actually succeeded.
     */
    fun <T> put(key: String, serializer: KSerializer<T>, value: T) {
        runCatching {
            files.write(key, json.encodeToString(Envelope.serializer(serializer),
                Envelope(clock().toEpochMilli(), value)))
        }
    }

    /**
     * The stored snapshot, or null.
     *
     * @param maxAge how old a snapshot may be and still be worth showing. Null
     *   means any age -- correct for a library the user last saw a week ago,
     *   because the alternative on a train is an empty screen.
     */
    fun <T> get(key: String, serializer: KSerializer<T>, maxAge: Duration? = null): Cached<T>? {
        val raw = files.read(key) ?: return null
        val envelope = runCatching {
            json.decodeFromString(Envelope.serializer(serializer), raw)
        }.getOrNull()

        if (envelope == null) {
            // Written by an older version of the app, or truncated. Unreadable
            // is the same as absent, and leaving it would mean failing to parse
            // it on every single launch.
            files.delete(key)
            return null
        }

        val storedAt = Instant.ofEpochMilli(envelope.storedAt)
        val age = Duration.between(storedAt, clock())
        if (maxAge != null && age > maxAge) {
            return null
        }
        return Cached(envelope.value, storedAt, age)
    }

    fun evict(key: String) {
        files.delete(key)
    }

    /** A snapshot and how old it is, so the UI can say "as of yesterday". */
    data class Cached<T>(val value: T, val storedAt: Instant, val age: Duration)

    @kotlinx.serialization.Serializable
    private data class Envelope<T>(val storedAt: Long, val value: T)

    companion object {
        /** Cache keys. Namespaced per user is unnecessary: sign-out clears them. */
        const val LIBRARY = "cache-library.json"
        const val UP_NEXT = "cache-up-next.json"
    }
}
