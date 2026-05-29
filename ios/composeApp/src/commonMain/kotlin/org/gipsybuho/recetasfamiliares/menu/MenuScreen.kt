package org.gipsybuho.recetasfamiliares.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*
import org.gipsybuho.recetasfamiliares.Spacing
import org.gipsybuho.recetasfamiliares.network.MenuItemDto
import org.gipsybuho.recetasfamiliares.recipes.AnimatedEmptyState

@Composable
fun MenuScreen(repository: MenuRepository) {
    var allItems    by remember { mutableStateOf<List<MenuItemDto>>(emptyList()) }
    var loading     by remember { mutableStateOf(true) }
    var error       by remember { mutableStateOf<String?>(null) }
    var weekOffset  by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching { allItems = repository.loadAllItems() }
            .onFailure { error = it.message }
        loading = false
    }

    // Calcular lunes de la semana actual + offset
    val today        = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val daysToMonday = today.dayOfWeek.isoDayNumber - 1
    val thisMonday   = today.minus(DatePeriod(days = daysToMonday))
    val weekStart    = thisMonday.plus(DatePeriod(days = weekOffset * 7))
    val weekEnd      = weekStart.plus(DatePeriod(days = 6))

    val weekItems = allItems.filter { item ->
        val date = item.plannedDate.take(10)
        date >= weekStart.toString() && date <= weekEnd.toString()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Header navegación semanas
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(onClick = { weekOffset-- }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Semana anterior")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Menú semanal", style = MaterialTheme.typography.headlineSmall)
                Text(
                    weekLabel(weekOffset),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { weekOffset++ }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Semana siguiente")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "${formatDate(weekStart.toString())} – ${formatDate(weekEnd.toString())}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            weekItems.isEmpty() -> AnimatedEmptyState(
                icon     = "📅",
                title    = "Sin menú esta semana",
                subtitle = "Planifica el menú desde Android o Desktop"
            )
            else -> {
                val byDate = weekItems.groupBy { it.plannedDate.take(10) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    byDate.entries.sortedBy { it.key }.forEach { (date, dayItems) ->
                        item(key = date) {
                            DayMenuCard(
                                date    = date,
                                items   = dayItems,
                                isToday = date == today.toString()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayMenuCard(date: String, items: List<MenuItemDto>, isToday: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isToday) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                 else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text  = formatDate(date),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (isToday) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text("Hoy",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp)
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Surface(
                        shape    = MaterialTheme.shapes.extraSmall,
                        color    = MaterialTheme.colorScheme.secondaryContainer,
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
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun weekLabel(offset: Int) = when (offset) {
    0    -> "Esta semana"
    -1   -> "Semana pasada"
    1    -> "Próxima semana"
    else -> if (offset < 0) "Hace ${-offset} semanas" else "En $offset semanas"
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
