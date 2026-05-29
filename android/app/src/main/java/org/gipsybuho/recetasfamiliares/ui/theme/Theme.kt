package org.gipsybuho.recetasfamiliares.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun RecetasTheme(
    appTheme: AppTheme = AppTheme.BOSQUE,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) appTheme.darkColors() else appTheme.lightColors(),
        typography  = AppTypography,
        content     = content
    )
}
