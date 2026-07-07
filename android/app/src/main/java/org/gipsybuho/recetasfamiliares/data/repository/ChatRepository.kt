package org.gipsybuho.recetasfamiliares.data.repository

import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.ChatSocket
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatExportDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatHistoryDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatMessageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SendChatMessageRequestDto
import java.util.UUID

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
        val request = SendChatMessageRequestDto(id = UUID.randomUUID().toString(), body = body)
        return api.sendChatMessage(family, request)
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

    companion object {
        const val PAGE_SIZE = 30
    }
}
