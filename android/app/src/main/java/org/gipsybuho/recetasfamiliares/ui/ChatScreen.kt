package org.gipsybuho.recetasfamiliares.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatAttachmentDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatMessageDto
import org.gipsybuho.recetasfamiliares.data.repository.CHAT_MAX_BODY_LENGTH
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: RecetasViewModel, onClose: () -> Unit) {
    val messages by viewModel.chatMessages.collectAsState()
    val connected by viewModel.chatConnected.collectAsState()
    val loading by viewModel.chatLoading.collectAsState()
    val hasMoreOlder by viewModel.chatHasMoreOlder.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val myUserId = viewModel.myUserId

    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessageDto?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deletingMessage by remember { mutableStateOf<ChatMessageDto?>(null) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val caption = draft.trim()
            viewModel.sendChatImage(context, uri, caption) {
                if (draft.trim() == caption) {
                    draft = ""
                }
            }
        }
    }

    // Ciclo de vida de la conexion en tiempo real ligado a la pantalla.
    DisposableEffect(Unit) {
        viewModel.openChat()
        onDispose { viewModel.closeChat() }
    }

    // Autoscroll al fondo solo si no interrumpe lectura de historial antiguo.
    var lastId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        val newest = messages.lastOrNull()
        if (newest != null && newest.id != lastId) {
            val previousLastId = lastId
            lastId = newest.id
            val visibleLastIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val isInitialLoad = previousLastId == null
            val isNearBottom = visibleLastIndex >= messages.lastIndex - 2
            val isOwnMessage = newest.authorUserId == myUserId
            if (isInitialLoad || isNearBottom || isOwnMessage) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    // Cargar mas antiguos al llegar arriba del todo.
    LaunchedEffect(listState.firstVisibleItemIndex, hasMoreOlder) {
        if (hasMoreOlder && listState.firstVisibleItemIndex == 0) {
            viewModel.loadOlderChat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar chat")
                    }
                },
                title = {
                    Column {
                        Text("Chat familiar", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (connected) "En línea" else "Sin conexión en tiempo real",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Exportar chat") },
                            onClick = {
                                menuOpen = false
                                viewModel.exportChat { text -> shareChatText(context, text) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Borrar chat para mí") },
                            onClick = {
                                menuOpen = false
                                confirmClear = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading && messages.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    messages.isEmpty() -> {
                        EmptyStateView(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = "Aún no hay mensajes",
                            subtitle = "Este es el chat privado de tu familia. Escribe el primer mensaje para empezar a coordinar la cocina."
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    isMine = message.authorUserId == myUserId,
                                    onEdit = { selected ->
                                        editDraft = selected.body.orEmpty()
                                        editingMessage = selected
                                    },
                                    onDelete = { selected -> deletingMessage = selected }
                                )
                            }
                        }
                    }
                }
            }

            ChatInputBar(
                draft = draft,
                connected = connected,
                onDraftChange = { draft = it.take(CHAT_MAX_BODY_LENGTH) },
                onPickImage = { imagePicker.launch("image/*") },
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        viewModel.sendChat(text) {
                            if (draft.trim() == text) {
                                draft = ""
                            }
                        }
                    }
                }
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Borrar chat para ti") },
            text = { Text("Se ocultará tu historial en este chat. Los demás miembros conservarán el suyo. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.clearChat()
                    confirmClear = false
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancelar") }
            }
        )
    }

    editingMessage?.let { message ->
        val text = editDraft.trim()
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Editar mensaje") },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it.take(CHAT_MAX_BODY_LENGTH) },
                    supportingText = { Text("${editDraft.length}/$CHAT_MAX_BODY_LENGTH") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotEmpty() && text != message.body.orEmpty(),
                    onClick = {
                        viewModel.editChatMessage(message, text) {
                            editingMessage = null
                        }
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancelar") }
            }
        )
    }

    deletingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deletingMessage = null },
            title = { Text("Eliminar mensaje") },
            text = { Text("Se mostrará como mensaje eliminado para todos los miembros.") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deleteChatMessage(message)
                    deletingMessage = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessage = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageDto,
    isMine: Boolean,
    onEdit: (ChatMessageDto) -> Unit,
    onDelete: (ChatMessageDto) -> Unit
) {
    var actionsOpen by remember(message.id) { mutableStateOf(false) }
    val time = formatTime(message.createdAt)
    val attachments = message.attachments.orEmpty()
    val bodyText = message.body?.takeIf { it.isNotBlank() }
    val deletedText = if (message.deleted) "(mensaje eliminado)" else null
    val canDelete = isMine && !message.deleted
    val canEdit = canDelete && bodyText != null
    val attachmentText = when (attachments.size) {
        0 -> null
        1 -> "1 imagen"
        else -> "${attachments.size} imágenes"
    }
    val semanticText = listOfNotNull(deletedText, bodyText, attachmentText)
        .ifEmpty { listOf("Mensaje") }
        .joinToString(", ")
    val speaker = if (isMine) "Tú" else message.authorDisplayName
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$speaker, $time: $semanticText" },
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                if (canDelete) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        IconButton(
                            onClick = { actionsOpen = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Opciones del mensaje")
                        }
                        DropdownMenu(
                            expanded = actionsOpen,
                            onDismissRequest = { actionsOpen = false }
                        ) {
                            if (canEdit) {
                                DropdownMenuItem(
                                    text = { Text("Editar") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        actionsOpen = false
                                        onEdit(message)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Eliminar") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    actionsOpen = false
                                    onDelete(message)
                                }
                            )
                        }
                    }
                }
                if (!isMine) {
                    Text(
                        message.authorDisplayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(Spacing.xs))
                }
                attachments.forEachIndexed { index, attachment ->
                    if (index > 0) Spacer(Modifier.height(Spacing.sm))
                    ChatAttachmentImage(attachment, speaker, bodyText)
                }
                val visibleText = deletedText ?: bodyText
                if (visibleText != null) {
                    if (attachments.isNotEmpty()) Spacer(Modifier.height(Spacing.sm))
                    Text(
                        visibleText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                        fontStyle = if (message.deleted) FontStyle.Italic else FontStyle.Normal
                    )
                }
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ChatAttachmentImage(attachment: ChatAttachmentDto, speaker: String, caption: String?) {
    AsyncImage(
        model = attachment.thumbnailUrl ?: attachment.url,
        contentDescription = if (caption.isNullOrBlank()) {
            "Imagen compartida por $speaker"
        } else {
            "Imagen compartida por $speaker: $caption"
        },
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    draft: String,
    connected: Boolean,
    onDraftChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        val nearLimit = draft.length >= CHAT_MAX_BODY_LENGTH - 160
        val canSend = connected && draft.isNotBlank() && draft.trim().length <= CHAT_MAX_BODY_LENGTH
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                shape = CircleShape,
                color = if (connected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(onClick = onPickImage, enabled = connected) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = if (connected) {
                            "Añadir imagen"
                        } else {
                            "Sin conexión en tiempo real"
                        },
                        tint = if (connected) MaterialTheme.colorScheme.onSecondaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Mensaje…") },
                supportingText = if (nearLimit) {
                    { Text("${draft.length}/$CHAT_MAX_BODY_LENGTH") }
                } else {
                    null
                },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            Surface(
                shape = CircleShape,
                color = if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (canSend) {
                            "Enviar mensaje"
                        } else if (!connected) {
                            "Sin conexión en tiempo real"
                        } else {
                            "Mensaje no listo para enviar"
                        },
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(iso: String): String =
    runCatching {
        OffsetDateTime.parse(iso)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(timeFormatter)
    }.getOrElse { iso }

private fun shareChatText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Exportar chat"))
}
