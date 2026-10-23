package dev.huelin.watchguru.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * Sizes are in sp throughout so the user's font-size setting is honoured; no
 * screen may hardcode a dp text size.
 */
val WatchGuruTypography = Typography().let { base ->
    // Heavier titles than Material's defaults: at Regular weight a screen's
    // name and a title's name are no louder than the metadata under them.
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * For anything that changes in place: a progress counter, an episode count, a
 * rating. Proportional digits shift sideways as the value goes from 9 to 10,
 * which reads as a glitch.
 */
val TabularNumbers = TextStyle(
    fontFamily = FontFamily.Default,
    fontFeatureSettings = "tnum",
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
)
