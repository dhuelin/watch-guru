package dev.huelin.watchguru.di

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.huelin.watchguru.BuildConfig
import dev.huelin.watchguru.data.CoilDataCleaner
import dev.huelin.watchguru.data.GoogleSignIn
import dev.huelin.watchguru.data.LocalDataCleaner
import dev.huelin.watchguru.data.MyServices
import dev.huelin.watchguru.data.OfflineRepository
import dev.huelin.watchguru.data.SessionEvents
import dev.huelin.watchguru.data.WatchGuruRepository
import dev.huelin.watchguru.data.offline.AndroidFileStore
import dev.huelin.watchguru.data.offline.FileStore
import dev.huelin.watchguru.data.offline.MutationQueue
import dev.huelin.watchguru.data.offline.SnapshotCache
import dev.huelin.watchguru.widget.UpNextWidget
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
        myServices: MyServices,
    ): LocalDataCleaner = object : LocalDataCleaner {
        private val images = CoilDataCleaner(context)

        override suspend fun clear() {
            // All three. The cached library is the previous user's watch
            // history in all but name, the queue may still hold changes that
            // would otherwise be replayed against whoever signs in next --
            // attributing one person's viewing to another's account -- and
            // which services somebody subscribes to is a fact about them, not
            // about the phone.
            offline.clearLocalData()
            myServices.clear()
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
