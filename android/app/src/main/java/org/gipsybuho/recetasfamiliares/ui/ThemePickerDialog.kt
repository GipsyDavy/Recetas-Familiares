package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.ui.theme.AppTheme
import org.gipsybuho.recetasfamiliares.ui.theme.ThemeMode
import org.gipsybuho.recetasfamiliares.ui.theme.lightColors

@Composable
internal fun ThemePickerDialog(
    currentTheme: AppTheme,
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onApply: (AppTheme, ThemeMode) -> Unit
) {
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var selectedMode  by remember { mutableStateOf(currentMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tema de la app", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Text("Modo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick  = { selectedMode = mode },
                            label = {
                                Text(when (mode) {
                                    ThemeMode.LIGHT  -> "Claro"
                                    ThemeMode.DARK   -> "Oscuro"
                                    ThemeMode.SYSTEM -> "Sistema"
                                })
                            }
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text("Color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AppTheme.entries) { theme ->
                        ThemeSwatchItem(
                            theme    = theme,
                            selected = selectedTheme == theme,
                            onClick  = { selectedTheme = theme }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedTheme, selectedMode) }) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ThemeSwatchItem(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val lightColors = theme.lightColors()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(lightColors.primaryContainer)
                .let { m -> if (selected) m.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else m }
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(lightColors.primary)
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint  = lightColors.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            theme.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
