package org.gipsybuho.recetasfamiliares.stock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.network.StockItemDto

@Composable
fun StockScreen(repository: StockRepository) {
    var items   by remember { mutableStateOf<List<StockItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { items = repository.loadStockItems() }
            .onFailure { error = it.message }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Stock Familiar", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Stock vacío", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Gestiona el stock desde Android o Desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent   = { Text(item.name) },
                            supportingContent = {
                                val qty = buildString {
                                    item.quantity?.let { append("%.1f".format(it)) }
                                    item.unit?.let { append(" $it") }
                                    item.expiresAt?.let { append(" · Caduca: ${it.take(10)}") }
                                }
                                if (qty.isNotBlank()) Text(qty)
                            },
                            trailingContent = item.lowStockThreshold?.let { threshold ->
                                val isLow = item.quantity != null && item.quantity <= threshold
                                if (isLow) {
                                    { Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text("Bajo stock",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    } }
                                } else null
                            }
                        )
                    }
                }
            }
        }
    }
}
