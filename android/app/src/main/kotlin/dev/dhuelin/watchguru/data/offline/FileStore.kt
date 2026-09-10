package dev.dhuelin.watchguru.data.offline

/**
 * Somewhere to put bytes that survive the process.
 *
 * The whole offline layer sits on this one seam, and it is deliberately tiny:
 * everything above it -- staleness, ordering, replay, collapsing -- is plain
 * Kotlin and unit-tested, and the only part that needs a device is the four
 * lines that know where `filesDir` is.
 *
 * Not Room. Room's advantage is querying, and nothing here queries: the app
 * loads a library of a few hundred rows and filters it in memory. A JSON
 * snapshot buys the same behaviour with no code generation, no schema
 * migrations, and logic that can be tested on a plain JVM. If the library ever
 * grows to the point where loading it whole is wrong, that is the moment to
 * introduce Room -- behind this same interface.
 */
interface FileStore {

    fun read(name: String): String?

    fun write(name: String, content: String)

    fun delete(name: String)
}

/** For tests. */
class InMemoryFileStore : FileStore {

    private val files = mutableMapOf<String, String>()

    override fun read(name: String): String? = synchronized(files) { files[name] }

    override fun write(name: String, content: String) {
        synchronized(files) { files[name] = content }
    }

    override fun delete(name: String) {
        synchronized(files) { files.remove(name) }
    }
}
