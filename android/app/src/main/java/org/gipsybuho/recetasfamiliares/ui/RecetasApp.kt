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
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
            MainTab.RECIPES -> RecipeList(recipes, Modifier.padding(padding), viewModel, viewModel::refresh)
            MainTab.STOCK -> StockList(stockItems, Modifier.padding(padding), viewModel::refresh)
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
    onRefresh: () -> Unit
) {
    Column(modifier.padding(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stock Familiar", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onRefresh) { Text("Actualizar") }
        }
        Spacer(Modifier.height(12.dp))
        if (stockItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin artículos en stock", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(stockItems, key = { it.id }) { item ->
                    StockItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun StockItemCard(item: StockItemEntity) {
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

    Card {
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
