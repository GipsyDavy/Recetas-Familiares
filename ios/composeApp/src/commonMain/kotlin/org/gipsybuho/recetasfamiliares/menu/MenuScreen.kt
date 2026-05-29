package org.gipsybuho.recetasfamiliares.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.network.MenuItemDto

@Composable
fun MenuScreen(repository: MenuRepository) {
    var menuItems by remember { mutableStateOf<List<MenuItemDto>>(emptyList()) }
    var loading   by remember { mutableStateOf(true) }
    var error     by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { menuItems = repository.loadCurrentWeek() }
            .onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Menú semanal", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            menuItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("Sin menú esta semana", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Planifica el menú desde Android o Desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            else -> {
                val byDate = menuItems.groupBy { it.plannedDate.take(10) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    byDate.forEach { (date, dayItems) ->
                        item(key = date) {
                            DayMenuCard(date = date, items = dayItems)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayMenuCard(date: String, items: List<MenuItemDto>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text  = formatDate(date),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(thickness = 0.5.dp)
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.defaultMinSize(minWidth = 72.dp)
                    ) {
                        Text(
                            mealTypeLabel(item.mealType),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        item.recipeTitle ?: item.note ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun formatDate(iso: String): String {
    if (iso.length < 10) return iso
    val parts = iso.split("-")
    if (parts.size < 3) return iso
    val day   = parts[2]
    val month = parts[1]
    return "$day/$month"
}

private fun mealTypeLabel(type: String) = when (type) {
    "BREAKFAST" -> "Desayuno"
    "LUNCH"     -> "Almuerzo"
    "SNACK"     -> "Merienda"
    "DINNER"    -> "Cena"
    else        -> type
}
