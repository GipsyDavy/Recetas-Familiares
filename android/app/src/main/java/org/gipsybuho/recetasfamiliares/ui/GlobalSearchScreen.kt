package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity

@Composable
internal fun GlobalSearchScreen(
    query: String,
    recipes: List<RecipeEntity>,
    stockItems: List<StockItemEntity>,
    notes: List<FamilyNoteEntity>,
    modifier: Modifier,
    onNavigate: (MainTab) -> Unit
) {
    val lower = query.lowercase()
    val filteredRecipes = recipes.filter {
        it.title.contains(lower, ignoreCase = true) ||
        it.description?.contains(lower, ignoreCase = true) == true
    }
    val filteredStock = stockItems.filter { it.name.contains(lower, ignoreCase = true) }
    val filteredNotes = notes.filter {
        it.title.contains(lower, ignoreCase = true) ||
        it.body.contains(lower, ignoreCase = true)
    }

    if (filteredRecipes.isEmpty() && filteredStock.isEmpty() && filteredNotes.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Sin resultados para \"$query\"",
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Spacing.xxl)
            )
        }
        return
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        if (filteredRecipes.isNotEmpty()) {
            item { SearchSectionHeader("Recetas", filteredRecipes.size) }
            items(filteredRecipes, key = { "r_${it.id}" }) { recipe ->
                SearchResultRow(
                    title = recipe.title,
                    meta = listOfNotNull(
                        recipe.prepMinutes?.let { "$it min" },
                        recipe.difficulty
                    ).joinToString("  ·  "),
                    onClick = { onNavigate(MainTab.RECIPES) }
                )
            }
        }

        if (filteredStock.isNotEmpty()) {
            item { SearchSectionHeader("Stock", filteredStock.size) }
            items(filteredStock, key = { "s_${it.id}" }) { item ->
                val qty = buildString {
                    item.quantity?.let { append(it.toBigDecimal().stripTrailingZeros().toPlainString()) }
                    item.unit?.takeIf { it.isNotBlank() }?.let { append(" $it") }
                }
                SearchResultRow(title = item.name, meta = qty, onClick = { onNavigate(MainTab.STOCK) })
            }
        }

        if (filteredNotes.isNotEmpty()) {
            item { SearchSectionHeader("Notas", filteredNotes.size) }
            items(filteredNotes, key = { "n_${it.id}" }) { note ->
                val preview = note.body.take(80).replace('\n', ' ')
                SearchResultRow(
                    title = note.title.ifBlank { "Sin título" },
                    meta = if (note.body.length > 80) "$preview…" else preview,
                    onClick = { onNavigate(MainTab.NOTES) }
                )
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(label: String, count: Int) {
    Column {
        HorizontalDivider()
        Text(
            "$label  ($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.xs)
        )
    }
}

@Composable
private fun SearchResultRow(title: String, meta: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (meta.isNotBlank()) {
            { Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}
