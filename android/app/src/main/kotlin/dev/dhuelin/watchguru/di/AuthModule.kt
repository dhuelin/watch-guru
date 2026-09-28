package dev.dhuelin.watchguru.di

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dhuelin.watchguru.BuildConfig
import dev.dhuelin.watchguru.data.CoilDataCleaner
import dev.dhuelin.watchguru.data.GoogleSignIn
import dev.dhuelin.watchguru.data.LocalDataCleaner
import dev.dhuelin.watchguru.data.OfflineRepository
import dev.dhuelin.watchguru.data.SessionEvents
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.data.offline.AndroidFileStore
import dev.dhuelin.watchguru.data.offline.FileStore
import dev.dhuelin.watchguru.data.offline.MutationQueue
import dev.dhuelin.watchguru.data.offline.SnapshotCache
import dev.dhuelin.watchguru.widget.UpNextWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    /**
     * No context here on purpose: Credential Manager needs the Activity, which
     * is supplied per call. Binding the application context would compile and
     * then fail at runtime when the sheet tries to appear.
     */
    @Provides
    @Singleton
    fun googleSignIn(): GoogleSignIn = GoogleSignIn(BuildConfig.GOOGLE_WEB_CLIENT_ID)

    @Provides
    @Singleton
    fun localDataCleaner(
        @ApplicationContext context: Context,
        offline: OfflineRepository,
    ): LocalDataCleaner = object : LocalDataCleaner {
        private val images = CoilDataCleaner(context)

        override suspend fun clear() {
            // Both halves. The cached library is the previous user's watch
            // history in all but name, and the queue may still hold changes
            // that would otherwise be replayed against whoever signs in next --
            // attributing one person's viewing to another's account.
            offline.clearLocalData()
            images.clear()
        }
    }

    @Provides
    @Singleton
    fun sessionEvents(): SessionEvents = SessionEvents()

    @Provides
    @Singleton
    fun fileStore(@ApplicationContext context: Context): FileStore = AndroidFileStore(context)

    @Provides
    @Singleton
    fun snapshotCache(files: FileStore, json: Json): SnapshotCache = SnapshotCache(files, json)

    @Provides
    @Singleton
    fun mutationQueue(files: FileStore, json: Json): MutationQueue = MutationQueue(files, json)

    @Provides
    @Singleton
    fun offlineRepository(
        network: WatchGuruRepository,
        cache: SnapshotCache,
        queue: MutationQueue,
        @ApplicationContext context: Context,
    ): OfflineRepository = OfflineRepository(network, cache, queue) {
        // Fire and forget, on a scope that outlives whichever screen made the
        // write: the user may well be leaving the app at that moment, and a
        // redraw cancelled by their own navigation is the widget going stale
        // for no reason. Failures are swallowed -- the launcher may have no
        // widget placed at all, and that is not an error.
        widgetScope.launch { runCatching { UpNextWidget().updateAll(context) } }
    }

    /**
     * Where a widget redraw runs.
     *
     * Deliberately not a `@Provides`: nothing else should get hold of a scope
     * with no lifecycle. It exists because updating a widget is suspending and
     * the thing that triggers it -- a write completing -- is not.
     */
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
