package org.gipsybuho.recetasfamiliares.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3E5F45),
    secondary = Color(0xFF8C4A2F),
    tertiary = Color(0xFFF2B84B),
    background = Color(0xFFFFFBF6),
    surface = Color(0xFFFFFBF6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAED6B4),
    secondary = Color(0xFFE6B89C),
    tertiary = Color(0xFFF2C76B),
    background = Color(0xFF161915),
    surface = Color(0xFF1D211C)
)

@Composable
fun RecetasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
