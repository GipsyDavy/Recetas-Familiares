package org.gipsybuho.recetasfamiliares.data.repository;

import com.google.gson.Gson;
import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.ChatSocket;
import org.gipsybuho.recetasfamiliares.api.dto.ChatDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Chat familiar fase 1: envio/historial por REST y recepcion en tiempo real via
 * WebSocket/STOMP. Sin cola offline (fase 1): el envio requiere red. El backend
 * valida ownership de familia en cada operacion.
 */
public class ChatRepository {

    public static final int MAX_BODY_LENGTH = 2_000;
    public static final int MAX_IMAGE_ATTACHMENTS = 5;
    public static final int PAGE_SIZE = 30;

    private final ApiClient api;
    private final AppSession session;
    private final Gson gson = new Gson();

    private final AtomicReference<Set<String>> lastOnlineUserIds = new AtomicReference<>(Set.of());
    private volatile Consumer<Set<String>> presenceListener;

    private final java.util.Map<String, Integer> unreadByConversation = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile String activeConversationId;
    private volatile java.util.function.Consumer<java.util.Map<String, Integer>> inboxListener;
    private volatile java.util.function.Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage> conversationMessageListener;
    private volatile ChatSocket activeSocket;

    private final java.util.Set<String> sectionsWithUnseenActivity = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile Consumer<java.util.Set<String>> activityListener;

    public ChatRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public void setPresenceListener(Consumer<Set<String>> listener) {
        this.presenceListener = listener;
    }

    public Set<String> lastOnlineUserIds() {
        return lastOnlineUserIds.get();
    }

    private void handlePresenceUpdate(Set<String> onlineUserIds) {
        Set<String> snapshot = Collections.unmodifiableSet(onlineUserIds);
        lastOnlineUserIds.set(snapshot);
        Consumer<Set<String>> listener = presenceListener;
        if (listener != null) {
            listener.accept(snapshot);
        }
    }

    /** Socket compartido de esta sesion de chat (familiar + privado). Null si no hay ninguna conexion abierta. */
    public ChatSocket activeSocket() {
        return activeSocket;
    }

    /**
     * Limpia el estado de no-leidos del chat privado al cerrar sesion o cambiar de
     * familia: sin esto, el contador de un usuario/familia anterior sobrevive en este
     * singleton y se mezcla con el del siguiente en un PC compartido.
     */
    public void resetPrivateChatState() {
        unreadByConversation.clear();
        activeConversationId = null;
        sectionsWithUnseenActivity.clear();
    }

    /** Snapshot inmutable de no-leidos por conversacion, para pintar la bandeja/badge. */
    public Map<String, Integer> unreadByConversation() {
        return Map.copyOf(unreadByConversation);
    }

    /**
     * Marca una conversacion como la que el usuario esta viendo ahora mismo:
     * limpia su contador de no-leidos y evita que nuevos pings la vuelvan a
     * marcar mientras siga activa. Pasar {@code null} cuando no hay ninguna
     * conversacion abierta (p.ej. al salir de Conversaciones).
     */
    public void setActiveConversation(String conversationId) {
        this.activeConversationId = conversationId;
        if (conversationId != null && unreadByConversation.remove(conversationId) != null) {
            notifyInboxListener();
        }
    }

    public void setInboxListener(Consumer<Map<String, Integer>> listener) {
        this.inboxListener = listener;
    }

    /** Recibe los mensajes en vivo de la conversacion actualmente suscrita en el socket. */
    public void setConversationMessageListener(
            Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage> listener) {
        this.conversationMessageListener = listener;
    }

    private void handleInboxPing(org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateInboxPing ping) {
        if (ping.conversationId().equals(activeConversationId)) {
            return;
        }
        unreadByConversation.merge(ping.conversationId(), 1, Integer::sum);
        notifyInboxListener();
    }

    private void handlePrivateMessage(org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage message) {
        Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage> listener = conversationMessageListener;
        if (listener != null) {
            listener.accept(message);
        }
    }

    private void notifyInboxListener() {
        Consumer<Map<String, Integer>> listener = inboxListener;
        if (listener != null) {
            listener.accept(unreadByConversation());
        }
    }

    public void setActivityListener(Consumer<java.util.Set<String>> listener) {
        this.activityListener = listener;
    }

    public java.util.Set<String> sectionsWithUnseenActivity() {
        return java.util.Set.copyOf(sectionsWithUnseenActivity);
    }

    /** Limpia una seccion localmente al navegar a ella (el POST "seen" real lo hace el llamador). */
    public void markSectionSeenLocally(String section) {
        if (sectionsWithUnseenActivity.remove(section)) {
            notifyActivityListener();
        }
    }

    private void handleActivityPing(org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos.FamilyActivityPing ping) {
        sectionsWithUnseenActivity.add(ping.section());
        notifyActivityListener();
    }

    private void notifyActivityListener() {
        Consumer<java.util.Set<String>> listener = activityListener;
        if (listener != null) {
            listener.accept(sectionsWithUnseenActivity());
        }
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

    public ChatDtos.ChatMessage sendImage(String body, File image) throws ApiException {
        String family = requireFamily();
        String text = body == null ? "" : body.trim();
        if (text.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo");
        }
        if (image == null || !image.isFile()) {
            throw new IllegalArgumentException("Selecciona una imagen valida");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", UUID.randomUUID().toString());
        if (!text.isEmpty()) {
            fields.put("body", text);
        }
        return api.postMultipart(
                "api/v1/families/" + family + "/chat/messages/images",
                fields,
                List.of(image),
                "files",
                ChatDtos.ChatMessage.class);
    }

    public ChatDtos.ChatMessage edit(String messageId, String body) throws ApiException {
        String family = requireFamily();
        String text = body == null ? "" : body.trim();
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Mensaje no valido");
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacio");
        }
        if (text.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo");
        }
        var request = new ChatDtos.EditChatMessageRequest(text);
        return api.put("api/v1/families/" + family + "/chat/messages/" + messageId.trim(),
                request, ChatDtos.ChatMessage.class);
    }

    public ChatDtos.ChatMessage delete(String messageId) throws ApiException {
        String family = requireFamily();
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Mensaje no valido");
        }
        return api.delete("api/v1/families/" + family + "/chat/messages/" + messageId.trim(),
                ChatDtos.ChatMessage.class);
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
                session.getUserId(),
                gson,
                onMessage,
                onConnectionChange,
                this::handlePresenceUpdate,
                this::handleInboxPing,
                this::handlePrivateMessage,
                this::handleActivityPing);
        socket.connect();
        this.activeSocket = socket;
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
