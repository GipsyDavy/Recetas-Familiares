package org.gipsybuho.recetasfamiliares.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

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
    val targetColors = remember(appTheme, darkTheme) {
        if (darkTheme) appTheme.darkColors() else appTheme.lightColors()
    }
    // Cambiar claro <-> oscuro de golpe mantiene el contraste AA durante todo el
    // cambio. La interpolacion se reserva para temas dentro del mismo modo.
    val colorScheme = key(darkTheme) { animateColorScheme(targetColors) }
    val view = LocalView.current

    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        DisposableEffect(activity, darkTheme, targetColors.background, targetColors.surface) {
            activity?.let { applySystemBarTheme(it, view, darkTheme, targetColors) }
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content
    )
}

@Suppress("DEPRECATION")
private fun applySystemBarTheme(
    activity: Activity,
    view: android.view.View,
    darkTheme: Boolean,
    colors: ColorScheme,
) {
    val window = activity.window
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
    }
    // No cambia decorFitsSystemWindows: el modo cocina gestiona ese estado y
    // restaura las barras al salir. En API recientes estos colores pueden ser
    // ignorados por edge-to-edge, pero el contraste de iconos sigue correcto.
    window.statusBarColor = colors.background.toArgb()
    window.navigationBarColor = colors.surface.toArgb()
}

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val transition = updateTransition(targetState = target, label = "appColorScheme")
    @Composable
    fun color(
        label: String,
        selector: @Composable (ColorScheme) -> androidx.compose.ui.graphics.Color,
    ) = transition.animateColor(
        transitionSpec = { tween(durationMillis = 300) },
        label = label,
        targetValueByState = selector,
    ).value

    return target.copy(
        primary = color("primary") { it.primary },
        onPrimary = color("onPrimary") { it.onPrimary },
        primaryContainer = color("primaryContainer") { it.primaryContainer },
        onPrimaryContainer = color("onPrimaryContainer") { it.onPrimaryContainer },
        secondary = color("secondary") { it.secondary },
        onSecondary = color("onSecondary") { it.onSecondary },
        secondaryContainer = color("secondaryContainer") { it.secondaryContainer },
        onSecondaryContainer = color("onSecondaryContainer") { it.onSecondaryContainer },
        tertiary = color("tertiary") { it.tertiary },
        onTertiary = color("onTertiary") { it.onTertiary },
        tertiaryContainer = color("tertiaryContainer") { it.tertiaryContainer },
        onTertiaryContainer = color("onTertiaryContainer") { it.onTertiaryContainer },
        background = color("background") { it.background },
        onBackground = color("onBackground") { it.onBackground },
        surface = color("surface") { it.surface },
        onSurface = color("onSurface") { it.onSurface },
        surfaceVariant = color("surfaceVariant") { it.surfaceVariant },
        onSurfaceVariant = color("onSurfaceVariant") { it.onSurfaceVariant },
        surfaceTint = color("surfaceTint") { it.surfaceTint },
        outline = color("outline") { it.outline },
        outlineVariant = color("outlineVariant") { it.outlineVariant },
        error = color("error") { it.error },
        onError = color("onError") { it.onError },
        errorContainer = color("errorContainer") { it.errorContainer },
        onErrorContainer = color("onErrorContainer") { it.onErrorContainer },
        surfaceContainerLow = color("surfaceContainerLow") { it.surfaceContainerLow },
        surfaceContainer = color("surfaceContainer") { it.surfaceContainer },
        surfaceContainerHigh = color("surfaceContainerHigh") { it.surfaceContainerHigh },
        surfaceContainerHighest = color("surfaceContainerHighest") { it.surfaceContainerHighest },
    )
}
