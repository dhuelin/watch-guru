package dev.huelin.watchguru.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 theming.
 *
 * The brand palette on every device. Dynamic colour used to replace it with
 * the wallpaper's from Android 12, which left most people never seeing the
 * violet at all and the app looking like a settings screen. Screens still use
 * roles, never values: see docs/DESIGN.md.
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
// The background and the surface containers are violet-tinted neutrals: the
// screen is the faintest tint of the brand, and cards sit on it one step
// lighter in light mode and one step lighter in dark, so a row reads as a
// thing rather than a line of text on a white page.
//
// Every pair this puts together -- onSurface on surface and on each
// container, onPrimary on primary, onPrimaryContainer on primaryContainer,
// primary on surface and the rest -- clears WCAG AA in both themes, the
// weakest being onSurfaceVariant on surfaceVariant in dark at 5.5:1.
internal val LightScheme = lightColorScheme(
    primary = Color(0xFF711CD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF25005A),
    secondary = Color(0xFF605A75),
    secondaryContainer = Color(0xFFE6DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF2E6C4F),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF7F2FA),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFF7F2FA),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE3E2E7),
    onSurfaceVariant = Color(0xFF484455),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDF8FF),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFFCCB7FF),
    onPrimary = Color(0xFF400089),
    primaryContainer = Color(0xFF5A1BB5),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFC7C4D3),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE6DEF8),
    tertiary = Color(0xFF93D5AE),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF110F15),
    onBackground = Color(0xFFE2E2E4),
    surface = Color(0xFF110F15),
    onSurface = Color(0xFFE2E2E4),
    surfaceVariant = Color(0xFF484455),
    onSurfaceVariant = Color(0xFFC7C5CF),
    surfaceContainerLowest = Color(0xFF0C0A10),
    surfaceContainerLow = Color(0xFF1D1B22),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
)

/**
 * The colour a card sits in: white on the tinted background in light mode,
 * a step up from it in dark. One role so every card agrees.
 */
val ColorScheme.card: Color
    get() = if (background.luminance() > 0.5f) surfaceContainerLowest else surfaceContainerHigh

@Composable
fun WatchGuruTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
