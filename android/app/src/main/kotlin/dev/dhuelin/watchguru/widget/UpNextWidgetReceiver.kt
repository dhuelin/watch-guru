package dev.dhuelin.watchguru.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The manifest's way in to [UpNextWidget].
 *
 * Nothing but the wiring: Glance's receiver handles every broadcast the
 * framework sends a widget, and the interesting parts live in the widget and
 * its action.
 */
class UpNextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = UpNextWidget()
}
