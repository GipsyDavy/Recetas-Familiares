package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.gipsybuho.recetasfamiliares.ui.theme.AppTheme
import org.gipsybuho.recetasfamiliares.ui.theme.ThemeMode
import org.gipsybuho.recetasfamiliares.ui.theme.darkColors
import org.gipsybuho.recetasfamiliares.ui.theme.lightColors

@Composable
internal fun ThemePickerDialog(
    currentTheme: AppTheme,
    currentMode: ThemeMode,
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit,
    onApply: (AppTheme, ThemeMode, Boolean) -> Unit,
) {
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var selectedMode by remember { mutableStateOf(currentMode) }
    var hapticsOn by remember { mutableStateOf(hapticsEnabled) }
    val systemDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 620.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Tema de la app", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Una atmósfera para cada momento en familia",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 136.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(
                            "Modo",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = selectedMode == mode,
                                    onClick = { selectedMode = mode },
                                    label = {
                                        Text(
                                            when (mode) {
                                                ThemeMode.LIGHT -> "Claro"
                                                ThemeMode.DARK -> "Oscuro"
                                                ThemeMode.SYSTEM -> "Sistema"
                                            }
                                        )
                                    },
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hápticos", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Respuesta táctil en acciones importantes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = hapticsOn, onCheckedChange = { hapticsOn = it })
                        }
                        Text(
                            "Colecciones",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }

                items(AppTheme.entries, key = { it.name }) { theme ->
                    val previewDark = theme.recommendedDark ||
                        selectedMode == ThemeMode.DARK ||
                        (selectedMode == ThemeMode.SYSTEM && systemDark)
                    ThemePreviewCard(
                        theme = theme,
                        selected = selectedTheme == theme,
                        previewDark = previewDark,
                        onClick = { selectedTheme = theme },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedTheme, selectedMode, hapticsOn) }) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun ThemePreviewCard(
    theme: AppTheme,
    selected: Boolean,
    previewDark: Boolean,
    onClick: () -> Unit,
) {
    val preview = if (previewDark) theme.darkColors() else theme.lightColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "themeCardScale_${theme.name}",
    )
    val tilt by animateFloatAsState(
        targetValue = if (pressed) 1.2f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "themeCardTilt_${theme.name}",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            pressed -> 2.dp
            selected -> 10.dp
            else -> 5.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "themeCardElevation_${theme.name}",
    )
    val shape = RoundedCornerShape(20.dp)
    val accessibilityText = buildString {
        append(theme.displayName)
        append(". ")
        append(theme.description)
        if (theme.isFeatured) append(". Tema principal")
        if (selected) append(". Seleccionado")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = elevation, shape = shape, clip = false)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = tilt
                translationY = if (pressed) 2.dp.toPx() else 0f
                cameraDistance = 18f * density
            }
            .clip(shape)
            .background(preview.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else preview.outlineVariant,
                shape = shape,
            )
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = accessibilityText },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .background(
                    Brush.linearGradient(
                        listOf(preview.primaryContainer, preview.primary, preview.tertiary)
                    )
                )
                .padding(Spacing.lg),
        ) {
            Text(
                theme.emoji,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            if (theme.isFeatured) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.42f),
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        "Principal",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }
            }
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = preview.surface.copy(alpha = 0.90f),
                    contentColor = preview.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(Spacing.lg),
        ) {
            Text(
                theme.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = preview.onSurface,
            )
            Text(
                theme.description,
                style = MaterialTheme.typography.bodySmall,
                color = preview.onSurfaceVariant,
            )
        }
    }
}
