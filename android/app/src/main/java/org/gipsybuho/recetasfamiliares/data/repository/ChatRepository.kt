package org.gipsybuho.recetasfamiliares.data.repository

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.ChatSocket
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatExportDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatHistoryDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatMessageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.EditChatMessageRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateInboxPingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SendChatMessageRequestDto
import java.util.UUID

const val CHAT_MAX_BODY_LENGTH = 2_000
const val CHAT_MAX_IMAGE_ATTACHMENTS = 5

/**
 * Chat familiar fase 1: envio/historial por REST y recepcion en tiempo real via
 * WebSocket/STOMP. Sin cola offline (fase 1): el envio requiere red.
 */
class ChatRepository(
    private val api: RecetasApi,
    private val httpClient: OkHttpClient,
    private val sessionStore: SessionStore,
    private val baseUrlProvider: () -> String,
    private val gson: Gson = Gson()
) {

    val familyId: String? get() = sessionStore.familyId
    val myUserId: String? get() = sessionStore.userId

    suspend fun loadHistory(before: String? = null, limit: Int = PAGE_SIZE): ChatHistoryDto {
        val family = requireFamily()
        val history = api.chatMessages(family, before, limit)
        return history.copy(items = history.items.map(::normalizeAttachments))
    }

    suspend fun send(body: String): ChatMessageDto {
        val family = requireFamily()
        val text = body.trim()
        require(text.isNotEmpty()) { "Chat message body is blank" }
        require(text.length <= CHAT_MAX_BODY_LENGTH) { "Chat message body is too long" }
        val request = SendChatMessageRequestDto(id = UUID.randomUUID().toString(), body = text)
        return normalizeAttachments(api.sendChatMessage(family, request))
    }

    suspend fun sendImages(
        body: String?,
        images: List<Pair<ByteArray, String>>
    ): ChatMessageDto {
        val family = requireFamily()
        val text = body?.trim().orEmpty()
        require(text.length <= CHAT_MAX_BODY_LENGTH) { "Chat message body is too long" }
        require(images.isNotEmpty()) { "At least one chat image is required" }
        require(images.size <= CHAT_MAX_IMAGE_ATTACHMENTS) { "Too many chat images" }

        val parts = images.mapIndexed { index, (bytes, contentType) ->
            require(bytes.isNotEmpty()) { "Chat image is empty" }
            val safeContentType = normalizeImageContentType(contentType)
            MultipartBody.Part.createFormData(
                "files",
                "chat-image-${index + 1}${extensionFor(safeContentType)}",
                bytes.toRequestBody(safeContentType.toMediaType())
            )
        }
        val id = UUID.randomUUID().toString().toRequestBody(TEXT_PLAIN)
        val bodyPart = text.takeIf { it.isNotEmpty() }?.toRequestBody(TEXT_PLAIN)
        return normalizeAttachments(api.sendChatImageMessage(family, id, bodyPart, parts))
    }

    suspend fun edit(messageId: String, body: String): ChatMessageDto {
        val family = requireFamily()
        val text = body.trim()
        require(messageId.isNotBlank()) { "Chat message id is blank" }
        require(text.isNotEmpty()) { "Chat message body is blank" }
        require(text.length <= CHAT_MAX_BODY_LENGTH) { "Chat message body is too long" }
        return normalizeAttachments(api.editChatMessage(family, messageId, EditChatMessageRequestDto(text)))
    }

    suspend fun delete(messageId: String): ChatMessageDto {
        val family = requireFamily()
        require(messageId.isNotBlank()) { "Chat message id is blank" }
        return normalizeAttachments(api.deleteChatMessage(family, messageId))
    }

    suspend fun clear() {
        api.clearChat(requireFamily())
    }

    suspend fun export(): ChatExportDto {
        val export = api.exportChat(requireFamily())
        return export.copy(messages = export.messages.map(::normalizeAttachments))
    }

    /**
     * Abre la conexion en tiempo real. Devuelve el socket para que el llamador
     * lo cierre al salir del chat. Null si no hay familia en sesion.
     */
    fun openRealtime(
        onMessage: (ChatMessageDto) -> Unit,
        onConnectionChange: (Boolean) -> Unit,
        onPresenceUpdate: (Set<String>) -> Unit,
        conversationId: String? = null,
        onInboxPing: (PrivateInboxPingDto) -> Unit = {},
        onPrivateMessage: (PrivateMessageDto) -> Unit = {}
    ): ChatSocket? {
        val family = familyId ?: return null
        val user = myUserId ?: return null
        val socket = ChatSocket(
            httpClient = httpClient,
            baseUrl = baseUrlProvider(),
            sessionStore = sessionStore,
            familyId = family,
            myUserId = user,
            gson = gson,
            onMessage = { msg -> onMessage(normalizeAttachments(msg)) },
            onConnectionChange = onConnectionChange,
            onPresenceUpdate = onPresenceUpdate,
            conversationId = conversationId,
            onInboxPing = onInboxPing,
            onPrivateMessage = { msg -> onPrivateMessage(normalizePrivateMessage(msg)) }
        )
        socket.connect()
        return socket
    }

    /** Mismo criterio que normalizeAttachments, para adjuntos de chat privado (rutas /uploads/dm/). */
    private fun normalizePrivateMessage(message: PrivateMessageDto): PrivateMessageDto {
        val attachments = message.attachments
        if (attachments.isNullOrEmpty()) return message
        return message.copy(
            attachments = attachments.map { attachment ->
                attachment.copy(
                    url = rewritePrivateUploadUrl(attachment.url),
                    thumbnailUrl = attachment.thumbnailUrl?.let(::rewritePrivateUploadUrl)
                )
            }
        )
    }

    private fun rewritePrivateUploadUrl(raw: String): String {
        val path = try {
            java.net.URI(raw).path
        } catch (e: Exception) {
            return raw
        }
        if (path.isNullOrBlank() || path.contains("..")) return raw
        val allowed = path.startsWith("/uploads/dm/") || path.startsWith("/uploads/dm_thumbnails/")
        return if (allowed) baseUrlProvider().trimEnd('/') + path else raw
    }

    private fun requireFamily(): String =
        familyId ?: throw IllegalStateException("No family in session")

    /**
     * Reescribe el origen de las URLs de adjunto de uploads al host del API que
     * ve este cliente. El backend las genera con app.upload.base-url (por defecto
     * http:localhost:8080), inalcanzable y de host distinto al API desde el
     * emulador o dispositivo: sin esta reescritura el AuthInterceptor no adjunta
     * el Bearer (host distinto) y la imagen no carga. Las URLs externas que no
     * apuntan a la ruta de uploads se dejan intactas.
     */
    private fun normalizeAttachments(message: ChatMessageDto): ChatMessageDto {
        val attachments = message.attachments
        if (attachments.isNullOrEmpty()) return message
        return message.copy(
            attachments = attachments.map { attachment ->
                attachment.copy(
                    url = rewriteUploadUrl(attachment.url),
                    thumbnailUrl = attachment.thumbnailUrl?.let(::rewriteUploadUrl)
                )
            }
        )
    }

    private fun rewriteUploadUrl(raw: String): String {
        val path = uploadPathOrNull(raw) ?: return raw
        return baseUrlProvider().trimEnd('/') + path
    }

    // Extrae solo el path (sin query ni fragment) y lo acepta unicamente si
    // apunta a los directorios de adjuntos del chat. Evita reescrituras falsas
    // por una URL externa con /uploads/ en el query y bloquea traversal (..).
    private fun uploadPathOrNull(raw: String): String? {
        val path = try {
            java.net.URI(raw).path
        } catch (e: Exception) {
            return null
        }
        if (path.isNullOrBlank() || path.contains("..")) return null
        val allowed = path.startsWith("/uploads/chat/") ||
            path.startsWith("/uploads/chat_thumbnails/")
        return if (allowed) path else null
    }

    private fun normalizeImageContentType(contentType: String): String {
        val normalized = contentType.lowercase().trim()
        require(normalized in ALLOWED_CHAT_IMAGE_TYPES) { "Unsupported chat image type" }
        return normalized
    }

    private fun extensionFor(contentType: String): String = when (contentType) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        else -> ".jpg"
    }

    companion object {
        const val PAGE_SIZE = 30
        private val TEXT_PLAIN = "text/plain".toMediaType()
        private val ALLOWED_CHAT_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
