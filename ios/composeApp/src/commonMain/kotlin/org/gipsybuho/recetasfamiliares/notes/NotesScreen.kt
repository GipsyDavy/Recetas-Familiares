package org.gipsybuho.recetasfamiliares.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.core.rememberHapticFeedback
import org.gipsybuho.recetasfamiliares.network.FamilyNoteDto

@Composable
fun NotesScreen(repository: NoteRepository) {
    var notes   by remember { mutableStateOf<List<FamilyNoteDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }
    val haptic  = rememberHapticFeedback()

    LaunchedEffect(Unit) {
        runCatching { notes = repository.loadNotes() }
            .onFailure { error = it.message }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Notas Familiares", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            notes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sin notas aún", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Escribe recuerdos y secretos culinarios desde Android o Desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { note ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { haptic.selection() }) {
                        ListItem(
                            headlineContent   = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (note.pinned) Text("📌")
                                    Text(note.title)
                                }
                            },
                            supportingContent = {
                                val preview = note.body.take(80).replace('\n', ' ')
                                Text(if (note.body.length > 80) "$preview…" else preview,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        )
                    }
                }
            }
        }
    }
}
