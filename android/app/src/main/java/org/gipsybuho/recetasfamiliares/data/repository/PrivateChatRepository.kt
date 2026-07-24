package org.gipsybuho.recetasfamiliares.data.repository

import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.EditPrivateMessageRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateConversationDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageExportDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageHistoryDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SendPrivateMessageRequestDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

const val PRIVATE_CHAT_MAX_BODY_LENGTH = 2_000
const val PRIVATE_CHAT_MAX_IMAGE_ATTACHMENTS = 5

/**
 * Chat privado 1:1 fase Android: envio/historial por REST. El tiempo real llega
 * por el ChatSocket compartido (ver ChatRepository.openRealtime), no aqui.
 */
class PrivateChatRepository(
    private val api: RecetasApi,
    private val sessionStore: SessionStore,
    private val baseUrlProvider: () -> String
) {

    suspend fun listConversations(): List<PrivateConversationDto> =
        api.conversations(requireFamily())

    suspend fun createOrGetConversation(otherUserId: String): PrivateConversationDto =
        api.createOrGetConversation(requireFamily(), otherUserId)

    suspend fun loadHistory(
        conversationId: String,
        before: String? = null,
        limit: Int = PAGE_SIZE
    ): PrivateMessageHistoryDto {
        val history = api.privateMessages(requireFamily(), conversationId, before, limit)
        return history.copy(items = history.items.map(::normalizeAttachments))
    }

    suspend fun send(conversationId: String, body: String): PrivateMessageDto {
        val text = body.trim()
        require(text.isNotEmpty()) { "Private message body is blank" }
        require(text.length <= PRIVATE_CHAT_MAX_BODY_LENGTH) { "Private message body is too long" }
        val request = SendPrivateMessageRequestDto(id = UUID.randomUUID().toString(), body = text)
        return normalizeAttachments(api.sendPrivateMessage(requireFamily(), conversationId, request))
    }

    suspend fun sendImages(
        conversationId: String,
        body: String?,
        images: List<Pair<ByteArray, String>>
    ): PrivateMessageDto {
        val text = body?.trim().orEmpty()
        require(text.length <= PRIVATE_CHAT_MAX_BODY_LENGTH) { "Private message body is too long" }
        require(images.isNotEmpty()) { "At least one private image is required" }
        require(images.size <= PRIVATE_CHAT_MAX_IMAGE_ATTACHMENTS) { "Too many private images" }

        val parts = images.mapIndexed { index, (bytes, contentType) ->
            require(bytes.isNotEmpty()) { "Private image is empty" }
            val safeContentType = normalizeImageContentType(contentType)
            MultipartBody.Part.createFormData(
                "files",
                "dm-image-${index + 1}${extensionFor(safeContentType)}",
                bytes.toRequestBody(safeContentType.toMediaType())
            )
        }
        val id = UUID.randomUUID().toString().toRequestBody(TEXT_PLAIN)
        val bodyPart = text.takeIf { it.isNotEmpty() }?.toRequestBody(TEXT_PLAIN)
        return normalizeAttachments(
            api.sendPrivateImageMessage(requireFamily(), conversationId, id, bodyPart, parts)
        )
    }

    suspend fun edit(conversationId: String, messageId: String, body: String): PrivateMessageDto {
        val text = body.trim()
        require(text.isNotEmpty()) { "Private message body is blank" }
        require(text.length <= PRIVATE_CHAT_MAX_BODY_LENGTH) { "Private message body is too long" }
        return normalizeAttachments(
            api.editPrivateMessage(requireFamily(), conversationId, messageId, EditPrivateMessageRequestDto(text))
        )
    }

    suspend fun delete(conversationId: String, messageId: String): PrivateMessageDto =
        normalizeAttachments(api.deletePrivateMessage(requireFamily(), conversationId, messageId))

    suspend fun clear(conversationId: String) {
        api.clearPrivateConversation(requireFamily(), conversationId)
    }

    suspend fun export(conversationId: String): PrivateMessageExportDto {
        val export = api.exportPrivateConversation(requireFamily(), conversationId)
        return export.copy(messages = export.messages.map(::normalizeAttachments))
    }

    private fun requireFamily(): String =
        sessionStore.familyId ?: throw IllegalStateException("No family in session")

    /** Mismo criterio que ChatRepository.normalizeAttachments, para las rutas de DM. */
    fun normalizeAttachments(message: PrivateMessageDto): PrivateMessageDto {
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

    private fun uploadPathOrNull(raw: String): String? {
        val path = try {
            java.net.URI(raw).path
        } catch (e: Exception) {
            return null
        }
        if (path.isNullOrBlank() || path.contains("..")) return null
        val allowed = path.startsWith("/uploads/dm/") || path.startsWith("/uploads/dm_thumbnails/")
        return if (allowed) path else null
    }

    private fun normalizeImageContentType(contentType: String): String {
        val normalized = contentType.lowercase().trim()
        require(normalized in ALLOWED_IMAGE_TYPES) { "Unsupported private image type" }
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
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
