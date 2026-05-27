package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity

private enum class MainTab { RECIPES, STOCK }

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
            }
        }
    ) { padding ->
        when (tab) {
            MainTab.RECIPES -> RecipeList(recipes, Modifier.padding(padding), viewModel::refresh)
            MainTab.STOCK -> StockList(stockItems, Modifier.padding(padding), viewModel::refresh)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeList(recipes: List<RecipeEntity>, modifier: Modifier, onRefresh: () -> Unit) {
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    Column(modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Recetas", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onRefresh) { Text("Actualizar") }
        }
        Spacer(Modifier.height(12.dp))
        if (selectedRecipe != null) {
            RecipeDetail(selectedRecipe!!, onBack = { selectedRecipe = null })
        } else {
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
private fun RecipeDetail(recipe: RecipeEntity, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onBack) { Text("Volver") }
        Text(recipe.title, style = MaterialTheme.typography.headlineMedium)
        Text(recipe.description ?: "Sin descripcion")
        Text("Raciones: ${recipe.servings ?: "-"}")
        Text("Preparacion: ${recipe.prepMinutes ?: "-"} min")
        Text("Coccion: ${recipe.cookMinutes ?: "-"} min")
        Text("Dificultad: ${recipe.difficulty ?: "-"}")
    }
}

@Composable
private fun StockList(stockItems: List<StockItemEntity>, modifier: Modifier, onRefresh: () -> Unit) {
    Column(modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Stock", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onRefresh) { Text("Actualizar") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stockItems, key = { it.id }) { item ->
                Card {
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = {
                            Text("${item.quantity ?: "-"} ${item.unit ?: ""} · caduca: ${item.expiresAt ?: "-"}")
                        }
                    )
                }
            }
        }
    }
}
