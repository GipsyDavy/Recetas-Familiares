package org.gipsybuho.recetasfamiliares.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.SubcomposeAsyncImage
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateAttachmentDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
import org.gipsybuho.recetasfamiliares.data.repository.PRIVATE_CHAT_MAX_BODY_LENGTH

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    viewModel: RecetasViewModel,
    conversation: PrivateConversationDto,
    onClose: () -> Unit
) {
    val messages by viewModel.privateMessages.collectAsState()
    val connected by viewModel.privateChatConnected.collectAsState()
    val loading by viewModel.privateChatLoading.collectAsState()
    val hasMoreOlder by viewModel.privateChatHasMoreOlder.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val myUserId = viewModel.myUserId

    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<PrivateMessageDto?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deletingMessage by remember { mutableStateOf<PrivateMessageDto?>(null) }
    var viewingAttachment by remember { mutableStateOf<PrivateAttachmentDto?>(null) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Esta pantalla se muestra con return propio antes del Scaffold de RecetasApp/
    // ConversationsScreen, asi que necesita su propio host para que los avisos
    // (guardar imagen, borrar, errores de export) sean visibles mientras esta abierta.
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val caption = draft.trim()
            viewModel.sendPrivateImage(context, uri, caption) {
                if (draft.trim() == caption) {
                    draft = ""
                }
            }
        }
    }

    // Ciclo de vida de la conexion en tiempo real ligado a esta conversacion:
    // se abre al entrar y se cierra siempre al salir (cambio de conversacion o cierre).
    DisposableEffect(conversation.conversationId) {
        viewModel.openPrivateChat(conversation.conversationId)
        onDispose { viewModel.closePrivateChat() }
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
            viewModel.loadOlderPrivateChat()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar conversacion")
                    }
                },
                title = {
                    Column {
                        Text(conversation.otherUserDisplayName, style = MaterialTheme.typography.titleMedium)
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
                            text = { Text("Exportar conversación") },
                            onClick = {
                                menuOpen = false
                                viewModel.exportPrivateChat { text -> shareChatText(context, text) }
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
                            icon = Icons.Outlined.Lock,
                            title = "Aún no hay mensajes",
                            subtitle = "Esta es tu conversación privada con ${conversation.otherUserDisplayName}. Escribe el primer mensaje para empezar."
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
                                PrivateMessageBubble(
                                    message = message,
                                    isMine = message.authorUserId == myUserId,
                                    onEdit = { selected ->
                                        editDraft = selected.body.orEmpty()
                                        editingMessage = selected
                                    },
                                    onDelete = { selected -> deletingMessage = selected },
                                    onImageClick = { attachment -> viewingAttachment = attachment }
                                )
                            }
                        }
                    }
                }
            }

            PrivateChatInputBar(
                draft = draft,
                connected = connected,
                onDraftChange = { draft = it.take(PRIVATE_CHAT_MAX_BODY_LENGTH) },
                onPickImage = { imagePicker.launch("image/*") },
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        viewModel.sendPrivateMessage(text) {
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
            text = { Text("Se ocultará tu historial en esta conversación. La otra persona conservará el suyo. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.clearPrivateChat()
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
                    onValueChange = { editDraft = it.take(PRIVATE_CHAT_MAX_BODY_LENGTH) },
                    supportingText = { Text("${editDraft.length}/$PRIVATE_CHAT_MAX_BODY_LENGTH") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotEmpty() && text != message.body.orEmpty(),
                    onClick = {
                        viewModel.editPrivateMessage(message, text) {
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
            text = { Text("Se mostrará como mensaje eliminado para ambos.") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deletePrivateMessage(message)
                    deletingMessage = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessage = null }) { Text("Cancelar") }
            }
        )
    }

    viewingAttachment?.let { attachment ->
        PrivateChatImageViewer(
            attachment = attachment,
            onDismiss = { viewingAttachment = null },
            onSave = { viewModel.saveChatImageToGallery(context, attachment.url) }
        )
    }
}

@Composable
private fun PrivateMessageBubble(
    message: PrivateMessageDto,
    isMine: Boolean,
    onEdit: (PrivateMessageDto) -> Unit,
    onDelete: (PrivateMessageDto) -> Unit,
    onImageClick: (PrivateAttachmentDto) -> Unit
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
                    PrivateAttachmentImage(attachment, speaker, bodyText, onClick = { onImageClick(attachment) })
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
private fun PrivateAttachmentImage(
    attachment: PrivateAttachmentDto,
    speaker: String,
    caption: String?,
    onClick: () -> Unit
) {
    val base = if (caption.isNullOrBlank()) {
        "Imagen compartida por $speaker"
    } else {
        "Imagen compartida por $speaker: $caption"
    }
    SubcomposeAsyncImage(
        model = attachment.thumbnailUrl ?: attachment.url,
        contentDescription = "$base. Toca para ampliar.",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        loading = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        },
        error = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Imagen no disponible",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/**
 * Visor a tamaño completo del adjunto (imagen original) con opción de guardar en
 * la galería. En API < 29 solicita WRITE_EXTERNAL_STORAGE antes de descargar.
 */
@Composable
private fun PrivateChatImageViewer(
    attachment: PrivateAttachmentDto,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onSave() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            SubcomposeAsyncImage(
                model = attachment.url,
                contentDescription = "Imagen a tamaño completo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(Spacing.md),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No se pudo cargar la imagen", color = Color.White)
                    }
                }
            )
            Row(
                Modifier.fillMaxWidth().padding(Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                }
                TextButton(
                    onClick = {
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            onSave()
                        }
                    }
                ) {
                    Text("Guardar", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivateChatInputBar(
    draft: String,
    connected: Boolean,
    onDraftChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        val nearLimit = draft.length >= PRIVATE_CHAT_MAX_BODY_LENGTH - 160
        val canSend = connected && draft.isNotBlank() && draft.trim().length <= PRIVATE_CHAT_MAX_BODY_LENGTH
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
                    { Text("${draft.length}/$PRIVATE_CHAT_MAX_BODY_LENGTH") }
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
