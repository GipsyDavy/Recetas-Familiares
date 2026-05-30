@file:OptIn(ExperimentalSharedTransitionApi::class)

package org.gipsybuho.recetasfamiliares.recipes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import org.gipsybuho.recetasfamiliares.stock.StockRepository
import org.gipsybuho.recetasfamiliares.sync.SyncRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    repository: RecipeRepository,
    syncRepo: SyncRepository,
    stockRepo: StockRepository? = null
) {
    var recipes          by remember { mutableStateOf<List<RecipeDto>>(emptyList()) }
    var stockNames       by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading          by remember { mutableStateOf(true) }
    var isRefreshing     by remember { mutableStateOf(false) }
    var error            by remember { mutableStateOf<String?>(null) }
    var selectedRecipe   by remember { mutableStateOf<RecipeDto?>(null) }
    var cookingMode      by remember { mutableStateOf(false) }
    var query            by remember { mutableStateOf("") }
    var difficultyFilter by remember { mutableStateOf<String?>(null) }
    var stockFilter      by remember { mutableStateOf(false) }
    val haptic           = rememberHapticFeedback()
    val scope            = rememberCoroutineScope()

    val filtered = remember(recipes, query, difficultyFilter, stockFilter, stockNames) {
        val base = recipes.filter { r ->
            (query.isBlank() || r.title.contains(query, ignoreCase = true) ||
                r.description?.contains(query, ignoreCase = true) == true) &&
            (difficultyFilter == null || r.difficulty?.uppercase() == difficultyFilter)
        }
        if (!stockFilter || stockNames.isEmpty()) base
        else {
            val allIngredients = repository.loadLocalIngredients()
            val matchingIds = allIngredients
                .filter { (_, name) -> name in stockNames }
                .map { it.first }
                .toSet()
            base.filter { it.id in matchingIds }
        }
    }

    suspend fun loadData() {
        runCatching {
            recipes = repository.loadRecipes()
            if (stockRepo != null) {
                stockNames = stockRepo.loadStockItems().map { it.name.lowercase().trim() }.toSet()
            }
        }.onFailure { error = it.message }
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

    SharedTransitionLayout {
        AnimatedContent(
            targetState = selectedRecipe,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInHorizontally { it }) togetherWith
                (fadeOut(tween(250)) + slideOutHorizontally { -it })
            },
            label = "recipe_nav"
        ) { targetRecipe ->
            if (targetRecipe != null) {
                RecipeDetailScreen(
                    recipe                  = targetRecipe,
                    repository              = repository,
                    onBack                  = { selectedRecipe = null },
                    onCookingMode           = { cookingMode = true },
                    sharedTransitionScope   = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh    = { onRefresh() },
                    modifier     = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.md)) {
                        Text("Recetas", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(Spacing.md))

                        OutlinedTextField(
                            value         = query,
                            onValueChange = { query = it },
                            placeholder   = { Text("Buscar recetas…") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            listOf("EASY" to "Fácil", "MEDIUM" to "Media", "HARD" to "Difícil").forEach { (key, label) ->
                                FilterChip(
                                    selected = difficultyFilter == key,
                                    onClick  = { difficultyFilter = if (difficultyFilter == key) null else key },
                                    label    = { Text(label) }
                                )
                            }
                            if (stockRepo != null) {
                                FilterChip(
                                    selected = stockFilter,
                                    onClick  = { stockFilter = !stockFilter },
                                    label    = { Text("Con mi stock") }
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))

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
                            filtered.isEmpty() -> AnimatedEmptyState(
                                icon     = if (query.isNotBlank() || difficultyFilter != null) "🔍" else "🍳",
                                title    = if (query.isNotBlank() || difficultyFilter != null) "Sin resultados" else "Sin recetas aún",
                                subtitle = if (query.isNotBlank() || difficultyFilter != null) "Prueba con otros filtros" else "Añade recetas desde Android o Desktop"
                            )
                            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                items(filtered, key = { it.id }) { recipe ->
                                    RecipeCard(
                                        recipe                  = recipe,
                                        sharedTransitionScope   = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        onClick                 = { haptic.selection(); selectedRecipe = recipe }
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
private fun RecipeCard(
    recipe: RecipeDto,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onClick: () -> Unit
) {
    val sharedMod = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState      = rememberSharedContentState(key = "recipe_bounds_${recipe.id}"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else Modifier

    Card(
        modifier  = Modifier.fillMaxWidth().animateItem().then(sharedMod).clickable(onClick = onClick),
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
