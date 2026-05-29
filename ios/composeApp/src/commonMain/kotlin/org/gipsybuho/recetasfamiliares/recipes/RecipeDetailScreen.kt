package org.gipsybuho.recetasfamiliares.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.core.rememberHapticFeedback
import org.gipsybuho.recetasfamiliares.network.RecipeDto
import org.gipsybuho.recetasfamiliares.network.RecipeIngredientDto
import org.gipsybuho.recetasfamiliares.network.RecipeStepDto

@Composable
fun RecipeDetailScreen(
    recipe: RecipeDto,
    repository: RecipeRepository,
    onBack: () -> Unit
) {
    var ingredients by remember { mutableStateOf<List<RecipeIngredientDto>>(emptyList()) }
    var steps       by remember { mutableStateOf<List<RecipeStepDto>>(emptyList()) }
    var loading     by remember { mutableStateOf(true) }
    val haptic      = rememberHapticFeedback()

    LaunchedEffect(recipe.id) {
        runCatching {
            ingredients = repository.loadIngredients(recipe.id)
            steps       = repository.loadSteps(recipe.id)
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = { haptic.selection(); onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                recipe.title,
                style    = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                maxLines = 2
            )
        }
        HorizontalDivider()

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Meta chips
                val totalMin = (recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)
                val hasMeta  = totalMin > 0 || recipe.difficulty != null || recipe.servings != null
                if (hasMeta) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (totalMin > 0) DetailMetaChip("⏱ ${totalMin}m")
                            recipe.difficulty?.let { DetailMetaChip(difficultyLabel(it)) }
                            recipe.servings?.let   { DetailMetaChip("$it porciones") }
                        }
                    }
                }

                // Descripción
                recipe.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    item {
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sección ingredientes
                item {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (ingredients.isNotEmpty()) "Ingredientes (${ingredients.size})" else "Ingredientes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (ingredients.isEmpty()) {
                    item {
                        Text(
                            "Sin ingredientes registrados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    items(ingredients.sortedBy { it.position }, key = { it.id }) { ing ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("• ${ing.name}", style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            val qty = buildString {
                                ing.quantity?.let { append("%.1f".format(it)) }
                                ing.unit?.takeIf { it.isNotBlank() }?.let { append(" $it") }
                            }
                            if (qty.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text(qty, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                // Sección pasos
                item {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (steps.isNotEmpty()) "Preparación (${steps.size} pasos)" else "Preparación",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (steps.isEmpty()) {
                    item {
                        Text(
                            "Sin pasos registrados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    items(steps.sortedBy { it.position }, key = { it.id }) { step ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape    = CircleShape,
                                color    = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${step.position}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier            = Modifier.weight(1f)
                            ) {
                                Text(step.instruction, style = MaterialTheme.typography.bodyMedium)
                                step.timerMinutes?.let {
                                    Text(
                                        "⏱ $it min",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetaChip(label: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            label,
            style    = MaterialTheme.typography.labelMedium,
            color    = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun difficultyLabel(value: String): String = when (value.uppercase()) {
    "EASY"   -> "Fácil"
    "MEDIUM" -> "Media"
    "HARD"   -> "Difícil"
    else     -> value
}
