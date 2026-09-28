package dev.dhuelin.watchguru.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.OfflineRepository
import dev.dhuelin.watchguru.data.TokenStore

/**
 * What the widget draws.
 *
 * Three states, not a nullable list: signed out is not the same as an empty
 * library, and neither is the same as "we could not reach anything". A widget
 * that renders all three as a blank rectangle is the most common way this
 * feature is got wrong -- see the acceptance criteria on #20, which ask for a
 * prompt when signed out rather than an error.
 */
sealed interface UpNextWidgetState {
    data object SignedOut : UpNextWidgetState
    data object Empty : UpNextWidgetState
    data class Unavailable(val offline: Boolean) : UpNextWidgetState
    data class Ready(val entries: List<UpNextWidgetEntry>) : UpNextWidgetState
}

/**
 * One row.
 *
 * A widget-shaped copy of the API model rather than the model itself: what the
 * widget needs is six fields, and depending on the generated type here would
 * mean every regeneration of the client is a change to the widget's contract
 * with itself.
 */
data class UpNextWidgetEntry(
    val titleId: Long,
    val episodeId: Long,
    val title: String,
    val episodeCode: String,
    val episodeName: String?,
    val watchedEpisodes: Int,
    val airedEpisodes: Int,
)

/**
 * The app's own singletons, reached from a widget.
 *
 * A Glance widget runs inside this same application, so it can use the very
 * repository the screens use -- which is why nothing here fetches or caches
 * anything of its own. Hilt cannot inject a `GlanceAppWidget` (it is not an
 * Android component with a lifecycle Hilt hooks), so the graph is entered by
 * hand through this door rather than with `@AndroidEntryPoint`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDependencies {
    fun offline(): OfflineRepository
    fun tokens(): TokenStore
}

fun Context.widgetDependencies(): WidgetDependencies =
    EntryPointAccessors.fromApplication(applicationContext, WidgetDependencies::class.java)

/**
 * Loads what the widget shows, at most [limit] rows.
 *
 * Signed-in is decided by the presence of a session rather than by a 401 from
 * the server: asking first costs a request that is certain to fail, and on a
 * home screen that request happens every time the widget refreshes.
 */
suspend fun loadUpNextWidgetState(context: Context, limit: Int): UpNextWidgetState {
    val dependencies = context.widgetDependencies()
    if (dependencies.tokens().tokens() == null) return UpNextWidgetState.SignedOut

    return when (val result = dependencies.offline().upNext(limit = limit)) {
        is ApiResult.Success -> result.value
            .map { entry ->
                UpNextWidgetEntry(
                    titleId = entry.titleId,
                    episodeId = entry.nextEpisodeId,
                    title = entry.primaryTitle,
                    episodeCode = entry.nextEpisodeCode,
                    episodeName = entry.nextEpisodeName,
                    watchedEpisodes = entry.watchedEpisodes,
                    airedEpisodes = entry.airedEpisodes,
                )
            }
            .let { if (it.isEmpty()) UpNextWidgetState.Empty else UpNextWidgetState.Ready(it) }

        ApiResult.Failure.Offline -> UpNextWidgetState.Unavailable(offline = true)
        // Including Unauthorised: the session was refused rather than absent,
        // and telling somebody to sign in when they are signed in and the
        // server is unhappy sends them somewhere that will not help.
        is ApiResult.Failure -> UpNextWidgetState.Unavailable(offline = false)
    }
}
