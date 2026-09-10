package dev.dhuelin.watchguru.data

import android.content.Context
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clears Coil's image caches.
 *
 * Coil's default image loader keeps a disk cache that outlives sign-out. Poster
 * art is not secret in itself, but on a shared device it is a list of what the
 * previous user was watching -- exactly what signing out is meant to remove.
 *
 * API responses need no equivalent: the OkHttp client is built without a cache,
 * so nothing from the API reaches the disk in the first place.
 */
class CoilDataCleaner(private val context: Context) : LocalDataCleaner {

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            val loader = context.imageLoader
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }
}
