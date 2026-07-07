package org.gipsybuho.recetasfamiliares.data.remote

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.dto.ChatMessageDto

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
    private val gson: Gson,
    private val onMessage: (ChatMessageDto) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit
) {

    private val wsUrl: String = toWebSocketUrl(baseUrl)
    private val topic: String = "/topic/families/$familyId/chat"

    private var webSocket: WebSocket? = null
    @Volatile private var closedByClient = false

    fun connect() {
        closedByClient = false
        val request = Request.Builder().url(wsUrl).build()
        webSocket = httpClient.newWebSocket(request, listener)
    }

    fun disconnect() {
        closedByClient = true
        webSocket?.close(1000, null)
        webSocket = null
        onConnectionChange(false)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val token = sessionStore.accessToken
            if (token.isNullOrBlank()) {
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
            if (!closedByClient) onConnectionChange(false)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onConnectionChange(false)
        }
    }

    private fun handleFrame(webSocket: WebSocket, frame: String) {
        val command = frame.substringBefore('\n').trim()
        when (command) {
            "CONNECTED" -> {
                val subscribe = "SUBSCRIBE\n" +
                    "id:sub-chat\n" +
                    "destination:$topic\n" +
                    "\n" +
                    NUL
                webSocket.send(subscribe)
                onConnectionChange(true)
            }
            "MESSAGE" -> {
                val body = frame.substringAfter("\n\n", "").trim()
                if (body.isNotEmpty()) {
                    runCatching { gson.fromJson(body, ChatMessageDto::class.java) }
                        .getOrNull()
                        ?.let(onMessage)
                }
            }
            "ERROR" -> {
                onConnectionChange(false)
                webSocket.close(1000, null)
            }
        }
    }

    private fun toWebSocketUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        val wsBase = when {
            trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
            trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
            else -> trimmed
        }
        return "$wsBase/ws"
    }

    private companion object {
        // Terminador de frame STOMP (byte NUL). Se calcula por codigo para no
        // incrustar un byte de control en el fuente.
        val NUL: String = 0.toChar().toString()
    }
}
