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

// Internal rather than private: the home-screen widget builds its own
// pre-Android-12 fallback from these same values, and a second copy of the
// palette is a second thing to forget to change.
//
// The violet is the app icon's, taken from the wordmark: #8B46F7, which is
// tone 51 of its own tonal palette. Every value below is a tone of that same
// hue -- primary at 40 and 80, neutrals at a twelfth of its chroma, the
// neutral variant at an eighth -- which is how Material builds a scheme and
// why these are not nine colours somebody picked one at a time.
//
// Tertiary stays green and error stays red. docs/DESIGN.md maps tertiary to
// *success* and error to *danger*; harmonising them toward the brand would
// make the two statuses agree with the icon and disagree with every other
// app on the device, which is the wrong trade for the one colour a user needs
// to read without thinking.
//
// Every pair this puts together -- onSurface on surface, onPrimary on
// primary, primary on surface and the rest -- clears WCAG AA in both themes,
// the weakest being onSurfaceVariant on surfaceVariant in dark at 5.5:1.
internal val LightScheme = lightColorScheme(
    primary = Color(0xFF711CD6),
    onPrimary = Color.White,
    secondary = Color(0xFF605A75),
    tertiary = Color(0xFF2E6C4F),
    error = Color(0xFFBA1A1A),
    surface = Color(0xFFF9F9F9),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE3E2E7),
    onSurfaceVariant = Color(0xFF484455),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFFCCB7FF),
    onPrimary = Color(0xFF400089),
    secondary = Color(0xFFC7C4D3),
    tertiary = Color(0xFF93D5AE),
    error = Color(0xFFFFB4AB),
    surface = Color(0xFF141316),
    onSurface = Color(0xFFE2E2E4),
    surfaceVariant = Color(0xFF484455),
    onSurfaceVariant = Color(0xFFC7C5CF),
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
