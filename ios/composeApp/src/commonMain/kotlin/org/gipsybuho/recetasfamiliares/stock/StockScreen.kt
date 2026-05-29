package org.gipsybuho.recetasfamiliares.stock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.core.rememberHapticFeedback
import org.gipsybuho.recetasfamiliares.network.StockItemDto
import org.gipsybuho.recetasfamiliares.sync.SyncRepository
import kotlin.math.roundToInt

@Composable
fun StockScreen(repository: StockRepository, syncRepo: SyncRepository) {
    var items        by remember { mutableStateOf<List<StockItemDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf<String?>(null) }
    val haptic       = rememberHapticFeedback()
    val scope        = rememberCoroutineScope()

    suspend fun loadData() {
        runCatching { items = repository.loadStockItems() }
            .onFailure { error = it.message }
    }

    LaunchedEffect(Unit) {
        loadData()
        loading = false
    }

    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            error = null
            syncRepo.pullIncremental()
            loadData()
            isRefreshing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stock Familiar", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { if (!isRefreshing) onRefresh() }) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onRefresh() }) { Text("Reintentar") }
                }
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Stock vacío", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Gestiona el stock desde Android o Desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    SwipeToRevealItem(
                        item     = item,
                        onTap    = { haptic.selection() },
                        onDelete = { haptic.error() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeToRevealItem(
    item: StockItemDto,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    val revealPx  = 80.dp
    val offsetX   = remember { Animatable(0f) }
    val scope     = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .pointerInput(item.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value < -(revealPx.toPx() / 2)) {
                                offsetX.animateTo(-revealPx.toPx(), tween(200))
                            } else {
                                offsetX.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, tween(200)) } },
                    onDrag = { change, delta ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx.toPx(), 0f))
                        }
                    }
                )
            }
    ) {
        // Botón rojo de eliminar (detrás)
        if (offsetX.value < 0f) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(revealPx)
                    .align(Alignment.CenterEnd)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable { onDelete() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar",
                    tint = Color.White)
            }
        }

        // Contenido del item (encima, desplazado)
        Card(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .clickable { onTap() }
        ) {
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
                        {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text("Bajo stock",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    } else null
                }
            )
        }
    }
}
