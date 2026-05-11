package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ThruSpark palette: earthy greens + dark backgrounds
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7CB87C),          // muted trail green
    onPrimary = Color(0xFF0A1A0A),
    primaryContainer = Color(0xFF1E3C1E),
    secondary = Color(0xFFB0AA7E),        // sand/khaki
    background = Color(0xFF0F1A0F),
    surface = Color(0xFF162016),
    error = Color(0xFFCF6679)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E6B2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8E6B8),
    secondary = Color(0xFF6B6532),
    background = Color(0xFFF4FBF4),
    surface = Color(0xFFE8F5E8),
    error = Color(0xFFB3261E)
)

@Composable
fun ThruSparkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
