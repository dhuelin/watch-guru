package dev.dhuelin.watchguru.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontFeature
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * Sizes are in sp throughout so the user's font-size setting is honoured; no
 * screen may hardcode a dp text size.
 */
val WatchGuruTypography = Typography()

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
