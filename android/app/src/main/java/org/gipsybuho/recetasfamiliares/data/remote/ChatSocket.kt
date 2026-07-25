package org.gipsybuho.recetasfamiliares.data.remote

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatMessageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyActivityPingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PresenceResponseDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateInboxPingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PrivateMessageDto

/**
 * Cliente STOMP minimo sobre el WebSocket nativo de OkHttp (sin dependencias
 * nuevas). Solo lo necesario para el chat fase 1:
 *  - CONNECT con el JWT en la cabecera Authorization del frame (no en la URL).
 *  - SUBSCRIBE al topic de la familia tras recibir CONNECTED.
 *  - Recibir frames MESSAGE (JSON de ChatMessageDto) y entregarlos.
 *
 * La entrega en tiempo real es unidireccional: el envio va por REST. Si la
 * conexion falla o se cierra, onConnectionChange(false) permite al llamador
 * degradar a polling.
 */
class ChatSocket(
    private val httpClient: OkHttpClient,
    baseUrl: String,
    private val sessionStore: SessionStore,
    private val familyId: String,
    private val myUserId: String,
    private val gson: Gson,
    private val onMessage: (ChatMessageDto) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit,
    private val onPresenceUpdate: (Set<String>) -> Unit,
    private val conversationId: String? = null,
    private val onInboxPing: (PrivateInboxPingDto) -> Unit = {},
    private val onPrivateMessage: (PrivateMessageDto) -> Unit = {},
    private val onActivityPing: (FamilyActivityPingDto) -> Unit = {}
) {

    private val wsUrl: String = toWebSocketUrl(baseUrl)
    private val topic: String = "/topic/families/$familyId/chat"
    private val presenceTopic: String = "/topic/families/$familyId/presence"
    private val inboxTopic: String = "/topic/users/$myUserId/inbox"
    private val activityTopic: String = "/topic/families/$familyId/activity"
    private val conversationTopic: String? = conversationId?.let { "/topic/conversations/$it" }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = Runnable {
        if (!closedByClient) {
            connect()
        }
    }

    private var webSocket: WebSocket? = null
    @Volatile private var closedByClient = false
    private var reconnectAttempt = 0

    fun connect() {
        closedByClient = false
        mainHandler.removeCallbacks(reconnectRunnable)
        val request = Request.Builder().url(wsUrl).build()
        webSocket = httpClient.newWebSocket(request, listener)
    }

    fun disconnect() {
        closedByClient = true
        mainHandler.removeCallbacks(reconnectRunnable)
        webSocket?.close(1000, null)
        webSocket = null
        onConnectionChange(false)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val token = sessionStore.accessToken
            if (token.isNullOrBlank()) {
                closedByClient = true
                webSocket.close(1000, null)
                onConnectionChange(false)
                return
            }
            val connect = "CONNECT\n" +
                "accept-version:1.2\n" +
                "heart-beat:0,0\n" +
                "host:stomp\n" +
                "Authorization:Bearer $token\n" +
                "\n" +
                NUL
            webSocket.send(connect)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Un mensaje de texto puede contener uno o varios frames STOMP
            // separados por el byte NUL.
            for (raw in text.split(NUL)) {
                val frame = raw.trimStart('\n')
                if (frame.isBlank()) continue
                handleFrame(webSocket, frame)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this@ChatSocket.webSocket = null
            if (!closedByClient) {
                onConnectionChange(false)
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            this@ChatSocket.webSocket = null
            onConnectionChange(false)
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (closedByClient) return
        val delayMillis = minOf(
            RECONNECT_BASE_MS * (1L shl reconnectAttempt.coerceAtMost(RECONNECT_SHIFT_LIMIT)),
            RECONNECT_MAX_MS
        )
        reconnectAttempt++
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, delayMillis)
    }

    private fun handleFrame(webSocket: WebSocket, frame: String) {
        val command = frame.substringBefore('\n').trim()
        when (command) {
            "CONNECTED" -> {
                val subscribeChat = "SUBSCRIBE\n" +
                    "id:sub-chat\n" +
                    "destination:$topic\n" +
                    "\n" +
                    NUL
                webSocket.send(subscribeChat)
                val subscribePresence = "SUBSCRIBE\n" +
                    "id:sub-presence\n" +
                    "destination:$presenceTopic\n" +
                    "\n" +
                    NUL
                webSocket.send(subscribePresence)
                val subscribeInbox = "SUBSCRIBE\n" +
                    "id:sub-inbox\n" +
                    "destination:$inboxTopic\n" +
                    "\n" +
                    NUL
                webSocket.send(subscribeInbox)
                val subscribeActivity = "SUBSCRIBE\n" +
                    "id:sub-activity\n" +
                    "destination:$activityTopic\n" +
                    "\n" +
                    NUL
                webSocket.send(subscribeActivity)
                conversationTopic?.let { convTopic ->
                    val subscribeConversation = "SUBSCRIBE\n" +
                        "id:sub-conversation\n" +
                        "destination:$convTopic\n" +
                        "\n" +
                        NUL
                    webSocket.send(subscribeConversation)
                }
                reconnectAttempt = 0
                onConnectionChange(true)
            }
            "MESSAGE" -> {
                val destination = extractStompHeader(frame, "destination")
                val body = frame.substringAfter("\n\n", "").trim()
                if (body.isEmpty()) {
                    // no-op
                } else if (destination == presenceTopic) {
                    handlePresenceMessage(body)
                } else if (destination == inboxTopic) {
                    handleInboxPing(body)
                } else if (destination != null && destination == conversationTopic) {
                    handlePrivateMessage(body)
                } else if (destination == activityTopic) {
                    handleActivityPing(body)
                } else if (destination == topic) {
                    handleChatMessage(body)
                }
            }
            "ERROR" -> {
                onConnectionChange(false)
                webSocket.close(1000, null)
            }
        }
    }

    private fun handleChatMessage(body: String) {
        runCatching { gson.fromJson(body, ChatMessageDto::class.java) }
            .getOrNull()
            ?.takeIf { it.isUsableChatMessage() }
            ?.let(onMessage)
    }

    private fun handlePresenceMessage(body: String) {
        runCatching { gson.fromJson(body, PresenceResponseDto::class.java) }
            .getOrNull()
            ?.onlineUserIds
            ?.let { onPresenceUpdate(it.toSet()) }
    }

    private fun handleInboxPing(body: String) {
        runCatching { gson.fromJson(body, PrivateInboxPingDto::class.java) }
            .getOrNull()
            ?.let(onInboxPing)
    }

    private fun handleActivityPing(body: String) {
        runCatching { gson.fromJson(body, FamilyActivityPingDto::class.java) }
            .getOrNull()
            ?.let(onActivityPing)
    }

    private fun handlePrivateMessage(body: String) {
        runCatching { gson.fromJson(body, PrivateMessageDto::class.java) }
            .getOrNull()
            ?.takeIf { it.id.isNotBlank() && it.conversationId == conversationId && it.authorUserId.isNotBlank() }
            ?.let(onPrivateMessage)
    }

    private fun toWebSocketUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        val wsBase = when {
            trimmed.startsWith("wss://") -> trimmed
            trimmed.startsWith("ws://") -> trimmed
            trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
            trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
            else -> "ws://$trimmed"
        }
        return "$wsBase/ws"
    }

    private fun ChatMessageDto.isUsableChatMessage(): Boolean = runCatching {
        id.isNotBlank() &&
            familyId == this@ChatSocket.familyId &&
            authorUserId.isNotBlank() &&
            authorDisplayName.isNotBlank() &&
            createdAt.isNotBlank() &&
            updatedAt.isNotBlank()
    }.getOrDefault(false)

    private companion object {
        // Terminador de frame STOMP (byte NUL). Se calcula por codigo para no
        // incrustar un byte de control en el fuente.
        val NUL: String = 0.toChar().toString()
        const val RECONNECT_BASE_MS = 2_000L
        const val RECONNECT_MAX_MS = 30_000L
        const val RECONNECT_SHIFT_LIMIT = 4
    }
}

internal fun extractStompHeader(frame: String, name: String): String? {
    val headersEnd = frame.indexOf("\n\n")
    val headerBlock = if (headersEnd >= 0) frame.substring(0, headersEnd) else frame
    val prefix = "$name:"
    return headerBlock.lineSequence().firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
}
