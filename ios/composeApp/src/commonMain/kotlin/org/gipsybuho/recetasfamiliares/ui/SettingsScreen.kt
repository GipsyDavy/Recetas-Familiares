package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.theme.AppTheme
import org.gipsybuho.recetasfamiliares.theme.ThemeMode
import org.gipsybuho.recetasfamiliares.theme.lightColors

@Composable
fun SettingsScreen(
    selectedTheme: AppTheme,
    themeMode: ThemeMode,
    onThemeChange: (AppTheme) -> Unit,
    onModeChange: (ThemeMode) -> Unit,
    onLogout: () -> Unit,
    session: SessionStore? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 8.dp))

        if (session != null) {
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    val initials = session.displayName?.take(2)?.uppercase()
                    if (!initials.isNullOrBlank()) {
                        Text(initials, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        Icon(Icons.Outlined.Person, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp))
                    }
                }
                Column {
                    Text(session.displayName ?: "—", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(session.email ?: "—", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("Modo de pantalla", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick  = { onModeChange(mode) },
                    label    = {
                        Text(when (mode) {
                            ThemeMode.LIGHT  -> "Claro"
                            ThemeMode.DARK   -> "Oscuro"
                            ThemeMode.SYSTEM -> "Sistema"
                        })
                    }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Color del tema", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AppTheme.entries) { theme ->
                ThemeSwatchItem(
                    theme    = theme,
                    selected = selectedTheme == theme,
                    onClick  = { onThemeChange(theme) }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            "Cerrar sesión",
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLogout)
                .padding(vertical = 12.dp)
        )
    }
}

@Composable
private fun ThemeSwatchItem(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val lc = theme.lightColors()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(lc.primaryContainer)
                .let { m -> if (selected) m.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else m }
        ) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(lc.primary)
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint     = lc.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            theme.displayName,
            style    = MaterialTheme.typography.labelSmall,
            color    = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
