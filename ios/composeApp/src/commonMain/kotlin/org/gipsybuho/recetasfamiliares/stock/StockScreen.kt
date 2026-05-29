package org.gipsybuho.recetasfamiliares.stock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.Spacing
import org.gipsybuho.recetasfamiliares.core.rememberHapticFeedback
import org.gipsybuho.recetasfamiliares.network.StockItemDto
import org.gipsybuho.recetasfamiliares.recipes.AnimatedEmptyState
import org.gipsybuho.recetasfamiliares.sync.SyncRepository
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(repository: StockRepository, syncRepo: SyncRepository) {
    var items        by remember { mutableStateOf<List<StockItemDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf<String?>(null) }
    var editingItem  by remember { mutableStateOf<StockItemDto?>(null) }
    var showCreate   by remember { mutableStateOf(false) }
    val haptic       = rememberHapticFeedback()
    val scope        = rememberCoroutineScope()

    suspend fun reload() {
        runCatching { items = repository.loadStockItems() }
            .onFailure { error = it.message }
    }

    LaunchedEffect(Unit) {
        reload()
        loading = false
    }

    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            error = null
            syncRepo.pullIncremental()
            reload()
            isRefreshing = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { onRefresh() },
            modifier     = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(Spacing.xl)) {
                Text("Stock Familiar", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.xl))

                when {
                    showCreate -> StockForm(
                        item     = null,
                        onSave   = { name, qty, unit, exp, thresh, note ->
                            scope.launch {
                                runCatching { repository.createStockItem(name, qty, unit, exp, thresh, note); reload() }
                                    .onFailure { error = it.message }
                                showCreate = false
                            }
                        },
                        onCancel = { showCreate = false }
                    )
                    editingItem != null -> StockForm(
                        item     = editingItem,
                        onSave   = { name, qty, unit, exp, thresh, note ->
                            scope.launch {
                                runCatching {
                                    repository.updateStockItem(editingItem!!.id, name, qty, unit, exp, thresh, note)
                                    reload()
                                }.onFailure { error = it.message }
                                editingItem = null
                            }
                        },
                        onCancel = { editingItem = null }
                    )
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    error != null && items.isEmpty() -> Box(
                        Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(Spacing.md))
                            OutlinedButton(onClick = { onRefresh() }) { Text("Reintentar") }
                        }
                    }
                    items.isEmpty() -> AnimatedEmptyState(
                        icon     = "🥦",
                        title    = "Stock vacío",
                        subtitle = "Toca + para añadir tu primer artículo"
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        items(items, key = { it.id }) { item ->
                            Box(Modifier.animateItem()) {
                                SwipeToRevealItem(
                                    item     = item,
                                    onTap    = { editingItem = item; haptic.selection() },
                                    onDelete = {
                                        scope.launch {
                                            haptic.error()
                                            runCatching { repository.deleteStockItem(item.id); reload() }
                                                .onFailure { error = it.message }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!showCreate && editingItem == null && !loading) {
            FloatingActionButton(
                onClick  = { showCreate = true; error = null },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl)
            ) { Icon(Icons.Filled.Add, contentDescription = "Nuevo artículo") }
        }
    }
}

@Composable
private fun StockForm(
    item: StockItemDto?,
    onSave: (String, Double?, String?, String?, Double?, String?) -> Unit,
    onCancel: () -> Unit
) {
    var name      by remember(item?.id) { mutableStateOf(item?.name ?: "") }
    var quantity  by remember(item?.id) { mutableStateOf(item?.quantity?.toString() ?: "") }
    var unit      by remember(item?.id) { mutableStateOf(item?.unit ?: "") }
    var expiresAt by remember(item?.id) { mutableStateOf(item?.expiresAt?.take(10) ?: "") }
    var threshold by remember(item?.id) { mutableStateOf(item?.lowStockThreshold?.toString() ?: "") }
    var note      by remember(item?.id) { mutableStateOf(item?.note ?: "") }
    var showAdv   by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(
            if (item == null) "Nuevo artículo" else "Editar artículo",
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedTextField(
            value         = name,
            onValueChange = { name = it },
            label         = { Text("Nombre *") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedTextField(
                value         = quantity,
                onValueChange = { quantity = it },
                label         = { Text("Cantidad") },
                singleLine    = true,
                modifier      = Modifier.weight(1f)
            )
            OutlinedTextField(
                value         = unit,
                onValueChange = { unit = it },
                label         = { Text("Unidad") },
                singleLine    = true,
                modifier      = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value         = expiresAt,
            onValueChange = { expiresAt = it },
            label         = { Text("Caduca (AAAA-MM-DD)") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick  = { showAdv = !showAdv },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showAdv) "▲ Ocultar opciones" else "▼ Opciones avanzadas")
        }
        AnimatedVisibility(showAdv) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                OutlinedTextField(
                    value         = threshold,
                    onValueChange = { threshold = it },
                    label         = { Text("Mínimo stock") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = note,
                    onValueChange = { note = it },
                    label         = { Text("Nota") },
                    minLines      = 2,
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Button(
                onClick  = {
                    if (name.isNotBlank()) onSave(
                        name.trim(),
                        quantity.toDoubleOrNull(),
                        unit.trim().ifBlank { null },
                        expiresAt.trim().ifBlank { null },
                        threshold.toDoubleOrNull(),
                        note.trim().ifBlank { null }
                    )
                },
                enabled  = name.isNotBlank()
            ) { Text("Guardar") }
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

@Composable
private fun SwipeToRevealItem(
    item: StockItemDto,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    val revealPx = 80.dp
    val offsetX  = remember { Animatable(0f) }
    val scope    = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .pointerInput(item.id) {
                detectHorizontalDragGestures(
                    onDragEnd    = {
                        scope.launch {
                            if (offsetX.value < -(revealPx.toPx() / 2)) {
                                offsetX.animateTo(-revealPx.toPx(), tween(200))
                            } else {
                                offsetX.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, tween(200)) } },
                    onDrag       = { change, delta ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx.toPx(), 0f))
                        }
                    }
                )
            }
    ) {
        if (offsetX.value < 0f) {
            Row(
                modifier              = Modifier
                    .fillMaxHeight()
                    .width(revealPx)
                    .align(Alignment.CenterEnd)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable { onDelete() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = Color.White)
            }
        }

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
                trailingContent   = item.lowStockThreshold?.let { threshold ->
                    val isLow = item.quantity != null && item.quantity <= threshold
                    if (isLow) {
                        {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    "Bajo stock",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else null
                }
            )
        }
    }
}
