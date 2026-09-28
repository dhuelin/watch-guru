package dev.dhuelin.watchguru.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.dhuelin.watchguru.MainActivity
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.ui.theme.DarkScheme
import dev.dhuelin.watchguru.ui.theme.LightScheme

/**
 * What to watch next, on the home screen.
 *
 * The widget is a view onto the same [OfflineRepository][
 * dev.dhuelin.watchguru.data.OfflineRepository] the screens use, so it shows
 * what the app shows and a mark made here takes the same route to the server --
 * including the queue, when there is no signal. Nothing about the network,
 * caching or replay is reimplemented here; a widget with its own client is a
 * second app that disagrees with the first one.
 *
 * Text only, no artwork. Glance cannot render a URL -- an image has to be
 * fetched, decoded and handed over as a bitmap -- and doing that inside a
 * widget's memory budget is a change of its own. The series, the episode and
 * the progress are what the widget is *for*; see #20, which still wants the
 * poster.
 */
class UpNextWidget : GlanceAppWidget() {

    /**
     * Three sizes rather than one that stretches.
     *
     * `Responsive` asks for the content once per size and lets the launcher
     * pick, which is what makes resizing instant instead of a round trip
     * through `provideGlance`.
     */
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(140.dp, 100.dp),  // small: one episode
            DpSize(250.dp, 100.dp),  // medium: one episode, markable
            DpSize(250.dp, 250.dp),  // large: several
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Loaded before provideContent, not inside it: the content lambda is
        // recomposed and must stay free of suspending work.
        val state = loadUpNextWidgetState(context, limit = MaximumRows)

        provideContent {
            GlanceTheme(colors = widgetColors()) {
                Content(state)
            }
        }
    }

    /**
     * Material 3, from the wallpaper where the system offers it.
     *
     * Glance's own default already does this on Android 12 and up. What it
     * cannot do is fall back to *this* app's palette below that, so the
     * fallback is the app's own scheme -- otherwise the widget on an Android 11
     * phone wears Glance's baseline colours and looks like it belongs to
     * something else.
     */
    @Composable
    private fun widgetColors() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceTheme.colors
        } else {
            ColorProviders(light = LightScheme, dark = DarkScheme)
        }

    @Composable
    private fun Content(state: UpNextWidgetState) {
        val wide = LocalSize.current.width >= 200.dp
        val tall = LocalSize.current.height >= 180.dp

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(16.dp)
                .padding(12.dp)
                // The whole widget opens the app, so the empty and error states
                // are a way in rather than a dead end.
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top,
        ) {
            when (state) {
                is UpNextWidgetState.SignedOut -> Message(R.string.widget_signed_out)
                is UpNextWidgetState.Empty -> Message(R.string.widget_nothing_next)
                is UpNextWidgetState.Unavailable -> Message(
                    if (state.offline) R.string.widget_offline else R.string.widget_unavailable
                )

                is UpNextWidgetState.Ready -> if (tall) {
                    Heading()
                    state.entries.take(MaximumRows).forEachIndexed { index, entry ->
                        if (index > 0) Spacer(GlanceModifier.height(10.dp))
                        EpisodeRow(entry, markable = true, compact = true)
                    }
                } else {
                    EpisodeRow(state.entries.first(), markable = wide, compact = false)
                }
            }
        }
    }

    @Composable
    private fun Heading() {
        Text(
            text = LocalContext.current.getString(R.string.widget_up_next),
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.padding(bottom = 8.dp),
        )
    }

    /**
     * One series and the episode it is waiting on.
     *
     * The mark button is a `Box` rather than Glance's `Button`, so the tap
     * target is the whole pill and its colours come from the theme above rather
     * than from a component with its own idea of them.
     */
    @Composable
    private fun EpisodeRow(entry: UpNextWidgetEntry, markable: Boolean, compact: Boolean) {
        val context = LocalContext.current

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = entry.title,
                    maxLines = if (compact) 1 else 2,
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = if (compact) 14.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = episodeLine(context, entry),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
                if (!compact) {
                    Text(
                        text = context.getString(
                            R.string.cd_progress, entry.watchedEpisodes, entry.airedEpisodes,
                        ),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                    )
                }
            }

            if (markable) {
                Spacer(GlanceModifier.width(8.dp))
                MarkWatchedButton(entry)
            }
        }
    }

    @Composable
    private fun MarkWatchedButton(entry: UpNextWidgetEntry) {
        Box(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primary)
                .cornerRadius(14.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(
                    actionRunCallback<MarkWatchedAction>(
                        MarkWatchedAction.parameters(entry),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = LocalContext.current.getString(R.string.widget_mark_watched),
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }

    @Composable
    private fun Message(resource: Int) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = LocalContext.current.getString(resource),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                ),
            )
        }
    }

    private companion object {
        /** What the large size shows, and so what is worth loading at all. */
        const val MaximumRows = 4
    }
}

/** `S2E5 · Episode name`, or just the code when the name is unknown. */
internal fun episodeLine(context: Context, entry: UpNextWidgetEntry): String =
    entry.episodeName
        ?.let { context.getString(R.string.widget_episode_named, entry.episodeCode, it) }
        ?: entry.episodeCode
