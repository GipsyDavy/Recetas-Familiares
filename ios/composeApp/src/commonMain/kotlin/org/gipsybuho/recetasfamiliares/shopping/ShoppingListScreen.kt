package org.gipsybuho.recetasfamiliares.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.core.rememberHapticFeedback
import org.gipsybuho.recetasfamiliares.network.ShoppingListDto
import org.gipsybuho.recetasfamiliares.network.ShoppingListItemDto

@Composable
fun ShoppingListScreen(repository: ShoppingListRepository) {
    var lists   by remember { mutableStateOf<List<ShoppingListDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ShoppingListDto?>(null) }

    LaunchedEffect(Unit) {
        runCatching { lists = repository.loadLists() }
            .onFailure { error = it.message }
        loading = false
    }

    if (selected != null) {
        ShoppingItemsScreen(list = selected!!, repository = repository, onBack = { selected = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Lista de la compra", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            lists.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("Sin listas de la compra", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Crea listas desde Android o Desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            else -> {
                val haptic = rememberHapticFeedback()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lists, key = { it.id }) { list ->
                        Card(modifier = Modifier.fillMaxWidth().clickable {
                            haptic.selection()
                            selected = list
                        }) {
                            ListItem(
                                headlineContent   = { Text(list.name) },
                                supportingContent = list.note?.let { note ->
                                    { Text(note, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                },
                                trailingContent   = {
                                    if (list.completed) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = "Completada",
                                            tint = MaterialTheme.colorScheme.primary)
                                    }
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
private fun ShoppingItemsScreen(
    list: ShoppingListDto,
    repository: ShoppingListRepository,
    onBack: () -> Unit
) {
    var items   by remember { mutableStateOf<List<ShoppingListItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(list.id) {
        runCatching { items = repository.loadItems(list.id) }
            .onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier           = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment  = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(list.name, style = MaterialTheme.typography.titleLarge)
        }

        HorizontalDivider()

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Lista vacía", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val pending   = items.count { !it.checked }
                val completed = items.count { it.checked }
                Text(
                    "$pending pendientes · $completed completados",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        ShoppingItemRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(item: ShoppingListItemDto) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(checked = item.checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(
                text  = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.checked) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null
            )
            val qty = buildString {
                item.quantity?.let { append("%.1f".format(it)) }
                item.unit?.let { append(" $it") }
            }
            if (qty.isNotBlank()) {
                Text(qty, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
