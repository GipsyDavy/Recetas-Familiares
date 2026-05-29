package org.gipsybuho.recetasfamiliares.recipes

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.Spacing
import org.gipsybuho.recetasfamiliares.cooking.CookingScreen
import org.gipsybuho.recetasfamiliares.core.rememberHapticFeedback
import org.gipsybuho.recetasfamiliares.network.RecipeDto
import org.gipsybuho.recetasfamiliares.sync.SyncRepository

@OptIn(ExperimentalMaterial3Api::class)
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

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = { onRefresh() },
        modifier     = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.xl)) {
            Text("Recetas", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xl))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null && recipes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(Spacing.md))
                        OutlinedButton(onClick = { onRefresh() }) { Text("Reintentar") }
                    }
                }
                recipes.isEmpty() -> AnimatedEmptyState(
                    icon     = "🍳",
                    title    = "Sin recetas aún",
                    subtitle = "Añade recetas desde Android o Desktop"
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    items(recipes, key = { it.id }) { recipe ->
                        RecipeCard(recipe = recipe, onClick = {
                            haptic.selection()
                            selectedRecipe = recipe
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeDto, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().animateItem().clickable(onClick = onClick),
        shape     = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(152.dp)) {
            Box(
                modifier           = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment   = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Restaurant,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint     = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                )
            }
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        startY = 60f
                    )
                ),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(recipe.title, style = MaterialTheme.typography.titleMedium,
                        color = Color.White, maxLines = 2)
                    recipe.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f), maxLines = 1)
                    }
                }
            }
        }
        val totalMin = (recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)
        val hasMeta  = totalMin > 0 || recipe.difficulty != null || recipe.servings != null
        if (hasMeta) {
            Row(
                modifier              = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (totalMin > 0) RecipeMetaChip("⏱ ${totalMin}m")
                recipe.difficulty?.let { RecipeMetaChip(difficultyLabel(it)) }
                recipe.servings?.let { RecipeMetaChip("$it porciones") }
            }
        }
    }
}

@Composable
private fun RecipeMetaChip(label: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            label,
            style    = MaterialTheme.typography.labelMedium,
            color    = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.lg + Spacing.xs, vertical = Spacing.xs)
        )
    }
}

@Composable
internal fun AnimatedEmptyState(icon: String, title: String, subtitle: String) {
    val alpha by rememberInfiniteTransition(label = "pulse")
        .animateFloat(initialValue = 0.5f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(Spacing.lg),
            modifier              = Modifier.padding(40.dp)
        ) {
            Text(icon, style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alpha(alpha))
            Text(title, style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun difficultyLabel(value: String): String = when (value.uppercase()) {
    "EASY"   -> "Fácil"
    "MEDIUM" -> "Media"
    "HARD"   -> "Difícil"
    else     -> value
}
