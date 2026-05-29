package org.gipsybuho.recetasfamiliares.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.core.rememberHapticFeedback
import org.gipsybuho.recetasfamiliares.network.RecipeDto

@Composable
fun RecipeListScreen(repository: RecipeRepository) {
    var recipes by remember { mutableStateOf<List<RecipeDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }
    val haptic  = rememberHapticFeedback()

    LaunchedEffect(Unit) {
        runCatching { recipes = repository.loadRecipes() }
            .onFailure { error = it.message }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Recetas", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            recipes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sin recetas aún", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Añade recetas desde Android o Desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recipes, key = { it.id }) { recipe ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { haptic.selection() }) {
                        ListItem(
                            headlineContent    = { Text(recipe.title) },
                            supportingContent  = { Text(recipe.description ?: "Sin descripción") },
                            trailingContent    = recipe.difficulty?.let { d ->
                                { Text(difficultyLabel(d),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary) }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun difficultyLabel(value: String): String = when (value.uppercase()) {
    "EASY"   -> "Fácil"
    "MEDIUM" -> "Media"
    "HARD"   -> "Difícil"
    else     -> value
}
