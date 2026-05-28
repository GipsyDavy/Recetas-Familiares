package org.gipsybuho.recetasfamiliares.ui

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeIngredientItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeStepItemDto
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListEntity
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemEntity
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private enum class MainTab { RECIPES, STOCK, SHOPPING, NOTES }

@Composable
fun RecetasApp(viewModel: RecetasViewModel) {
    val context = LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.scheduleSync(WorkManager.getInstance(context))
            viewModel.refresh()
        }
    }

    if (!isLoggedIn) {
        LoginScreen(viewModel)
    } else {
        MainShell(viewModel)
    }
}

@Composable
private fun LoginScreen(viewModel: RecetasViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Recetas Familiares", style = MaterialTheme.typography.headlineMedium)
        Text("Tu cocina familiar sincronizada", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { viewModel.login(email, password) { error = it } },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar")
        }
    }
}

@Composable
private fun MainShell(viewModel: RecetasViewModel) {
    var tab by remember { mutableStateOf(MainTab.RECIPES) }
    val recipes by viewModel.recipes.collectAsState()
    val stockItems by viewModel.stockItems.collectAsState()
    val shoppingLists by viewModel.shoppingLists.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainTab.RECIPES,
                    onClick = { tab = MainTab.RECIPES },
                    icon = { Icon(Icons.Outlined.Restaurant, contentDescription = null) },
                    label = { Text("Recetas") }
                )
                NavigationBarItem(
                    selected = tab == MainTab.STOCK,
                    onClick = { tab = MainTab.STOCK },
                    icon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
                    label = { Text("Stock") }
                )
                NavigationBarItem(
                    selected = tab == MainTab.SHOPPING,
                    onClick = { tab = MainTab.SHOPPING },
                    icon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = null) },
                    label = { Text("Lista") }
                )
                NavigationBarItem(
                    selected = tab == MainTab.NOTES,
                    onClick = { tab = MainTab.NOTES },
                    icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    label = { Text("Notas") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            MainTab.RECIPES -> RecipeList(recipes, Modifier.padding(padding), viewModel, viewModel::refresh)
            MainTab.STOCK -> StockList(stockItems, Modifier.padding(padding), viewModel)
            MainTab.SHOPPING -> ShoppingListScreen(shoppingLists, Modifier.padding(padding), viewModel)
            MainTab.NOTES -> NotesScreen(notes, Modifier.padding(padding), viewModel)
        }
    }
}

private data class IngredientDraft(val name: String = "", val quantity: String = "", val unit: String = "")
private data class StepDraft(val instruction: String = "", val timerMinutes: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeList(
    recipes: List<RecipeEntity>,
    modifier: Modifier,
    viewModel: RecetasViewModel,
    onRefresh: () -> Unit
) {
    val isRefreshing by viewModel.isRefreshing.collectAsState()
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
        Column(Modifier.padding(16.dp)) {
            when {
                showCreateForm -> RecipeForm(
                    initial = null,
                    initialIngredients = emptyList(),
                    initialSteps = emptyList(),
                    viewModel = viewModel,
                    onSaved = { showCreateForm = false },
                    onCancel = { showCreateForm = false; error = null }
                )
                editingRecipe != null -> RecipeForm(
                    initial = editingRecipe,
                    initialIngredients = editingIngredients,
                    initialSteps = editingSteps,
                    viewModel = viewModel,
                    onSaved = { editingRecipe = null; selectedRecipe = null },
                    onCancel = { editingRecipe = null }
                )
                cookingMode && selectedRecipe != null -> CookingScreen(
                    recipe = selectedRecipe!!,
                    viewModel = viewModel,
                    onExit = { cookingMode = false }
                )
                selectedRecipe != null -> RecipeDetail(
                    recipe = selectedRecipe!!,
                    viewModel = viewModel,
                    onBack = { selectedRecipe = null },
                    onEdit = { ings, stps ->
                        editingIngredients = ings
                        editingSteps = stps
                        editingRecipe = selectedRecipe
                    },
                    onDelete = {
                        viewModel.deleteRecipe(selectedRecipe!!)
                        selectedRecipe = null
                    },
                    onCookingMode = { cookingMode = true }
                )
                else -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Recetas", style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = onRefresh) { Text("Actualizar") }
                    }
                    error?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar recetas...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Borrar") } }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    if (filtered.isEmpty()) {
                        if (query.isBlank()) {
                            EmptyStateView(
                                icon = Icons.Outlined.Restaurant,
                                title = "Sin recetas aún",
                                subtitle = "Empieza a guardar las recetas de tu familia y construid vuestro recetario",
                                actionLabel = "Crear primera receta",
                                onAction = { showCreateForm = true }
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Sin resultados para \"$query\"",
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filtered, key = { it.id }) { recipe ->
                                Card(onClick = { selectedRecipe = recipe; error = null }) {
                                    ListItem(
                                        headlineContent = { Text(recipe.title) },
                                        supportingContent = { Text(recipe.description ?: "Sin descripción") }
                                    )
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva receta")
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeDetail(
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
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(recipe.id) { viewModel.loadPhotos(recipe.id) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.launchUploadPhoto(context, recipe.id, it) }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onBack) { Text("← Volver") }
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.toggleFavorite(recipe.id) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar favorito" else "Añadir favorito",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Modo Cocina") },
                            onClick = { showMenu = false; onCookingMode() }
                        )
                        DropdownMenuItem(
                            text = { Text("Añadir foto") },
                            onClick = { showMenu = false; photoPicker.launch("image/*") }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            onClick = { showMenu = false; onEdit(ingredients, steps) }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }

        if (photos.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoThumbnail(photo, onDelete = { viewModel.deletePhoto(photo) })
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recipe.servings?.let { MetaChip("$it porciones") }
                val totalMin = (recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)
                if (totalMin > 0) MetaChip("$totalMin min")
                recipe.difficulty?.let { MetaChip(it) }
            }
        }
        recipe.description?.let { desc ->
            item {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
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
                    "Sin ingredientes — actualiza para sincronizar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(ingredients, key = { it.id }) { ing ->
                IngredientRow(ing)
            }
        }
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
                    "Sin pasos — actualiza para sincronizar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(steps, key = { it.id }) { step ->
                StepRow(step)
            }
        }
    }
}

@Composable
private fun IngredientRow(ing: RecipeIngredientEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "• ${ing.name}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        val qty = buildString {
            ing.quantity?.let { append(it.toBigDecimal().stripTrailingZeros().toPlainString()) }
            ing.unit?.takeIf { it.isNotBlank() }?.let { append(" $it") }
        }
        if (qty.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(qty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun StepRow(step: RecipeStepEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
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
            modifier = Modifier.weight(1f)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CookingScreen(
    recipe: RecipeEntity,
    viewModel: RecetasViewModel,
    onExit: () -> Unit
) {
    val steps by viewModel.stepsFor(recipe.id).collectAsState(initial = emptyList())
    var currentIndex by remember { mutableStateOf(0) }
    var timerSecondsLeft by remember(currentIndex, steps) {
        mutableStateOf(steps.getOrNull(currentIndex)?.timerMinutes?.let { it * 60 })
    }
    var timerRunning by remember(currentIndex) { mutableStateOf(false) }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while ((timerSecondsLeft ?: 0) > 0) {
                delay(1_000L)
                timerSecondsLeft = (timerSecondsLeft ?: 0) - 1
            }
            timerRunning = false
        }
    }

    val view = LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (view.context as android.app.Activity).window
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val currentStep = steps.getOrNull(currentIndex)
    val finished = steps.isNotEmpty() && currentIndex >= steps.size

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Salir")
                }
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                )
            }

            if (steps.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (finished) "¡Receta completada!"
                        else "Paso ${currentIndex + 1} de ${steps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { minOf(1f, (currentIndex + 1).toFloat() / steps.size) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    steps.isEmpty() -> Text(
                        "Esta receta no tiene pasos registrados",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    finished -> Text(
                        "¡Buen provecho!",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    else -> Text(
                        currentStep!!.instruction,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        lineHeight = MaterialTheme.typography.headlineMedium.lineHeight
                    )
                }
            }

            if (currentStep?.timerMinutes != null && !finished) {
                val secs = timerSecondsLeft ?: (currentStep.timerMinutes * 60)
                val mm = secs / 60
                val ss = secs % 60
                val timerDone = secs == 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = when {
                            timerDone -> MaterialTheme.colorScheme.errorContainer
                            timerRunning -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (timerDone) "⏱ ¡Listo!" else "⏱ %02d:%02d".format(mm, ss),
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (timerDone) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = {
                                if (timerDone) {
                                    timerSecondsLeft = currentStep.timerMinutes * 60
                                    timerRunning = false
                                } else {
                                    timerRunning = !timerRunning
                                }
                            }) {
                                Icon(
                                    imageVector = if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (timerRunning) "Pausar" else "Iniciar temporizador"
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { if (currentIndex > 0) { currentIndex--; timerRunning = false } },
                    enabled = currentIndex > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("← Anterior") }

                if (finished) {
                    Button(onClick = onExit, modifier = Modifier.weight(1f)) {
                        Text("Cerrar")
                    }
                } else if (currentIndex < steps.size - 1) {
                    Button(
                        onClick = { currentIndex++; timerRunning = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Siguiente →") }
                } else {
                    Button(
                        onClick = { currentIndex = steps.size },
                        modifier = Modifier.weight(1f)
                    ) { Text("¡Finalizar!") }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoThumbnail(photo: RecipePhotoEntity, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        AsyncImage(
            model = photo.thumbnailUrl ?: photo.url,
            contentDescription = photo.caption,
            modifier = Modifier
                .size(120.dp, 88.dp)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = {}, onLongClick = { showMenu = true }),
            contentScale = ContentScale.Crop
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Eliminar foto") },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }
}

@Composable
private fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun MetaChip(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockList(
    stockItems: List<StockItemEntity>,
    modifier: Modifier,
    viewModel: RecetasViewModel
) {
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedItem by remember { mutableStateOf<StockItemEntity?>(null) }
    var editingItem by remember { mutableStateOf<StockItemEntity?>(null) }
    var showCreateForm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) stockItems
        else stockItems.filter { it.name.contains(query, ignoreCase = true) }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refresh() }, modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            when {
                showCreateForm -> StockForm(
                    initial = null,
                    onSave = { name, qty, unit, threshold, expires, note ->
                        viewModel.createStockItem(name, qty, unit, threshold, expires, note) { error = it }
                        showCreateForm = false
                    },
                    onCancel = { showCreateForm = false }
                )
                editingItem != null -> StockForm(
                    initial = editingItem,
                    onSave = { name, qty, unit, threshold, expires, note ->
                        viewModel.updateStockItem(editingItem!!, name, qty, unit, threshold, expires, note) { error = it }
                        editingItem = null
                        selectedItem = null
                    },
                    onCancel = { editingItem = null }
                )
                selectedItem != null -> StockDetail(
                    item = selectedItem!!,
                    onBack = { selectedItem = null },
                    onEdit = { editingItem = selectedItem },
                    onDelete = {
                        viewModel.deleteStockItem(selectedItem!!)
                        selectedItem = null
                    }
                )
                else -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stock Familiar", style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = { viewModel.refresh() }) { Text("Actualizar") }
                    }
                    error?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar en stock...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Borrar") } }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    if (filtered.isEmpty()) {
                        if (query.isBlank()) {
                            EmptyStateView(
                                icon = Icons.Outlined.Inventory2,
                                title = "Stock vacío",
                                subtitle = "Registra los ingredientes de casa para controlar caducidades y bajo stock",
                                actionLabel = "Añadir primer artículo",
                                onAction = { showCreateForm = true }
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Sin resultados para \"$query\"",
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filtered, key = { it.id }) { item ->
                                StockItemCard(item, onClick = { selectedItem = item; error = null })
                            }
                        }
                    }
                }
            }
        }

        if (!showCreateForm && editingItem == null && selectedItem == null) {
            FloatingActionButton(
                onClick = { showCreateForm = true; error = null },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo item de stock")
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingListScreen(
    lists: List<ShoppingListEntity>,
    modifier: Modifier,
    viewModel: RecetasViewModel
) {
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedList by remember { mutableStateOf<ShoppingListEntity?>(null) }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refresh() }, modifier = modifier) {
    Column(Modifier.padding(16.dp)) {
        if (selectedList != null) {
            ShoppingListDetail(selectedList!!, viewModel, onBack = { selectedList = null })
        } else {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lista de la compra", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { viewModel.refresh() }) { Text("Actualizar") }
            }
            Spacer(Modifier.height(12.dp))
            if (lists.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Outlined.ShoppingCart,
                    title = "Sin listas de la compra",
                    subtitle = "Las listas se generan desde el menú semanal o se sincronizan desde otro dispositivo"
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lists, key = { it.id }) { list ->
                        Card(onClick = { selectedList = list }) {
                            ListItem(
                                headlineContent = { Text(list.name) },
                                supportingContent = {
                                    if (list.completed) Text("Completada", color = MaterialTheme.colorScheme.primary)
                                    else list.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ShoppingListDetail(
    list: ShoppingListEntity,
    viewModel: RecetasViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.itemsFor(list.id).collectAsState(initial = emptyList())
    val pending = items.count { !it.checked }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onBack) { Text("← Volver") }
            Text(
                list.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
        }
        if (pending > 0) {
            Text(
                "$pending pendiente${if (pending != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.ShoppingCart,
                title = "Lista vacía",
                subtitle = "Desliza hacia abajo para sincronizar los artículos"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.id }) { item ->
                    ShoppingItemRow(item, onCheckedChange = { viewModel.checkItem(item, it) })
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingListItemEntity,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Checkbox(checked = item.checked, onCheckedChange = onCheckedChange)
        Text(
            text = buildString {
                append(item.name)
                item.quantity?.let { append("  ${it.toBigDecimal().stripTrailingZeros().toPlainString()}") }
                item.unit?.takeIf { it.isNotBlank() }?.let { append(" $it") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.checked) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Notes ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesScreen(
    notes: List<FamilyNoteEntity>,
    modifier: Modifier,
    viewModel: RecetasViewModel
) {
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedNote by remember { mutableStateOf<FamilyNoteEntity?>(null) }
    var editingNote by remember { mutableStateOf<FamilyNoteEntity?>(null) }
    var showCreateForm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) notes
        else notes.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.body.contains(query, ignoreCase = true)
        }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refresh() }, modifier = modifier) {
    Column(Modifier.padding(16.dp)) {
        when {
            showCreateForm -> NoteForm(
                initialTitle = "",
                initialBody = "",
                initialPinned = false,
                onSave = { title, body, pinned ->
                    viewModel.createNote(title, body, pinned) { error = it }
                    showCreateForm = false
                },
                onCancel = { showCreateForm = false }
            )
            editingNote != null -> NoteForm(
                initialTitle = editingNote!!.title,
                initialBody = editingNote!!.body,
                initialPinned = editingNote!!.pinned,
                onSave = { title, body, pinned ->
                    viewModel.updateNote(editingNote!!, title, body, pinned) { error = it }
                    editingNote = null
                    selectedNote = null
                },
                onCancel = { editingNote = null }
            )
            selectedNote != null -> NoteDetail(
                note = selectedNote!!,
                onBack = { selectedNote = null },
                onEdit = { editingNote = selectedNote },
                onDelete = {
                    viewModel.deleteNote(selectedNote!!)
                    selectedNote = null
                }
            )
            else -> {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Notas familiares", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { showCreateForm = true }) { Text("Nueva nota") }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar notas...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Borrar") } }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    if (query.isBlank()) {
                        EmptyStateView(
                            icon = Icons.Outlined.Description,
                            title = "Sin notas familiares",
                            subtitle = "Escribe recuerdos, anécdotas y secretos culinarios de vuestra familia",
                            actionLabel = "Nueva nota",
                            onAction = { showCreateForm = true }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Sin resultados para \"$query\"",
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.id }) { note ->
                            NoteCard(note, onClick = { selectedNote = note; error = null })
                        }
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteCard(note: FamilyNoteEntity, onClick: () -> Unit) {
    Card(onClick = onClick) {
        ListItem(
            headlineContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.pinned) {
                        Text("📌", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(note.title)
                }
            },
            supportingContent = {
                val preview = note.body.take(80).replace('\n', ' ')
                Text(
                    if (note.body.length > 80) "$preview…" else preview,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = note.recipeTitle?.let {
                { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
            }
        )
    }
}

@Composable
private fun NoteDetail(
    note: FamilyNoteEntity,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onBack) { Text("← Volver") }
                Text(
                    note.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (note.pinned) MetaChip("📌 Fijada")
                note.recipeTitle?.let { MetaChip(it) }
            }
        }
        item {
            Text(
                note.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) { Text("Editar") }
                OutlinedButton(onClick = onDelete) { Text("Eliminar") }
            }
        }
    }
}

@Composable
private fun NoteForm(
    initialTitle: String,
    initialBody: String,
    initialPinned: Boolean,
    onSave: (String, String, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    var pinned by remember(initialPinned) { mutableStateOf(initialPinned) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (initialTitle.isEmpty()) "Nueva nota" else "Editar nota",
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Contenido") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = pinned, onCheckedChange = { pinned = it })
            Text("Fijar nota")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), body.trim(), pinned) },
                enabled = title.isNotBlank()
            ) { Text("Guardar") }
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockItemCard(item: StockItemEntity, onClick: () -> Unit = {}) {
    val expiryDays = remember(item.expiresAt) {
        item.expiresAt?.let { dateStr ->
            runCatching {
                ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(dateStr.substring(0, 10)))
            }.getOrNull()
        }
    }
    val isLowStock = item.lowStockThreshold != null && item.quantity != null &&
            item.quantity <= item.lowStockThreshold

    val expiryColor = when {
        expiryDays != null && expiryDays <= 3 -> MaterialTheme.colorScheme.error
        expiryDays != null && expiryDays <= 7 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(onClick = onClick) {
        ListItem(
            headlineContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.name)
                    if (isLowStock) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                "Bajo stock",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val qty = "${item.quantity ?: "-"} ${item.unit ?: ""}".trim()
                    Text(qty, style = MaterialTheme.typography.bodySmall)
                    item.expiresAt?.let {
                        val label = when {
                            expiryDays == null -> "Caduca: ${it.substring(0, 10)}"
                            expiryDays < 0 -> "Caducado"
                            expiryDays == 0L -> "Caduca hoy"
                            expiryDays <= 7 -> "Caduca en $expiryDays días"
                            else -> "Caduca: ${it.substring(0, 10)}"
                        }
                        Text(label, style = MaterialTheme.typography.bodySmall, color = expiryColor)
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeForm(
    initial: RecipeEntity?,
    initialIngredients: List<RecipeIngredientEntity>,
    initialSteps: List<RecipeStepEntity>,
    viewModel: RecetasViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var description by remember(initial) { mutableStateOf(initial?.description ?: "") }
    var servings by remember(initial) { mutableStateOf(initial?.servings?.toString() ?: "") }
    var prepMinutes by remember(initial) { mutableStateOf(initial?.prepMinutes?.toString() ?: "") }
    var cookMinutes by remember(initial) { mutableStateOf(initial?.cookMinutes?.toString() ?: "") }
    var difficulty by remember(initial) { mutableStateOf(initial?.difficulty ?: "") }
    var titleError by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val ingredients: SnapshotStateList<IngredientDraft> = remember(initialIngredients) {
        if (initialIngredients.isNotEmpty())
            initialIngredients.map { IngredientDraft(it.name, it.quantity?.toString() ?: "", it.unit ?: "") }.toMutableStateList()
        else
            mutableListOf(IngredientDraft()).toMutableStateList()
    }
    val steps: SnapshotStateList<StepDraft> = remember(initialSteps) {
        if (initialSteps.isNotEmpty())
            initialSteps.map { StepDraft(it.instruction, it.timerMinutes?.toString() ?: "") }.toMutableStateList()
        else
            mutableListOf(StepDraft()).toMutableStateList()
    }

    val difficulties = listOf("EASY" to "Fácil", "MEDIUM" to "Media", "HARD" to "Difícil")

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                if (initial == null) "Nueva receta" else "Editar receta",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        // Metadata
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleError = false },
                label = { Text("Título *") },
                isError = titleError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = servings,
                    onValueChange = { servings = it },
                    label = { Text("Porciones") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = prepMinutes,
                    onValueChange = { prepMinutes = it },
                    label = { Text("Prep (min)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cookMinutes,
                    onValueChange = { cookMinutes = it },
                    label = { Text("Cocción (min)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                difficulties.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = difficulty == value,
                        onClick = { difficulty = if (difficulty == value) "" else value },
                        shape = SegmentedButtonDefaults.itemShape(index, difficulties.size)
                    ) { Text(label) }
                }
            }
        }
        // Ingredients
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ingredientes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { ingredients.add(IngredientDraft()) }) { Text("+ Añadir") }
            }
        }
        items(ingredients.size) { i ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ingredients[i].name,
                    onValueChange = { ingredients[i] = ingredients[i].copy(name = it) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = ingredients[i].quantity,
                    onValueChange = { ingredients[i] = ingredients[i].copy(quantity = it) },
                    label = { Text("Cant.") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = ingredients[i].unit,
                    onValueChange = { ingredients[i] = ingredients[i].copy(unit = it) },
                    label = { Text("Ud.") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { if (ingredients.size > 1) ingredients.removeAt(i) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Eliminar", modifier = Modifier.size(18.dp))
                }
            }
        }
        // Steps
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pasos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { steps.add(StepDraft()) }) { Text("+ Añadir") }
            }
        }
        items(steps.size) { i ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Surface(
                    shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp).align(Alignment.CenterVertically)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                OutlinedTextField(
                    value = steps[i].instruction,
                    onValueChange = { steps[i] = steps[i].copy(instruction = it) },
                    label = { Text("Instrucción") },
                    maxLines = 3,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = steps[i].timerMinutes,
                    onValueChange = { steps[i] = steps[i].copy(timerMinutes = it) },
                    label = { Text("Min.") },
                    singleLine = true,
                    modifier = Modifier.width(70.dp)
                )
                IconButton(onClick = { if (steps.size > 1) steps.removeAt(i) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Eliminar", modifier = Modifier.size(18.dp))
                }
            }
        }
        // Footer
        item {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (title.isBlank()) { titleError = true; return@Button }
                    val ingDtos = ingredients.filter { it.name.isNotBlank() }
                        .map { RecipeIngredientItemDto(it.name.trim(), it.quantity.toDoubleOrNull(), it.unit.trim().ifBlank { null }, null) }
                    val stepDtos = steps.filter { it.instruction.isNotBlank() }
                        .map { RecipeStepItemDto(it.instruction.trim(), it.timerMinutes.toIntOrNull()) }
                    if (initial == null) {
                        viewModel.createRecipe(title.trim(), description.trim().ifBlank { null },
                            servings.toIntOrNull(), prepMinutes.toIntOrNull(), cookMinutes.toIntOrNull(),
                            difficulty.ifBlank { null }, ingDtos, stepDtos) { error = it }
                    } else {
                        viewModel.updateRecipe(initial, title.trim(), description.trim().ifBlank { null },
                            servings.toIntOrNull(), prepMinutes.toIntOrNull(), cookMinutes.toIntOrNull(),
                            difficulty.ifBlank { null }, ingDtos, stepDtos) { error = it }
                    }
                    onSaved()
                }) { Text(if (initial == null) "Guardar receta" else "Guardar cambios") }
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun StockDetail(
    item: StockItemEntity,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isLowStock = item.lowStockThreshold != null && item.quantity != null &&
            item.quantity <= item.lowStockThreshold

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onBack) { Text("← Volver") }
            Text(item.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val qty = "${item.quantity ?: "—"} ${item.unit ?: ""}".trim()
            MetaChip(qty)
            item.expiresAt?.let { MetaChip("Caduca: ${it.take(10)}") }
            item.lowStockThreshold?.let {
                MetaChip(if (isLowStock) "⚠ Bajo stock (mín: $it)" else "Mín: $it")
            }
        }
        item.note?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit) { Text("Editar") }
            OutlinedButton(onClick = onDelete) { Text("Eliminar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockForm(
    initial: StockItemEntity?,
    onSave: (name: String, quantity: Double?, unit: String?, lowStockThreshold: Double?, expiresAt: String?, note: String?) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var quantity by remember(initial) { mutableStateOf(initial?.quantity?.toString() ?: "") }
    var unit by remember(initial) { mutableStateOf(initial?.unit ?: "") }
    var expiresAt by remember(initial) { mutableStateOf(initial?.expiresAt?.take(10) ?: "") }
    var lowStockThreshold by remember(initial) { mutableStateOf(initial?.lowStockThreshold?.toString() ?: "") }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var showAdvanced by remember {
        mutableStateOf(initial?.lowStockThreshold != null || !initial?.note.isNullOrEmpty())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.expiresAt?.take(10)?.let {
            runCatching {
                LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrNull()
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        expiresAt = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (initial == null) "Nuevo item de stock" else "Editar item de stock",
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = { Text("Nombre *") },
            isError = nameError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Cantidad") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Unidad") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = if (expiresAt.isEmpty()) "" else "Caduca: $expiresAt",
            onValueChange = {},
            label = { Text("Fecha de caducidad") },
            readOnly = true,
            trailingIcon = {
                TextButton(onClick = { showDatePicker = true }) { Text("Seleccionar") }
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showAdvanced) "Ocultar avanzado ▲" else "Mostrar avanzado ▼")
        }
        if (showAdvanced) {
            OutlinedTextField(
                value = lowStockThreshold,
                onValueChange = { lowStockThreshold = it },
                label = { Text("Umbral de stock bajo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notas") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (name.isBlank()) { nameError = true; return@Button }
                onSave(
                    name.trim(),
                    quantity.toDoubleOrNull(),
                    unit.trim().ifBlank { null },
                    lowStockThreshold.toDoubleOrNull(),
                    expiresAt.ifBlank { null },
                    note.trim().ifBlank { null }
                )
            }) { Text(if (initial == null) "Guardar" else "Guardar cambios") }
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}
