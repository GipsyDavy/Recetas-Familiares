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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import org.gipsybuho.recetasfamiliares.R
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesScreen(
    notes: List<FamilyNoteEntity>,
    modifier: Modifier,
    viewModel: RecetasViewModel
) {
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedNote by remember { mutableStateOf<FamilyNoteEntity?>(null) }
    var editingNote by remember { mutableStateOf<FamilyNoteEntity?>(null) }
    var showCreateForm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val haptic         = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    var pendingDeleteNote by remember { mutableStateOf<FamilyNoteEntity?>(null) }
    var pendingNoteSwipeState by remember { mutableStateOf<SwipeToDismissBoxState?>(null) }
    val filtered = if (query.isBlank()) notes
        else notes.filter {
            it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
        }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refresh() }, modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(Spacing.xl)) {
            when {
                showCreateForm -> NoteForm(
                    initialTitle = "", initialBody = "", initialPinned = false,
                    onSave = { title, body, pinned ->
                        viewModel.createNote(title, body, pinned) { error = it }
                        showCreateForm = false
                    },
                    onCancel = { showCreateForm = false }
                )
                editingNote != null -> NoteForm(
                    initialTitle = editingNote!!.title, initialBody = editingNote!!.body,
                    initialPinned = editingNote!!.pinned,
                    onSave = { title, body, pinned ->
                        viewModel.updateNote(editingNote!!, title, body, pinned) { error = it }
                        editingNote = null; selectedNote = null
                    },
                    onCancel = { editingNote = null }
                )
                selectedNote != null -> NoteDetail(
                    note = selectedNote!!,
                    onBack = { selectedNote = null },
                    onEdit = { editingNote = selectedNote },
                    onDelete = { viewModel.deleteNote(selectedNote!!); selectedNote = null }
                )
                else -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Notas familiares", style = MaterialTheme.typography.headlineSmall)
                        IconButton(onClick = { showCreateForm = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Nueva nota")
                        }
                    }
                    error?.let {
                        Spacer(Modifier.height(Spacing.md))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(Spacing.md))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Buscar notas...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Borrar") } }
                        } else null,
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Spacing.md))
                    if (notes.isEmpty() && isRefreshing) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            repeat(4) { SkeletonNoteCard() }
                        }
                    } else Crossfade(targetState = filtered.isEmpty(), label = "notesListState") { isEmpty ->
                        if (isEmpty) {
                            if (query.isBlank()) {
                                LottieEmptyStateView(
                                    lottieRes   = R.raw.lottie_empty_list,
                                    title       = "Sin notas familiares",
                                    subtitle    = "Escribe recuerdos, anécdotas y secretos culinarios de vuestra familia",
                                    actionLabel = "Nueva nota",
                                    onAction    = { showCreateForm = true }
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Sin resultados para \"$query\"",
                                        color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                items(filtered, key = { it.id }) { note ->
                                    val swipeState = rememberSwipeToDismissBoxState()
                                    LaunchedEffect(swipeState.currentValue) {
                                        if (swipeState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                            pendingDeleteNote = note
                                            pendingNoteSwipeState = swipeState
                                        }
                                    }
                                    SwipeToDismissBox(
                                        state = swipeState,
                                        modifier = Modifier.animateItem(),
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
                                        NoteCard(note, onClick = { selectedNote = note; error = null })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!showCreateForm && editingNote == null && selectedNote == null) {
            FloatingActionButton(
                onClick = { showCreateForm = true; error = null },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl)
            ) { Icon(Icons.Filled.Add, contentDescription = "Nueva nota") }
        }
        }
    }

    pendingDeleteNote?.let { note ->
        AlertDialog(
            onDismissRequest = {
                scope.launch { pendingNoteSwipeState?.reset() }
                pendingDeleteNote = null; pendingNoteSwipeState = null
            },
            title = { Text("¿Eliminar nota?") },
            text = { Text("\"${note.title}\" se eliminará de las notas familiares.") },
            confirmButton = {
                TextButton(onClick = {
                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deleteNote(note)
                    pendingDeleteNote = null; pendingNoteSwipeState = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { pendingNoteSwipeState?.reset() }
                    pendingDeleteNote = null; pendingNoteSwipeState = null
                }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteCard(note: FamilyNoteEntity, onClick: () -> Unit) {
    Card(onClick = onClick) {
        ListItem(
            headlineContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    if (note.pinned) Text("📌", style = MaterialTheme.typography.bodyMedium)
                    Text(note.title)
                }
            },
            supportingContent = {
                val preview = note.body.take(80).replace('\n', ' ')
                Text(if (note.body.length > 80) "$preview…" else preview,
                    maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = note.recipeTitle?.let {
                { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
            }
        )
    }
}

@Composable
internal fun NoteDetail(note: FamilyNoteEntity, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(note.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (note.pinned) MetaChip("📌 Fijada")
                note.recipeTitle?.let { MetaChip(it) }
            }
        }
        item { Text(note.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
        item {
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
}

@Composable
internal fun NoteForm(
    initialTitle: String, initialBody: String, initialPinned: Boolean,
    onSave: (String, String, Boolean) -> Unit, onCancel: () -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    var pinned by remember(initialPinned) { mutableStateOf(initialPinned) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(if (initialTitle.isEmpty()) "Nueva nota" else "Editar nota", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = title, onValueChange = { title = it },
            label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = body, onValueChange = { body = it },
            label = { Text("Contenido") }, minLines = 6, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Checkbox(checked = pinned, onCheckedChange = { pinned = it })
            Text("Fijar nota")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Button(onClick = { if (title.isNotBlank()) onSave(title.trim(), body.trim(), pinned) },
                enabled = title.isNotBlank()) { Text("Guardar") }
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

@Composable
private fun SkeletonNoteCard() {
    val alpha by rememberInfiniteTransition(label = "shimmer")
        .animateFloat(initialValue = 0.25f, targetValue = 0.65f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha)))
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Box(modifier = Modifier.fillMaxWidth(0.9f).height(12.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
                    Box(modifier = Modifier.fillMaxWidth(0.7f).height(12.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
                }
            }
        )
    }
}
