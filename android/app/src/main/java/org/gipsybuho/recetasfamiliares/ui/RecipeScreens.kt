@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.gipsybuho.recetasfamiliares.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil3.compose.AsyncImage
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeRatingDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecipeList(
    recipes: List<RecipeEntity>,
    modifier: Modifier,
    viewModel: RecetasViewModel,
    onRefresh: () -> Unit,
    openRecipeId: String? = null,
    onRecipeOpened: () -> Unit = {}
) {
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val recipeHasMore by viewModel.recipeHasMore.collectAsState()
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var editingRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var editingIngredients by remember { mutableStateOf<List<RecipeIngredientEntity>>(emptyList()) }
    var editingSteps by remember { mutableStateOf<List<RecipeStepEntity>>(emptyList()) }
    var showCreateForm by remember { mutableStateOf(false) }
    var cookingMode by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var difficultyFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openRecipeId, recipes) {
        if (openRecipeId != null && recipes.isNotEmpty()) {
            val found = recipes.find { it.id == openRecipeId }
            if (found != null) { selectedRecipe = found; onRecipeOpened() }
        }
    }

    val filtered = recipes.let { list ->
        var r = list
        if (query.isNotBlank()) r = r.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.description?.contains(query, ignoreCase = true) == true
        }
        if (difficultyFilter != null) r = r.filter { it.difficulty == difficultyFilter }
        r
    }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(Spacing.xl)) {
                when {
                    showCreateForm -> RecipeForm(
                        initial = null, initialIngredients = emptyList(), initialSteps = emptyList(),
                        viewModel = viewModel,
                        onSaved = { showCreateForm = false },
                        onCancel = { showCreateForm = false; error = null }
                    )
                    editingRecipe != null -> RecipeForm(
                        initial = editingRecipe, initialIngredients = editingIngredients,
                        initialSteps = editingSteps, viewModel = viewModel,
                        onSaved = { editingRecipe = null; selectedRecipe = null },
                        onCancel = { editingRecipe = null }
                    )
                    cookingMode && selectedRecipe != null -> CookingScreen(
                        recipe = selectedRecipe!!, viewModel = viewModel,
                        onExit = { cookingMode = false }
                    )
                    selectedRecipe != null -> RecipeDetail(
                        recipe = selectedRecipe!!, viewModel = viewModel,
                        onBack = { selectedRecipe = null },
                        onEdit = { ings, stps ->
                            editingIngredients = ings; editingSteps = stps
                            editingRecipe = selectedRecipe
                        },
                        onDelete = { viewModel.deleteRecipe(selectedRecipe!!); selectedRecipe = null },
                        onCookingMode = { cookingMode = true }
                    )
                    else -> {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Recetas", style = MaterialTheme.typography.headlineSmall)
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                            }
                        }
                        error?.let {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(Spacing.md))
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Buscar recetas...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = if (query.isNotEmpty()) {
                                { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Borrar") } }
                            } else null,
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            listOf("EASY" to "Fácil", "MEDIUM" to "Media", "HARD" to "Difícil").forEach { (value, label) ->
                                val isSelected = difficultyFilter == value
                                val chipContainerColor by animateColorAsState(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    label = "chipColor_$value"
                                )
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { difficultyFilter = if (isSelected) null else value },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = chipContainerColor,
                                        containerColor = chipContainerColor
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        if (recipes.isEmpty() && isRefreshing) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                repeat(4) { SkeletonRecipeCard() }
                            }
                        } else Crossfade(targetState = filtered.isEmpty(), label = "recipeListState") { isEmpty ->
                            if (isEmpty) {
                                if (query.isBlank() && difficultyFilter == null) {
                                    EmptyStateView(icon = Icons.Outlined.Restaurant,
                                        title = "Sin recetas aún",
                                        subtitle = "Empieza a guardar las recetas de tu familia y construid vuestro recetario",
                                        actionLabel = "Crear primera receta", onAction = { showCreateForm = true })
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Sin resultados para \"$query\"",
                                            color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                                    }
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    items(filtered, key = { it.id }) { recipe ->
                                        RecipeCard(recipe, modifier = Modifier.animateItem()) { selectedRecipe = recipe; error = null }
                                    }
                                    if (recipeHasMore && query.isBlank()) {
                                        item {
                                            OutlinedButton(
                                                onClick = { viewModel.loadNextRecipePage() },
                                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)
                                            ) { Text("Cargar más recetas") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!showCreateForm && editingRecipe == null && selectedRecipe == null) {
                FloatingActionButton(
                    onClick = { showCreateForm = true; error = null },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl)
                ) { Icon(Icons.Filled.Add, contentDescription = "Nueva receta") }
            }
            if (selectedRecipe != null && editingRecipe == null && !cookingMode && !showCreateForm) {
                ExtendedFloatingActionButton(
                    onClick = { cookingMode = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl),
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = { Text("Cocinar") }
                )
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Icon(
                Icons.Outlined.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(56.dp).align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.30f)
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.58f)),
                        startY = 64f
                    )
                )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2
                )
                recipe.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1
                    )
                }
            }
        }
        val totalMin = (recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)
        val hasMeta = totalMin > 0 || recipe.difficulty != null || recipe.servings != null
        if (hasMeta) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (totalMin > 0) MetaChip("⏱ ${totalMin}m")
                recipe.difficulty?.let { MetaChip(it) }
                recipe.servings?.let { MetaChip("$it porciones") }
            }
        }
    }
}

@Composable
private fun SkeletonRecipeCard() {
    val alpha by rememberInfiniteTransition(label = "shimmer")
        .animateFloat(initialValue = 0.25f, targetValue = 0.65f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(152.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha)))
        Row(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(3) {
                Box(modifier = Modifier.height(20.dp).width(64.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecipeDetail(
    recipe: RecipeEntity,
    viewModel: RecetasViewModel,
    onBack: () -> Unit,
    onEdit: (List<RecipeIngredientEntity>, List<RecipeStepEntity>) -> Unit,
    onDelete: () -> Unit,
    onCookingMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val ingredients by viewModel.ingredientsFor(recipe.id).collectAsState(initial = emptyList())
    val steps by viewModel.stepsFor(recipe.id).collectAsState(initial = emptyList())
    val isFavorite by viewModel.isFavorite(recipe.id).collectAsState(initial = false)
    val photos by viewModel.photosFor(recipe.id).collectAsState(initial = emptyList())
    val ratings by viewModel.recipeRatings.collectAsState()
    val myUserId = viewModel.myUserId
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(recipe.id) {
        viewModel.loadPhotos(recipe.id)
        viewModel.loadRatings(recipe.id)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.launchUploadPhoto(context, recipe.id, it) }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(recipe.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.toggleFavorite(recipe.id) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar favorito" else "Añadir favorito",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
                if (showMenu) {
                    ModalBottomSheet(onDismissRequest = { showMenu = false }) {
                        Column(modifier = Modifier.padding(bottom = Spacing.xxl)) {
                            ListItem(
                                headlineContent = { Text("Modo Cocina") },
                                leadingContent  = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                                modifier = Modifier.clickable { showMenu = false; onCookingMode() }
                            )
                            ListItem(
                                headlineContent = { Text("Añadir foto") },
                                leadingContent  = { Icon(Icons.Filled.Add, contentDescription = null) },
                                modifier = Modifier.clickable { showMenu = false; photoPicker.launch("image/*") }
                            )
                            ListItem(
                                headlineContent = { Text("Compartir") },
                                leadingContent  = { Icon(Icons.Filled.Share, contentDescription = null) },
                                modifier = Modifier.clickable { showMenu = false; shareRecipe(context, recipe, ingredients, steps) }
                            )
                            ListItem(
                                headlineContent = { Text("Editar") },
                                leadingContent  = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                modifier = Modifier.clickable { showMenu = false; onEdit(ingredients, steps) }
                            )
                            ListItem(
                                headlineContent = { Text("Eliminar") },
                                leadingContent  = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                modifier = Modifier.clickable { showMenu = false; onDelete() }
                            )
                        }
                    }
                }
            }
        }
        if (photos.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoThumbnail(photo, onDelete = { viewModel.deletePhoto(photo) })
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                recipe.servings?.let { MetaChip("$it porciones") }
                val totalMin = (recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)
                if (totalMin > 0) MetaChip("$totalMin min")
                recipe.difficulty?.let { MetaChip(it) }
            }
        }
        recipe.description?.let { desc ->
            item { Text(desc, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.xs))
            Text(if (ingredients.isNotEmpty()) "Ingredientes (${ingredients.size})" else "Ingredientes",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (ingredients.isEmpty()) {
            item { Text("Sin ingredientes — actualiza para sincronizar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
        } else {
            items(ingredients, key = { it.id }) { ing -> IngredientRow(ing) }
        }
        item {
            Spacer(Modifier.height(Spacing.xs))
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.xs))
            Text(if (steps.isNotEmpty()) "Preparación (${steps.size} pasos)" else "Preparación",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (steps.isEmpty()) {
            item { Text("Sin pasos — actualiza para sincronizar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
        } else {
            items(steps, key = { it.id }) { step -> StepRow(step) }
        }
        item {
            Spacer(Modifier.height(Spacing.xs))
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.xs))
            Text(if (ratings.isNotEmpty()) "Valoraciones (${ratings.size})" else "Valoraciones",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            RatingsSection(ratings = ratings, myUserId = myUserId,
                onSubmit = { stars, comment, existingId, onError -> viewModel.submitRating(recipe.id, stars, comment, existingId, onError) },
                onDelete = { ratingId -> viewModel.deleteRating(recipe.id, ratingId) })
        }
    }
}

@Composable
internal fun IngredientRow(ing: RecipeIngredientEntity) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("• ${ing.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        val qty = buildString {
            ing.quantity?.let { append(it.toBigDecimal().stripTrailingZeros().toPlainString()) }
            ing.unit?.takeIf { it.isNotBlank() }?.let { append(" $it") }
        }
        if (qty.isNotBlank()) {
            Spacer(Modifier.width(Spacing.md))
            Text(qty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
internal fun StepRow(step: RecipeStepEntity) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("${step.position}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.weight(1f)) {
            Text(step.instruction, style = MaterialTheme.typography.bodyMedium)
            step.timerMinutes?.let {
                Text("⏱ $it min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoThumbnail(photo: RecipePhotoEntity, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        AsyncImage(
            model = photo.thumbnailUrl ?: photo.url,
            contentDescription = photo.caption,
            modifier = Modifier.size(120.dp, 88.dp).clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = {}, onLongClick = { showMenu = true }),
            contentScale = ContentScale.Crop
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Eliminar foto") }, onClick = { showMenu = false; onDelete() })
        }
    }
}

@Composable
internal fun RatingsSection(
    ratings: List<RecipeRatingDto>,
    myUserId: String?,
    onSubmit: (stars: Int, comment: String?, existingId: String?, onError: (String) -> Unit) -> Unit,
    onDelete: (ratingId: String) -> Unit
) {
    val myRating = ratings.firstOrNull { it.userId == myUserId }
    var selectedStars by remember(myRating) { mutableStateOf(myRating?.stars ?: 0) }
    var commentText by remember(myRating) { mutableStateOf(myRating?.comment ?: "") }
    var showForm by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        if (myRating == null) {
            if (!showForm) {
                OutlinedButton(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth()) { Text("Añadir valoración") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Tu valoración:", style = MaterialTheme.typography.labelLarge)
                Row {
                    TextButton(onClick = { showForm = true; selectedStars = myRating.stars; commentText = myRating.comment ?: "" }) { Text("Editar") }
                    TextButton(onClick = { onDelete(myRating.id) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
            }
            StarRow(stars = myRating.stars, interactive = false)
            myRating.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (showForm) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(if (myRating == null) "Nueva valoración" else "Editar valoración", style = MaterialTheme.typography.titleSmall)
                StarRow(stars = selectedStars, interactive = true, onSelect = { selectedStars = it })
                OutlinedTextField(value = commentText, onValueChange = { commentText = it },
                    label = { Text("Comentario (opcional)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Button(onClick = {
                        if (selectedStars == 0) { formError = "Selecciona entre 1 y 5 estrellas"; return@Button }
                        onSubmit(selectedStars, commentText.trim().ifBlank { null }, myRating?.id) { err -> formError = err }
                        showForm = false
                    }, enabled = selectedStars > 0) { Text("Guardar") }
                    OutlinedButton(onClick = { showForm = false; formError = null }) { Text("Cancelar") }
                }
            }
        }
        val othersRatings = ratings.filter { it.userId != myUserId }
        if (othersRatings.isNotEmpty()) {
            HorizontalDivider()
            othersRatings.forEach { rating ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(rating.userDisplayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        StarRow(stars = rating.stars, interactive = false)
                    }
                    rating.comment?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (ratings.isEmpty() && !showForm) {
            Text("Sé el primero en valorar esta receta", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun shareRecipe(
    context: android.content.Context,
    recipe: org.gipsybuho.recetasfamiliares.data.local.RecipeEntity,
    ingredients: List<org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity>,
    steps: List<org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity>
) {
    val text = buildString {
        appendLine("🍳 ${recipe.title}")
        val meta = buildList {
            recipe.servings?.let { add("$it porciones") }
            val mins = (recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)
            if (mins > 0) add("$mins min")
            recipe.difficulty?.let { add(it) }
        }
        if (meta.isNotEmpty()) appendLine(meta.joinToString(" · "))
        recipe.description?.let { appendLine(); appendLine(it) }
        if (ingredients.isNotEmpty()) {
            appendLine(); appendLine("🥗 Ingredientes:")
            ingredients.sortedBy { it.position }.forEach { ing ->
                val qty = buildString {
                    ing.quantity?.let { append(it.toBigDecimal().stripTrailingZeros().toPlainString()) }
                    ing.unit?.takeIf { it.isNotBlank() }?.let { append(" $it") }
                }
                appendLine("• ${ing.name}${if (qty.isNotBlank()) "  $qty" else ""}")
            }
        }
        if (steps.isNotEmpty()) {
            appendLine(); appendLine("👨‍🍳 Preparación:")
            steps.sortedBy { it.position }.forEach { step ->
                val timer = step.timerMinutes?.let { " ⏱ $it min" } ?: ""
                appendLine("${step.position}. ${step.instruction}$timer")
            }
        }
        appendLine(); append("📱 Recetas Familiares")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, recipe.title)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir receta"))
}

@Composable
internal fun StarRow(stars: Int, interactive: Boolean, onSelect: ((Int) -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        for (i in 1..5) {
            val filled = i <= stars
            if (interactive && onSelect != null) {
                IconButton(onClick = { onSelect(i) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (filled) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "$i estrellas",
                        tint = if (filled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = if (filled) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (filled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
