package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    viewModel: RecetasViewModel,
    initialConversation: PrivateConversationDto? = null,
    onClose: () -> Unit
) {
    var selectedConversation by remember(initialConversation) { mutableStateOf(initialConversation) }
    val conversations by viewModel.conversations.collectAsState()
    val unreadByConversation by viewModel.privateChatUnread.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadConversations()
    }

    val current = selectedConversation
    if (current != null) {
        PrivateChatScreen(
            viewModel = viewModel,
            conversation = current,
            onClose = { selectedConversation = null }
        )
        return
    }

    // Con un detalle de conversacion abierto, esta pantalla hace return antes de
    // componer su propio SnackbarHost, asi que PrivateChatScreen muestra los avisos
    // (mismo patron que ChatScreen respecto al Scaffold de RecetasApp).
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar conversaciones")
                    }
                },
                title = { Text("Conversaciones") }
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyStateView(
                    icon = Icons.Outlined.Lock,
                    title = "Sin conversaciones todavía",
                    subtitle = "Escribe a un miembro de la familia desde Perfil para empezar una conversación privada."
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(conversations, key = { it.conversationId }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        unreadCount = unreadByConversation[conversation.conversationId] ?: 0,
                        onClick = { selectedConversation = conversation }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: PrivateConversationDto,
    unreadCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(end = Spacing.md).size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!conversation.otherUserAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = conversation.otherUserAvatarUrl,
                        contentDescription = "Foto de ${conversation.otherUserDisplayName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        conversation.otherUserDisplayName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(conversation.otherUserDisplayName, style = MaterialTheme.typography.bodyLarge)
            conversation.lastMessagePreview?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (unreadCount > 0) {
            Badge { Text(if (unreadCount > 9) "9+" else "$unreadCount") }
        }
    }
}
