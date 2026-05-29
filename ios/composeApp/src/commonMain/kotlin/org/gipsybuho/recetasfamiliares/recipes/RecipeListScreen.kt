package org.gipsybuho.recetasfamiliares.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.cooking.CookingScreen
import org.gipsybuho.recetasfamiliares.core.rememberHapticFeedback
import org.gipsybuho.recetasfamiliares.network.RecipeDto
import org.gipsybuho.recetasfamiliares.sync.SyncRepository

@Composable
fun RecipeListScreen(repository: RecipeRepository, syncRepo: SyncRepository) {
    var recipes        by remember { mutableStateOf<List<RecipeDto>>(emptyList()) }
    var loading        by remember { mutableStateOf(true) }
    var isRefreshing   by remember { mutableStateOf(false) }
    var error          by remember { mutableStateOf<String?>(null) }
    var selectedRecipe by remember { mutableStateOf<RecipeDto?>(null) }
    var cookingMode    by remember { mutableStateOf(false) }
    val haptic         = rememberHapticFeedback()
    val scope          = rememberCoroutineScope()

    suspend fun loadData() {
        runCatching { recipes = repository.loadRecipes() }
            .onFailure { error = it.message }
    }

    LaunchedEffect(Unit) {
        loadData()
        loading = false
    }

    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            error = null
            syncRepo.pullIncremental()
            loadData()
            isRefreshing = false
        }
    }

    if (cookingMode && selectedRecipe != null) {
        CookingScreen(
            recipe     = selectedRecipe!!,
            repository = repository,
            onExit     = { cookingMode = false }
        )
        return
    }

    if (selectedRecipe != null) {
        RecipeDetailScreen(
            recipe        = selectedRecipe!!,
            repository    = repository,
            onBack        = { selectedRecipe = null },
            onCookingMode = { cookingMode = true }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recetas", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { if (!isRefreshing) onRefresh() }) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && recipes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onRefresh() }) { Text("Reintentar") }
                }
            }
            recipes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sin recetas aún", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Añade recetas desde Android o Desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recipes, key = { it.id }) { recipe ->
                    Card(modifier = Modifier.fillMaxWidth().clickable {
                        haptic.selection()
                        selectedRecipe = recipe
                    }) {
                        ListItem(
                            headlineContent   = { Text(recipe.title) },
                            supportingContent = { Text(recipe.description ?: "Sin descripción") },
                            trailingContent   = recipe.difficulty?.let { d ->
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
