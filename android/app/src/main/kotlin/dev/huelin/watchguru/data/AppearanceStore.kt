package dev.huelin.watchguru.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class AppearanceStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(
        prefs.getString(KEY, null)
            ?.let { saved -> Appearance.entries.firstOrNull { it.name == saved } }
            ?: Appearance.SYSTEM,
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun set(appearance: Appearance) {
        prefs.edit().putString(KEY, appearance.name).apply()
        _appearance.value = appearance
    }

    private companion object {
        const val FILE_NAME = "watch_guru_settings"
        const val KEY = "appearance"
    }
}
