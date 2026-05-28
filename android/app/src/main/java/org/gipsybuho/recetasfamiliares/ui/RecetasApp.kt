package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListEntity
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemEntity

private enum class MainTab { RECIPES, STOCK, SHOPPING, NOTES }

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
        verticalArrangement = Arrangement.Center
    ) {
        Text("Recetas Familiares", style = MaterialTheme.typography.headlineMedium)
        Text("Tu cocina familiar sincronizada", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Spacing.xxl))
        OutlinedTextField(value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.lg))
        OutlinedTextField(value = password, onValueChange = { password = it },
            label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth())
        error?.let {
            Spacer(Modifier.height(Spacing.lg))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { viewModel.login(email, password) { error = it } },
            modifier = Modifier.fillMaxWidth()) { Text("Entrar") }
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
        viewModel.userMessage.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
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
            }
        }
    ) { padding ->
        when (tab) {
            MainTab.RECIPES  -> RecipeList(recipes, Modifier.padding(padding), viewModel, viewModel::refresh)
            MainTab.STOCK    -> StockList(stockItems, Modifier.padding(padding), viewModel)
            MainTab.SHOPPING -> ShoppingListScreen(shoppingLists, Modifier.padding(padding), viewModel)
            MainTab.NOTES    -> NotesScreen(notes, Modifier.padding(padding), viewModel)
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
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Lista de la compra", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { viewModel.refresh() }) { Text("Actualizar") }
                }
                Spacer(Modifier.height(Spacing.lg))
                if (lists.isEmpty()) {
                    EmptyStateView(icon = Icons.Outlined.ShoppingCart,
                        title = "Sin listas de la compra",
                        subtitle = "Las listas se generan desde el menú semanal o se sincronizan desde otro dispositivo")
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

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Button(onClick = onBack) { Text("← Volver") }
            Text(list.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        if (pending > 0) {
            Text("$pending pendiente${if (pending != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = Spacing.xs))
        }
        Spacer(Modifier.height(Spacing.md))
        if (items.isEmpty()) {
            EmptyStateView(icon = Icons.Outlined.ShoppingCart,
                title = "Lista vacía",
                subtitle = "Desliza hacia abajo para sincronizar los artículos")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(items, key = { it.id }) { item ->
                    ShoppingItemRow(item, onCheckedChange = { viewModel.checkItem(item, it) })
                }
            }
        }
    }
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
            modifier = Modifier.weight(1f)
        )
    }
}
