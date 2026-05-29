package org.gipsybuho.recetasfamiliares.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.Spacing
import org.gipsybuho.recetasfamiliares.core.rememberHapticFeedback
import org.gipsybuho.recetasfamiliares.network.FamilyNoteDto
import org.gipsybuho.recetasfamiliares.recipes.AnimatedEmptyState

@Composable
fun NotesScreen(repository: NoteRepository) {
    var notes        by remember { mutableStateOf<List<FamilyNoteDto>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    var error        by remember { mutableStateOf<String?>(null) }
    var selectedNote by remember { mutableStateOf<FamilyNoteDto?>(null) }
    var editingNote  by remember { mutableStateOf<FamilyNoteDto?>(null) }
    var showCreate   by remember { mutableStateOf(false) }
    val scope        = rememberCoroutineScope()
    val haptic       = rememberHapticFeedback()

    suspend fun reload() {
        runCatching { notes = repository.loadNotes() }.onFailure { error = it.message }
    }

    LaunchedEffect(Unit) {
        reload()
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(Spacing.xl)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null && notes.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                showCreate -> NoteForm(
                    title  = "",
                    body   = "",
                    pinned = false,
                    onSave = { t, b, p ->
                        scope.launch {
                            runCatching { repository.createNote(t, b, p); reload() }
                                .onFailure { error = it.message }
                            showCreate = false
                        }
                    },
                    onCancel = { showCreate = false }
                )
                editingNote != null -> NoteForm(
                    title  = editingNote!!.title,
                    body   = editingNote!!.body,
                    pinned = editingNote!!.pinned,
                    onSave = { t, b, p ->
                        scope.launch {
                            runCatching { repository.updateNote(editingNote!!.id, t, b, p); reload() }
                                .onFailure { error = it.message }
                            editingNote = null
                            selectedNote = null
                        }
                    },
                    onCancel = { editingNote = null }
                )
                selectedNote != null -> NoteDetail(
                    note     = selectedNote!!,
                    onBack   = { selectedNote = null },
                    onEdit   = { editingNote = selectedNote },
                    onDelete = {
                        scope.launch {
                            haptic.error()
                            runCatching { repository.deleteNote(selectedNote!!.id); reload() }
                                .onFailure { error = it.message }
                            selectedNote = null
                        }
                    }
                )
                else -> {
                    Text("Notas Familiares", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(Spacing.lg))
                    if (notes.isEmpty()) {
                        AnimatedEmptyState(
                            icon     = "📝",
                            title    = "Sin notas familiares",
                            subtitle = "Toca + para escribir recuerdos y secretos culinarios"
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            items(notes, key = { it.id }) { note ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().animateItem()
                                        .clickable { selectedNote = note; haptic.selection() }
                                ) {
                                    ListItem(
                                        headlineContent   = {
                                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                                if (note.pinned) Text("📌")
                                                Text(note.title)
                                            }
                                        },
                                        supportingContent = {
                                            val preview = note.body.take(80).replace('\n', ' ')
                                            Text(
                                                if (note.body.length > 80) "$preview…" else preview,
                                                maxLines = 2,
                                                color    = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!showCreate && editingNote == null && selectedNote == null && !loading) {
            FloatingActionButton(
                onClick  = { showCreate = true; error = null },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xl)
            ) { Icon(Icons.Filled.Add, contentDescription = "Nueva nota") }
        }
    }
}

@Composable
private fun NoteForm(
    title: String, body: String, pinned: Boolean,
    onSave: (String, String, Boolean) -> Unit, onCancel: () -> Unit
) {
    var t by remember(title) { mutableStateOf(title) }
    var b by remember(body) { mutableStateOf(body) }
    var p by remember(pinned) { mutableStateOf(pinned) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(
            if (title.isEmpty()) "Nueva nota" else "Editar nota",
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedTextField(
            value         = t,
            onValueChange = { t = it },
            label         = { Text("Título") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value         = b,
            onValueChange = { b = it },
            label         = { Text("Contenido") },
            minLines      = 6,
            modifier      = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Checkbox(checked = p, onCheckedChange = { p = it })
            Text("Fijar nota")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Button(
                onClick  = { if (t.isNotBlank()) onSave(t.trim(), b.trim(), p) },
                enabled  = t.isNotBlank()
            ) { Text("Guardar") }
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

@Composable
private fun NoteDetail(
    note: FamilyNoteDto, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        contentPadding      = PaddingValues(bottom = Spacing.xxl)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    note.title,
                    style    = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (note.pinned) {
            item {
                Text(
                    "📌 Fijada",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        item {
            Text(note.body, style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Editar")
                }
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Eliminar")
                }
            }
        }
    }
}
