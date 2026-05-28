package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
    onRefresh: () -> Unit
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
    val filtered = if (query.isBlank()) recipes
        else recipes.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.description?.contains(query, ignoreCase = true) == true
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
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Recetas", style = MaterialTheme.typography.headlineSmall)
                            Button(onClick = onRefresh) { Text("Actualizar") }
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
                        Spacer(Modifier.height(Spacing.md))
                        if (filtered.isEmpty()) {
                            if (query.isBlank()) {
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
                                    Card(onClick = { selectedRecipe = recipe; error = null }) {
                                        ListItem(
                                            headlineContent = { Text(recipe.title) },
                                            supportingContent = { Text(recipe.description ?: "Sin descripción") }
                                        )
                                    }
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
            if (!showCreateForm && editingRecipe == null && selectedRecipe == null) {
                FloatingActionButton(
                    onClick = { showCreateForm = true; error = null },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl)
                ) { Icon(Icons.Filled.Favorite, contentDescription = "Nueva receta") }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(onClick = onBack) { Text("← Volver") }
                Text(recipe.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.toggleFavorite(recipe.id) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar favorito" else "Añadir favorito",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Modo Cocina") }, onClick = { showMenu = false; onCookingMode() })
                        DropdownMenuItem(text = { Text("Añadir foto") }, onClick = { showMenu = false; photoPicker.launch("image/*") })
                        DropdownMenuItem(text = { Text("Editar") }, onClick = { showMenu = false; onEdit(ingredients, steps) })
                        DropdownMenuItem(text = { Text("Eliminar") }, onClick = { showMenu = false; onDelete() })
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

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
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
