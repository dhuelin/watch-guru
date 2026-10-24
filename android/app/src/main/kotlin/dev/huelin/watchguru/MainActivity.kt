package dev.huelin.watchguru

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.huelin.watchguru.data.Appearance
import dev.huelin.watchguru.data.AppearanceStore
import dev.huelin.watchguru.ui.WatchGuruApp
import dev.huelin.watchguru.ui.theme.WatchGuruTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appearanceStore: AppearanceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge before setContent, so the first frame already accounts
        // for the system bars rather than shifting once insets arrive.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by appearanceStore.appearance.collectAsStateWithLifecycle()
            val wallpaperColours by appearanceStore.wallpaperColours.collectAsStateWithLifecycle()
            val darkTheme = when (appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }

            // Again whenever the theme changes: by default the bar icons
            // follow the system, which leaves dark icons on a dark app when
            // the phone is light and the app was set to dark.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LIGHT_SCRIM,
                        DARK_SCRIM,
                    ) { darkTheme },
                )
                onDispose {}
            }

            WatchGuruTheme(darkTheme = darkTheme, dynamicColor = wallpaperColours) {
                WatchGuruApp()
            }
        }
    }

    private companion object {
        // The scrims enableEdgeToEdge uses by default for three-button
        // navigation, which it does not expose.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
