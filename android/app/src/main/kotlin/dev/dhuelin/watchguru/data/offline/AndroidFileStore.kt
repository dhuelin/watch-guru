package dev.dhuelin.watchguru.data.offline

import android.content.Context
import java.io.File

/**
 * [FileStore] over the app's private files directory.
 *
 * The only part of the offline layer that knows about Android, which is why it
 * is this short: everything that could be got wrong lives above it, in plain
 * Kotlin, under test.
 *
 * Not encrypted. What is here is a copy of the user's own library and the
 * changes they have made to it -- the same data the API would return them --
 * and it is already inside the app sandbox. The credentials are a different
 * matter and live in the Keystore; see EncryptedTokenStore.
 */
class AndroidFileStore(context: Context) : FileStore {

    private val directory = File(context.filesDir, "offline").apply { mkdirs() }

    override fun read(name: String): String? =
        runCatching { File(directory, name).takeIf { it.exists() }?.readText() }.getOrNull()

    override fun write(name: String, content: String) {
        // Write-then-rename, so a process killed mid-write leaves the previous
        // snapshot intact rather than a truncated one. The reader tolerates a
        // corrupt file, but only by discarding it, which loses queued work.
        runCatching {
            val temporary = File(directory, "$name.tmp")
            temporary.writeText(content)
            temporary.renameTo(File(directory, name))
        }
    }

    override fun delete(name: String) {
        runCatching { File(directory, name).delete() }
    }
}
