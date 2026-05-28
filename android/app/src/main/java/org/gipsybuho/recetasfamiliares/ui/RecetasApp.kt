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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
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

    Scaffold(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeList(
    recipes: List<RecipeEntity>,
    modifier: Modifier,
    viewModel: RecetasViewModel,
    onRefresh: () -> Unit
) {
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    Column(modifier.padding(16.dp)) {
        if (selectedRecipe != null) {
            RecipeDetail(selectedRecipe!!, viewModel, onBack = { selectedRecipe = null })
        } else {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Recetas", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onRefresh) { Text("Actualizar") }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recipes, key = { it.id }) { recipe ->
                    Card(onClick = { selectedRecipe = recipe }) {
                        ListItem(
                            headlineContent = { Text(recipe.title) },
                            supportingContent = { Text(recipe.description ?: "Sin descripcion") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeDetail(
    recipe: RecipeEntity,
    viewModel: RecetasViewModel,
    onBack: () -> Unit
) {
    val ingredients by viewModel.ingredientsFor(recipe.id).collectAsState(initial = emptyList())
    val steps by viewModel.stepsFor(recipe.id).collectAsState(initial = emptyList())
    val isFavorite by viewModel.isFavorite(recipe.id).collectAsState(initial = false)

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

@Composable
private fun StockList(
    stockItems: List<StockItemEntity>,
    modifier: Modifier,
    viewModel: RecetasViewModel
) {
    var selectedItem by remember { mutableStateOf<StockItemEntity?>(null) }
    var editingItem by remember { mutableStateOf<StockItemEntity?>(null) }
    var showCreateForm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(modifier) {
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
                    Spacer(Modifier.height(12.dp))
                    if (stockItems.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sin artículos en stock", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(stockItems, key = { it.id }) { item ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingListScreen(
    lists: List<ShoppingListEntity>,
    modifier: Modifier,
    viewModel: RecetasViewModel
) {
    var selectedList by remember { mutableStateOf<ShoppingListEntity?>(null) }
    Column(modifier.padding(16.dp)) {
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin listas de la compra", color = MaterialTheme.colorScheme.outline)
                }
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin artículos — actualiza para sincronizar", color = MaterialTheme.colorScheme.outline)
            }
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

@Composable
private fun NotesScreen(
    notes: List<FamilyNoteEntity>,
    modifier: Modifier,
    viewModel: RecetasViewModel
) {
    var selectedNote by remember { mutableStateOf<FamilyNoteEntity?>(null) }
    var editingNote by remember { mutableStateOf<FamilyNoteEntity?>(null) }
    var showCreateForm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier.padding(16.dp)) {
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
                Spacer(Modifier.height(12.dp))
                if (notes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Sin notas — crea tu primera nota familiar",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(notes, key = { it.id }) { note ->
                            NoteCard(note, onClick = { selectedNote = note; error = null })
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
