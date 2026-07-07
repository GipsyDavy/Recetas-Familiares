package org.gipsybuho.recetasfamiliares.data.repository;

import com.google.gson.Gson;
import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.ChatSocket;
import org.gipsybuho.recetasfamiliares.api.dto.ChatDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chat familiar fase 1: envio/historial por REST y recepcion en tiempo real via
 * WebSocket/STOMP. Sin cola offline (fase 1): el envio requiere red. El backend
 * valida ownership de familia en cada operacion.
 */
public class ChatRepository {

    public static final int MAX_BODY_LENGTH = 2_000;
    public static final int PAGE_SIZE = 30;

    private final ApiClient api;
    private final AppSession session;
    private final Gson gson = new Gson();

    public ChatRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public String familyId() {
        return session.getFamilyId();
    }

    public String myUserId() {
        return session.getUserId();
    }

    public ChatDtos.ChatHistory loadHistory(String before, int limit) throws ApiException {
        String family = requireFamily();
        StringBuilder path = new StringBuilder("api/v1/families/")
                .append(family)
                .append("/chat/messages?limit=")
                .append(limit);
        if (before != null && !before.isBlank()) {
            path.append("&before=").append(before.trim());
        }
        return api.get(path.toString(), ChatDtos.ChatHistory.class);
    }

    public ChatDtos.ChatMessage send(String body) throws ApiException {
        String family = requireFamily();
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacio");
        }
        if (text.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo");
        }
        var request = new ChatDtos.SendChatMessageRequest(UUID.randomUUID().toString(), text);
        return api.post("api/v1/families/" + family + "/chat/messages", request, ChatDtos.ChatMessage.class);
    }

    public void clear() throws ApiException {
        api.post("api/v1/families/" + requireFamily() + "/chat/clear", "{}", Void.class);
    }

    public ChatDtos.ChatExport export() throws ApiException {
        return api.get("api/v1/families/" + requireFamily() + "/chat/export", ChatDtos.ChatExport.class);
    }

    /**
     * Abre la conexion en tiempo real. Devuelve el socket para que el llamador
     * lo cierre al salir del chat. Null si no hay familia en sesion.
     */
    public ChatSocket openRealtime(
            Consumer<ChatDtos.ChatMessage> onMessage,
            Consumer<Boolean> onConnectionChange
    ) {
        String family = familyId();
        if (family == null || family.isBlank()) {
            return null;
        }
        ChatSocket socket = new ChatSocket(
                api,
                session::getAccessToken,
                family,
                gson,
                onMessage,
                onConnectionChange);
        socket.connect();
        return socket;
    }

    private String requireFamily() {
        String family = familyId();
        if (family == null || family.isBlank()) {
            throw new IllegalStateException("No hay familia en la sesion");
        }
        return family;
    }
}
