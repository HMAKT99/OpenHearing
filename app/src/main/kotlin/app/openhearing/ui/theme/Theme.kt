package app.openhearing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// This app is for people with hearing difficulty; type scales with the system
// font size automatically, and a high-contrast palette is offered for low vision.
private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

// Maximum-contrast palette (near-black on white / white on near-black).
private val HighContrastLight =
    lightColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
    )
private val HighContrastDark =
    darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
    )

@Composable
fun OpenHearingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors =
        when {
            highContrast && darkTheme -> HighContrastDark
            highContrast -> HighContrastLight
            darkTheme -> DarkColors
            else -> LightColors
        }
    MaterialTheme(colorScheme = colors, content = content)
}
