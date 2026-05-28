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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
    val filtered = if (query.isBlank()) notes
        else notes.filter {
            it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
        }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refresh() }, modifier = modifier) {
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
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Notas familiares", style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = { showCreateForm = true }) { Text("Nueva nota") }
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
                    if (filtered.isEmpty()) {
                        if (query.isBlank()) {
                            EmptyStateView(icon = Icons.Outlined.Description,
                                title = "Sin notas familiares",
                                subtitle = "Escribe recuerdos, anécdotas y secretos culinarios de vuestra familia",
                                actionLabel = "Nueva nota", onAction = { showCreateForm = true })
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Sin resultados para \"$query\"",
                                    color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            items(filtered, key = { it.id }) { note ->
                                NoteCard(note, onClick = { selectedNote = note; error = null })
                            }
                        }
                    }
                }
            }
        }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(onClick = onBack) { Text("← Volver") }
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
                Button(onClick = onEdit) { Text("Editar") }
                OutlinedButton(onClick = onDelete) { Text("Eliminar") }
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
