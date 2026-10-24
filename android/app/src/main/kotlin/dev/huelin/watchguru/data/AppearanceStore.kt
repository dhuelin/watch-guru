package dev.huelin.watchguru.data

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.huelin.watchguru.widget.UpNextWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Light, dark, or whatever the phone is set to. */
enum class Appearance { SYSTEM, LIGHT, DARK }

/**
 * The appearance chosen in Profile, kept on this device.
 *
 * Per device rather than on the account: it is about the screen in front of
 * you. Plain preferences, unlike the token store, because nothing here is a
 * secret, and read synchronously on creation so the first frame is already in
 * the chosen theme rather than flashing the system one.
 */
@Singleton
class AppearanceStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _appearance = MutableStateFlow(
        prefs.getString(KEY, null)
            ?.let { saved -> Appearance.entries.firstOrNull { it.name == saved } }
            ?: Appearance.SYSTEM,
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    /**
     * Whether the app and its widget take the wallpaper's colours instead of
     * the brand's, on Android 12 and up. Off unless chosen: the violet is
     * Watch Guru's, and with wallpaper colours most people never saw it.
     */
    private val _wallpaperColours = MutableStateFlow(prefs.getBoolean(KEY_WALLPAPER, false))
    val wallpaperColours: StateFlow<Boolean> = _wallpaperColours.asStateFlow()

    init {
        // Again on every start, so a choice made before this existed still
        // reaches the system.
        applyNightMode(_appearance.value)
    }

    fun set(appearance: Appearance) {
        prefs.edit().putString(KEY, appearance.name).apply()
        _appearance.value = appearance
        applyNightMode(appearance)
    }

    fun setWallpaperColours(on: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER, on).apply()
        _wallpaperColours.value = on
        // The widget follows the same choice. Redrawn on a scope of its own,
        // not the screen's, so leaving Profile straight away does not cancel
        // it; a launcher with no widget placed is not an error.
        widgetScope.launch { runCatching { UpNextWidget().updateAll(context) } }
    }

    /**
     * Tells Android 12+ the app's own night mode, which the system keeps and
     * applies before the app runs: the splash screen and the window behind
     * the first frame follow the choice instead of the phone's setting.
     * Compose still themes from [appearance]; this only covers what is drawn
     * before it.
     */
    private fun applyNightMode(appearance: Appearance) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
            when (appearance) {
                Appearance.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                Appearance.LIGHT -> UiModeManager.MODE_NIGHT_NO
                Appearance.DARK -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    private companion object {
        const val FILE_NAME = "watch_guru_settings"
        const val KEY = "appearance"
        const val KEY_WALLPAPER = "wallpaper_colours"
    }
}
