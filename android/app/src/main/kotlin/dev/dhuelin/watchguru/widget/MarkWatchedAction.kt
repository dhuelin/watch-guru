package dev.dhuelin.watchguru.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dev.dhuelin.watchguru.data.OfflineRepository.Written

/**
 * Marking the next episode watched, from the home screen.
 *
 * The whole point of the widget: one tap, no app. It goes through the same
 * repository the screens use, so it reaches the server when there is signal and
 * joins the replay queue when there is not -- and either way the widget
 * refreshes itself afterwards, which is what makes the next episode appear.
 *
 * `ActionCallback` runs in the app's process with a suspending body, so there is
 * no need for a service, a worker or a broadcast of our own.
 */
class MarkWatchedAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val episodeId = parameters[EpisodeId] ?: return
        val titleId = parameters[TitleId] ?: return

        val written = context.widgetDependencies().offline()
            .markEpisodeWatched(episodeId = episodeId, titleId = titleId)

        // Redrawn on success and on a queued write alike: both mean this
        // episode is done as far as the user is concerned, and leaving it on
        // screen is the widget disagreeing with the tap it just accepted.
        //
        // A rejected write is the one case to leave alone -- the episode really
        // is still next, and silently advancing would hide that.
        if (written is Written.Failed) return
        UpNextWidget().updateAll(context)
    }

    companion object {
        private val EpisodeId = ActionParameters.Key<Long>("episodeId")
        private val TitleId = ActionParameters.Key<Long>("titleId")

        /**
         * Both ids, because the queue needs them both.
         *
         * The episode is what gets marked; the title is what the pending entry
         * is collapsed on, so two taps on the same series leave one entry
         * rather than two.
         */
        fun parameters(entry: UpNextWidgetEntry) = actionParametersOf(
            EpisodeId to entry.episodeId,
            TitleId to entry.titleId,
        )
    }
}
