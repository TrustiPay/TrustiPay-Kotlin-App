package app.trustipay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = TrustiPayPrimary,
    secondary = TrustiPaySecondary,
    tertiary = TrustiPayTertiary,
    background = TrustiPayBackground,
    onBackground = TrustiPayPrimary,
    surface = Color.White,
    onSurface = TrustiPayPrimary,
    primaryContainer = TrustiPayBackground,
    onPrimaryContainer = TrustiPayPrimary,
    secondaryContainer = Color(0xFFE6F4EF),
    onSecondaryContainer = TrustiPaySecondary,
    tertiaryContainer = Color(0xFFE3F7EA),
    onTertiaryContainer = TrustiPayPrimary,
    surfaceVariant = Color(0xFFF2F7F4),
    onSurfaceVariant = Color(0xFF66756F),
    outline = TrustiPaySecondary,
)

@Composable
fun TrustiPayTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
