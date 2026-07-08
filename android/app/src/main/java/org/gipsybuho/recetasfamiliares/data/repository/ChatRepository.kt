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
    private val baseUrl: String,
    private val gson: Gson = Gson()
) {

    val familyId: String? get() = sessionStore.familyId
    val myUserId: String? get() = sessionStore.userId

    suspend fun loadHistory(before: String? = null, limit: Int = PAGE_SIZE): ChatHistoryDto {
        val family = requireFamily()
        return api.chatMessages(family, before, limit)
    }

    suspend fun send(body: String): ChatMessageDto {
        val family = requireFamily()
        val text = body.trim()
        require(text.isNotEmpty()) { "Chat message body is blank" }
        require(text.length <= CHAT_MAX_BODY_LENGTH) { "Chat message body is too long" }
        val request = SendChatMessageRequestDto(id = UUID.randomUUID().toString(), body = text)
        return api.sendChatMessage(family, request)
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
        return api.sendChatImageMessage(family, id, bodyPart, parts)
    }

    suspend fun edit(messageId: String, body: String): ChatMessageDto {
        val family = requireFamily()
        val text = body.trim()
        require(messageId.isNotBlank()) { "Chat message id is blank" }
        require(text.isNotEmpty()) { "Chat message body is blank" }
        require(text.length <= CHAT_MAX_BODY_LENGTH) { "Chat message body is too long" }
        return api.editChatMessage(family, messageId, EditChatMessageRequestDto(text))
    }

    suspend fun delete(messageId: String): ChatMessageDto {
        val family = requireFamily()
        require(messageId.isNotBlank()) { "Chat message id is blank" }
        return api.deleteChatMessage(family, messageId)
    }

    suspend fun clear() {
        api.clearChat(requireFamily())
    }

    suspend fun export(): ChatExportDto {
        return api.exportChat(requireFamily())
    }

    /**
     * Abre la conexion en tiempo real. Devuelve el socket para que el llamador
     * lo cierre al salir del chat. Null si no hay familia en sesion.
     */
    fun openRealtime(
        onMessage: (ChatMessageDto) -> Unit,
        onConnectionChange: (Boolean) -> Unit
    ): ChatSocket? {
        val family = familyId ?: return null
        val socket = ChatSocket(
            httpClient = httpClient,
            baseUrl = baseUrl,
            sessionStore = sessionStore,
            familyId = family,
            gson = gson,
            onMessage = onMessage,
            onConnectionChange = onConnectionChange
        )
        socket.connect()
        return socket
    }

    private fun requireFamily(): String =
        familyId ?: throw IllegalStateException("No family in session")

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
