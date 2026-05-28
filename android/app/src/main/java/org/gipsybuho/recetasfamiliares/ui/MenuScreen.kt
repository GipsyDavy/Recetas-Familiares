package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.gipsybuho.recetasfamiliares.data.local.MenuItemEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    menuItems: List<MenuItemEntity>,
    recipes: List<RecipeEntity>,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onAssignToMenu: (date: String, recipeId: String, mealType: String) -> Unit = { _, _, _ -> },
    onRemoveFromMenu: (MenuItemEntity) -> Unit = {},
    onNavigateToRecipe: (String) -> Unit = {}
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

    var assignToDate by remember { mutableStateOf<String?>(null) }
    var assignToDateLabel by remember { mutableStateOf("") }
    var optionsForItem by remember { mutableStateOf<MenuItemEntity?>(null) }

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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Semana anterior")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(weekTitle, style = MaterialTheme.typography.titleMedium)
                        Text(weekRangeLabel, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = { weekOffset++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Semana siguiente")
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }

            items(7) { dayIndex ->
                val date = weekStart.plusDays(dayIndex.toLong())
                val dateKey = date.toString()
                val dayName = date.dayOfWeek
                    .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"))
                    .replaceFirstChar { it.uppercase() }
                DayMenuCard(
                    date = "$dayName, ${date.format(dateFmt)}",
                    dayItems = byDay[dateKey] ?: emptyList(),
                    isToday = date == today,
                    onAssign = {
                        assignToDate = dateKey
                        assignToDateLabel = "$dayName, ${date.format(dateFmt)}"
                    },
                    onMealTap = { optionsForItem = it }
                )
            }

            if (weekItems.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                            modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "Sin comidas esta semana. Toca «+» en cualquier día para añadir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    assignToDate?.let { date ->
        AssignMenuDialog(
            dateLabel = assignToDateLabel,
            recipes = recipes,
            onConfirm = { recipeId, mealType ->
                onAssignToMenu(date, recipeId, mealType)
                assignToDate = null
            },
            onDismiss = { assignToDate = null }
        )
    }

    optionsForItem?.let { item ->
        AlertDialog(
            onDismissRequest = { optionsForItem = null },
            title = { Text(item.recipeTitle ?: mealTypeLabel(item.mealType)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (item.recipeId != null) {
                        TextButton(
                            onClick = { onNavigateToRecipe(item.recipeId); optionsForItem = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ver receta") }
                    }
                    TextButton(
                        onClick = { onRemoveFromMenu(item); optionsForItem = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Eliminar del menú") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { optionsForItem = null }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun DayMenuCard(
    date: String,
    dayItems: List<MenuItemEntity>,
    isToday: Boolean,
    onAssign: () -> Unit,
    onMealTap: (MenuItemEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isToday) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                 else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (isToday) MetaChip("Hoy")
                }
                IconButton(onClick = onAssign, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Añadir comida",
                        modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (dayItems.isEmpty()) {
                Text(
                    text = "Sin comidas planificadas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = Spacing.xxs)
                )
            } else {
                Spacer(Modifier.height(Spacing.xs))
                dayItems.forEach { item -> MealRow(item, onClick = { onMealTap(item) }) }
            }
        }
    }
}

@Composable
private fun MealRow(item: MenuItemEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        if (item.recipeId != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun AssignMenuDialog(
    dateLabel: String,
    recipes: List<RecipeEntity>,
    onConfirm: (recipeId: String, mealType: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var mealType by remember { mutableStateOf("LUNCH") }
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, recipes) {
        if (query.isBlank()) recipes.take(20)
        else recipes.filter { it.title.contains(query, ignoreCase = true) }.take(15)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(Spacing.xl)) {
                Text("Añadir al menú", style = MaterialTheme.typography.titleLarge)
                Text(dateLabel, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(Spacing.md))

                Text("Tipo de comida", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    listOf("BREAKFAST" to "Desayuno", "LUNCH" to "Comida",
                           "DINNER" to "Cena", "SNACK" to "Merienda").forEach { (value, label) ->
                        FilterChip(
                            selected = mealType == value,
                            onClick = { mealType = value },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Buscar receta...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.xs))

                LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                    if (filtered.isEmpty()) {
                        item {
                            Text("Sin recetas", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(Spacing.sm))
                        }
                    } else {
                        items(filtered, key = { it.id }) { recipe ->
                            ListItem(
                                headlineContent = { Text(recipe.title, style = MaterialTheme.typography.bodyMedium) },
                                trailingContent = if (selectedRecipe?.id == recipe.id) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                                modifier = Modifier.clickable { selectedRecipe = recipe }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(Spacing.sm))
                    Button(
                        onClick = { selectedRecipe?.let { onConfirm(it.id, mealType) } },
                        enabled = selectedRecipe != null
                    ) { Text("Añadir") }
                }
            }
        }
    }
}

private fun mealTypeLabel(type: String): String = when (type.uppercase()) {
    "BREAKFAST" -> "Desayuno"
    "LUNCH"     -> "Comida"
    "DINNER"    -> "Cena"
    "SNACK"     -> "Merienda"
    else        -> type.lowercase().replaceFirstChar { it.uppercase() }
}
