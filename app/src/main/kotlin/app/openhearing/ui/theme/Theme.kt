package app.openhearing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// This app is for people with hearing difficulty; the palette favours high
// contrast. Full accessibility theming (large type, dynamic color, contrast
// options) is built out in Phase 4.
private val LightColors =
    lightColorScheme()

private val DarkColors =
    darkColorScheme()

@Composable
fun OpenHearingTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
