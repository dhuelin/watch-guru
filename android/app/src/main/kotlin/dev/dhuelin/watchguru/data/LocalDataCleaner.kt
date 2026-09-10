package dev.dhuelin.watchguru.data

/**
 * Everything that has to be wiped on sign-out besides the token.
 *
 * Clearing only the token is not enough on a shared device: cached artwork
 * alone reveals what the previous user was watching. This is the seam for that,
 * and it is where the offline cache (#14) will hook in when it exists.
 *
 * Suspending because clearing a disk cache is disk I/O, which has no business
 * on the main thread.
 */
interface LocalDataCleaner {
    suspend fun clear()
}
