package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockList(
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
    var sortByExpiry by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pendingDeleteItem by remember { mutableStateOf<StockItemEntity?>(null) }
    var pendingDismissState by remember { mutableStateOf<SwipeToDismissBoxState?>(null) }
    val filtered = stockItems
        .let { list -> if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) } }
        .let { list -> if (sortByExpiry) list.sortedBy { it.expiresAt ?: "9999-99-99" } else list }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refresh() }, modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(Spacing.xl)) {
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
                            editingItem = null; selectedItem = null
                        },
                        onCancel = { editingItem = null }
                    )
                    selectedItem != null -> StockDetail(
                        item = selectedItem!!,
                        onBack = { selectedItem = null },
                        onEdit = { editingItem = selectedItem },
                        onDelete = { viewModel.deleteStockItem(selectedItem!!); selectedItem = null }
                    )
                    else -> {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Stock Familiar", style = MaterialTheme.typography.headlineSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = { PlainTooltip { Text(if (sortByExpiry) "Ordenando por caducidad (activo)" else "Ordenar por caducidad") } },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(onClick = { sortByExpiry = !sortByExpiry }) {
                                        Icon(Icons.Filled.Sort, contentDescription = "Ordenar por caducidad",
                                            tint = if (sortByExpiry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = { PlainTooltip { Text("Actualizar") } },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(onClick = { viewModel.refresh() }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                                    }
                                }
                            }
                        }
                        error?.let {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(Spacing.md))
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Buscar en stock...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = if (query.isNotEmpty()) {
                                { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Borrar") } }
                            } else null,
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.md))
                        if (filtered.isEmpty()) {
                            if (query.isBlank()) {
                                EmptyStateView(icon = Icons.Outlined.Inventory2,
                                    title = "Stock vacío",
                                    subtitle = "Registra los ingredientes de casa para controlar caducidades y bajo stock",
                                    actionLabel = "Añadir primer artículo", onAction = { showCreateForm = true })
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Sin resultados para \"$query\"",
                                        color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                items(filtered, key = { it.id }) { item ->
                                    val swipeState = rememberSwipeToDismissBoxState()
                                    LaunchedEffect(swipeState.currentValue) {
                                        if (swipeState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                            pendingDeleteItem = item
                                            pendingDismissState = swipeState
                                        }
                                    }
                                    SwipeToDismissBox(
                                        state = swipeState,
                                        enableDismissFromStartToEnd = false,
                                        backgroundContent = {
                                            Box(
                                                Modifier.fillMaxSize()
                                                    .clip(MaterialTheme.shapes.medium)
                                                    .background(MaterialTheme.colorScheme.errorContainer),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(horizontal = Spacing.xl),
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    ) {
                                        StockItemCard(item, onClick = { selectedItem = item; error = null })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!showCreateForm && editingItem == null && selectedItem == null) {
                FloatingActionButton(
                    onClick = { showCreateForm = true; error = null },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl)
                ) { Icon(Icons.Filled.Add, contentDescription = "Nuevo item de stock") }
            }
        }
    }

    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                scope.launch { pendingDismissState?.reset() }
                pendingDeleteItem = null; pendingDismissState = null
            },
            title = { Text("¿Eliminar ítem?") },
            text = { Text("\"${item.name}\" se eliminará del stock familiar.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deleteStockItem(item)
                    pendingDeleteItem = null; pendingDismissState = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { pendingDismissState?.reset() }
                    pendingDeleteItem = null; pendingDismissState = null
                }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockItemCard(item: StockItemEntity, onClick: () -> Unit = {}) {
    val expiryDays = remember(item.expiresAt) {
        item.expiresAt?.let { dateStr ->
            runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(dateStr.substring(0, 10))) }.getOrNull()
        }
    }
    val isLowStock = item.lowStockThreshold != null && item.quantity != null && item.quantity <= item.lowStockThreshold
    val targetExpiryColor = when {
        expiryDays != null && expiryDays <= 3 -> MaterialTheme.colorScheme.error
        expiryDays != null && expiryDays <= 7 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val expiryColor by animateColorAsState(targetValue = targetExpiryColor, label = "expiryColor")
    Card(onClick = onClick) {
        ListItem(
            headlineContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name)
                    if (isLowStock) {
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.errorContainer) {
                            Text("Bajo stock", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs))
                        }
                    }
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
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
internal fun StockDetail(item: StockItemEntity, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val isLowStock = item.lowStockThreshold != null && item.quantity != null && item.quantity <= item.lowStockThreshold
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(item.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val qty = "${item.quantity ?: "—"} ${item.unit ?: ""}".trim()
            MetaChip(qty)
            item.expiresAt?.let { MetaChip("Caduca: ${it.take(10)}") }
            item.lowStockThreshold?.let { MetaChip(if (isLowStock) "⚠ Bajo stock (mín: $it)" else "Mín: $it") }
        }
        item.note?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Button(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Editar")
            }
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Eliminar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockForm(
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
    var showAdvanced by remember { mutableStateOf(initial?.lowStockThreshold != null || !initial?.note.isNullOrEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.expiresAt?.take(10)?.let {
            runCatching { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
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
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = datePickerState) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(if (initial == null) "Nuevo item de stock" else "Editar item de stock", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = name, onValueChange = { name = it; nameError = false },
            label = { Text("Nombre *") }, isError = nameError, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedTextField(value = quantity, onValueChange = { quantity = it },
                label = { Text("Cantidad") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = unit, onValueChange = { unit = it },
                label = { Text("Unidad") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            value = if (expiresAt.isEmpty()) "" else "Caduca: $expiresAt",
            onValueChange = {}, label = { Text("Fecha de caducidad") }, readOnly = true,
            trailingIcon = { TextButton(onClick = { showDatePicker = true }) { Text("Seleccionar") } },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showAdvanced) "Ocultar avanzado ▲" else "Mostrar avanzado ▼")
        }
        if (showAdvanced) {
            OutlinedTextField(value = lowStockThreshold, onValueChange = { lowStockThreshold = it },
                label = { Text("Umbral de stock bajo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it },
                label = { Text("Notas") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Button(onClick = {
                if (name.isBlank()) { nameError = true; return@Button }
                onSave(name.trim(), quantity.toDoubleOrNull(), unit.trim().ifBlank { null },
                    lowStockThreshold.toDoubleOrNull(), expiresAt.ifBlank { null }, note.trim().ifBlank { null })
            }) { Text(if (initial == null) "Guardar" else "Guardar cambios") }
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}
