package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.data.local.MenuItemEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    menuItems: List<MenuItemEntity>,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    var weekOffset by remember { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val weekStart = today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
    val weekEnd = weekStart.plusDays(6)
    val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))

    val weekItems = menuItems.filter { item ->
        runCatching {
            val d = LocalDate.parse(item.plannedDate.substring(0, 10))
            !d.isBefore(weekStart) && !d.isAfter(weekEnd)
        }.getOrDefault(false)
    }.sortedBy { it.plannedDate }

    val byDay = weekItems.groupBy { it.plannedDate.substring(0, 10) }

    val weekRangeLabel = "${weekStart.format(dateFmt)} – ${weekEnd.format(dateFmt)} ${weekEnd.year}"
    val weekTitle = when {
        weekOffset == 0  -> "Esta semana"
        weekOffset == -1 -> "Semana pasada"
        weekOffset < -1  -> "Hace ${-weekOffset} semanas"
        weekOffset == 1  -> "Próxima semana"
        else             -> "En $weekOffset semanas"
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text("Menú semanal", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { weekOffset-- }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Semana anterior"
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(weekTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            weekRangeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { weekOffset++ }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Semana siguiente"
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }

            if (weekItems.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text("Sin menú esta semana", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Planifica comidas desde el escritorio o sincroniza para ver el menú",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                items(7) { dayIndex ->
                    val date = weekStart.plusDays(dayIndex.toLong())
                    val dateKey = date.toString()
                    val dayName = date.dayOfWeek
                        .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"))
                        .replaceFirstChar { it.uppercase() }
                    DayMenuCard(
                        date = "$dayName, ${date.format(dateFmt)}",
                        items = byDay[dateKey] ?: emptyList(),
                        isToday = date == today
                    )
                }
            }
        }
    }
}

@Composable
private fun DayMenuCard(date: String, items: List<MenuItemEntity>, isToday: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isToday) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                )
                if (isToday) MetaChip("Hoy")
            }
            if (items.isEmpty()) {
                Text(
                    text = "Sin comidas planificadas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = Spacing.xxs)
                )
            } else {
                Spacer(Modifier.height(Spacing.xs))
                items.forEach { item -> MealRow(item) }
            }
        }
    }
}

@Composable
private fun MealRow(item: MenuItemEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = mealTypeLabel(item.mealType),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = item.recipeTitle ?: "(sin título)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun mealTypeLabel(type: String): String = when (type.uppercase()) {
    "BREAKFAST" -> "Desayuno"
    "LUNCH"     -> "Comida"
    "DINNER"    -> "Cena"
    "SNACK"     -> "Merienda"
    else        -> type.lowercase().replaceFirstChar { it.uppercase() }
}
