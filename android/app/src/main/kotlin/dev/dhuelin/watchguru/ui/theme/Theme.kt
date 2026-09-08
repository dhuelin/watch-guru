package dev.dhuelin.watchguru.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 theming.
 *
 * Dynamic colour is on by default from Android 12, which means the app
 * legitimately looks different on different devices and different from the iOS
 * app. That is the intended behaviour, not drift: see docs/DESIGN.md, which
 * shares colour *roles* between platforms rather than values.
 */

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F5BA9),
    onPrimary = Color.White,
    secondary = Color(0xFF585E71),
    tertiary = Color(0xFF2E6C4F),
    error = Color(0xFFBA1A1A),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE1E1EC),
    onSurfaceVariant = Color(0xFF44464F),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB4C4FF),
    onPrimary = Color(0xFF082978),
    secondary = Color(0xFFC0C6DC),
    tertiary = Color(0xFF93D5AE),
    error = Color(0xFFFFB4AB),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
)

@Composable
fun WatchGuruTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = WatchGuruTypography,
        content = content,
    )
}
