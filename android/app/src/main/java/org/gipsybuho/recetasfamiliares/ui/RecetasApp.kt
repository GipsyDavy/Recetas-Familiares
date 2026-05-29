package org.gipsybuho.recetasfamiliares.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import org.gipsybuho.recetasfamiliares.R
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListEntity
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemEntity

internal enum class MainTab { RECIPES, STOCK, SHOPPING, NOTES, MENU }

@Composable
fun RecetasApp(viewModel: RecetasViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.scheduleSync(WorkManager.getInstance(context))
            viewModel.refresh()
        }
    }

    if (!isLoggedIn) LoginScreen(viewModel) else MainShell(viewModel)
}

@Composable
private fun LoginScreen(viewModel: RecetasViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Restaurant,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            "Recetas Familiares",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Tu cocina familiar sincronizada",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xxl))
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.lg))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(Spacing.lg))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Spacing.xxl))
        Button(
            onClick = { viewModel.login(email, password) { error = it } },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar")
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(viewModel: RecetasViewModel) {
    var tab by remember { mutableStateOf(MainTab.RECIPES) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showThemePicker by remember { mutableStateOf(false) }
    val recipes by viewModel.recipes.collectAsState()
    val stockItems by viewModel.stockItems.collectAsState()
    val shoppingLists by viewModel.shoppingLists.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    var navigateToRecipeId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            if (searchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar recetas, stock, notas...") },
                            singleLine = true,
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                { IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Borrar")
                                }}
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searchActive = false; searchQuery = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar búsqueda")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        val tabTitle = when (tab) {
                            MainTab.RECIPES  -> "Recetas"
                            MainTab.STOCK    -> "Stock familiar"
                            MainTab.SHOPPING -> "Lista de la compra"
                            MainTab.NOTES    -> "Notas familiares"
                            MainTab.MENU     -> "Menú semanal"
                        }
                        Text(
                            tabTitle,
                            modifier = Modifier.semantics { heading() }
                        )
                    },
                    actions = {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Tema") } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = { showThemePicker = true }) {
                                Icon(Icons.Filled.Palette, contentDescription = "Cambiar tema")
                            }
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Buscar") } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "Buscar")
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == MainTab.RECIPES, onClick = { tab = MainTab.RECIPES },
                    icon = { Icon(Icons.Outlined.Restaurant, contentDescription = null) }, label = { Text("Recetas") })
                NavigationBarItem(selected = tab == MainTab.STOCK, onClick = { tab = MainTab.STOCK },
                    icon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) }, label = { Text("Stock") })
                NavigationBarItem(selected = tab == MainTab.SHOPPING, onClick = { tab = MainTab.SHOPPING },
                    icon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = null) }, label = { Text("Lista") })
                NavigationBarItem(selected = tab == MainTab.NOTES, onClick = { tab = MainTab.NOTES },
                    icon = { Icon(Icons.Outlined.Description, contentDescription = null) }, label = { Text("Notas") })
                NavigationBarItem(selected = tab == MainTab.MENU, onClick = { tab = MainTab.MENU },
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) }, label = { Text("Menú") })
            }
        }
    ) { padding ->
        if (showThemePicker) {
            ThemePickerDialog(
                currentTheme = selectedTheme,
                currentMode  = themeMode,
                onDismiss    = { showThemePicker = false },
                onApply      = { theme, mode ->
                    viewModel.setTheme(theme)
                    viewModel.setThemeMode(mode)
                    showThemePicker = false
                }
            )
        }

        if (searchActive && searchQuery.trim().length >= 2) {
            GlobalSearchScreen(
                query = searchQuery.trim(),
                recipes = recipes,
                stockItems = stockItems,
                notes = notes,
                modifier = Modifier.padding(padding),
                onNavigate = { selectedTab ->
                    searchActive = false
                    searchQuery = ""
                    tab = selectedTab
                }
            )
        } else {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label = "tabContent"
            ) { currentTab ->
                when (currentTab) {
                    MainTab.RECIPES  -> RecipeList(
                        recipes = recipes, modifier = Modifier.padding(padding),
                        viewModel = viewModel, onRefresh = viewModel::refresh,
                        openRecipeId = navigateToRecipeId,
                        onRecipeOpened = { navigateToRecipeId = null }
                    )
                    MainTab.STOCK    -> StockList(stockItems, Modifier.padding(padding), viewModel)
                    MainTab.SHOPPING -> ShoppingListScreen(shoppingLists, Modifier.padding(padding), viewModel)
                    MainTab.NOTES    -> NotesScreen(notes, Modifier.padding(padding), viewModel)
                    MainTab.MENU     -> MenuScreen(
                        menuItems = menuItems, recipes = recipes,
                        modifier = Modifier.padding(padding),
                        isRefreshing = isRefreshing, onRefresh = viewModel::refresh,
                        onAssignToMenu = { date, recipeId, mealType -> viewModel.assignToMenu(recipeId, date, mealType) },
                        onRemoveFromMenu = { item -> viewModel.removeFromMenu(item) },
                        onNavigateToRecipe = { recipeId ->
                            navigateToRecipeId = recipeId
                            tab = MainTab.RECIPES
                        }
                    )
                }
            }
        }
    }
}

// ── Shopping ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingListScreen(lists: List<ShoppingListEntity>, modifier: Modifier, viewModel: RecetasViewModel) {
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedList by remember { mutableStateOf<ShoppingListEntity?>(null) }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refresh() }, modifier = modifier) {
        Column(Modifier.padding(Spacing.xl)) {
            if (selectedList != null) {
                ShoppingListDetail(selectedList!!, viewModel, onBack = { selectedList = null })
            } else {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lista de la compra", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                if (lists.isEmpty()) {
                    LottieEmptyStateView(
                        lottieRes = R.raw.lottie_empty_list,
                        title     = "Sin listas de la compra",
                        subtitle  = "Las listas se generan desde el menú semanal o se sincronizan desde otro dispositivo")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
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
private fun ShoppingListDetail(list: ShoppingListEntity, viewModel: RecetasViewModel, onBack: () -> Unit) {
    val items by viewModel.itemsFor(list.id).collectAsState(initial = emptyList())
    val pending = items.count { !it.checked }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(list.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { shareShoppingList(context, list.name, items) }) { Text("Compartir") }
        }
        if (pending > 0) {
            Text("$pending pendiente${if (pending != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = Spacing.xs))
        }
        Spacer(Modifier.height(Spacing.md))
        if (items.isEmpty()) {
            LottieEmptyStateView(
                lottieRes = R.raw.lottie_empty_list,
                title     = "Lista vacía",
                subtitle  = "Desliza hacia abajo para sincronizar los artículos")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(items, key = { it.id }) { item ->
                    ShoppingItemRow(item, onCheckedChange = { viewModel.checkItem(item, it) })
                }
            }
        }
    }
}

private fun shareShoppingList(
    context: android.content.Context,
    listName: String,
    items: List<ShoppingListItemEntity>
) {
    val sb = StringBuilder()
    sb.append("🛒 $listName\n\n")
    items.forEach { item ->
        val check = if (item.checked) "✅" else "☐"
        sb.append("$check ${item.name}")
        item.quantity?.let { q ->
            sb.append("  ${q.toBigDecimal().stripTrailingZeros().toPlainString()}")
            item.unit?.takeIf { it.isNotBlank() }?.let { u -> sb.append(" $u") }
        }
        sb.append("\n")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
    }
    context.startActivity(Intent.createChooser(intent, "Compartir lista de la compra"))
}

@Composable
private fun ShoppingItemRow(item: ShoppingListItemEntity, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Checkbox(checked = item.checked, onCheckedChange = onCheckedChange)
        Text(
            text = buildString {
                append(item.name)
                item.quantity?.let { append("  ${it.toBigDecimal().stripTrailingZeros().toPlainString()}") }
                item.unit?.takeIf { it.isNotBlank() }?.let { append(" $it") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f)
        )
    }
}
