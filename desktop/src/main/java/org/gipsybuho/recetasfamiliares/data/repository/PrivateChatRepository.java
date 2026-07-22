package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

/**
 * Chat privado 1:1 fase Desktop: envio/historial por REST, tiempo real via
 * el ChatSocket compartido de {@link ChatRepository} (una sola conexion WS por
 * sesion). Backend valida ownership de conversacion y de familia en cada
 * operacion.
 */
public class PrivateChatRepository {

    public static final int MAX_BODY_LENGTH = 2_000;
    public static final int PAGE_SIZE = 30;

    private final ApiClient api;
    private final AppSession session;

    public PrivateChatRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public PrivateChatDtos.PrivateConversation[] listConversations() throws ApiException {
        return api.get("api/v1/families/" + requireFamily() + "/conversations",
                PrivateChatDtos.PrivateConversation[].class);
    }

    public PrivateChatDtos.PrivateConversation createOrGetConversation(String otherUserId) throws ApiException {
        return api.post("api/v1/families/" + requireFamily() + "/conversations/with/" + otherUserId,
                "{}", PrivateChatDtos.PrivateConversation.class);
    }

    public PrivateChatDtos.PrivateMessageHistory loadHistory(String conversationId, String before, int limit)
            throws ApiException {
        StringBuilder path = new StringBuilder("api/v1/families/")
                .append(requireFamily())
                .append("/conversations/").append(conversationId)
                .append("/messages?limit=").append(limit);
        if (before != null && !before.isBlank()) {
            path.append("&before=").append(before.trim());
        }
        return api.get(path.toString(), PrivateChatDtos.PrivateMessageHistory.class);
    }

    public PrivateChatDtos.PrivateMessage send(String conversationId, String body) throws ApiException {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacio");
        }
        if (text.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo");
        }
        var request = new PrivateChatDtos.SendPrivateMessageRequest(
                java.util.UUID.randomUUID().toString(), text);
        return api.post("api/v1/families/" + requireFamily() + "/conversations/" + conversationId + "/messages",
                request, PrivateChatDtos.PrivateMessage.class);
    }

    public PrivateChatDtos.PrivateMessage sendImage(String conversationId, String body, java.io.File image)
            throws ApiException {
        String text = body == null ? "" : body.trim();
        if (text.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo");
        }
        if (image == null || !image.isFile()) {
            throw new IllegalArgumentException("Selecciona una imagen valida");
        }
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("id", java.util.UUID.randomUUID().toString());
        if (!text.isEmpty()) {
            fields.put("body", text);
        }
        return api.postMultipart(
                "api/v1/families/" + requireFamily() + "/conversations/" + conversationId + "/messages/images",
                fields, java.util.List.of(image), "files", PrivateChatDtos.PrivateMessage.class);
    }

    public PrivateChatDtos.PrivateMessage edit(String conversationId, String messageId, String body)
            throws ApiException {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacio");
        }
        if (text.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo");
        }
        var request = new PrivateChatDtos.EditPrivateMessageRequest(text);
        return api.put("api/v1/families/" + requireFamily() + "/conversations/" + conversationId
                + "/messages/" + messageId, request, PrivateChatDtos.PrivateMessage.class);
    }

    public PrivateChatDtos.PrivateMessage delete(String conversationId, String messageId) throws ApiException {
        return api.delete("api/v1/families/" + requireFamily() + "/conversations/" + conversationId
                + "/messages/" + messageId, PrivateChatDtos.PrivateMessage.class);
    }

    public void clear(String conversationId) throws ApiException {
        api.post("api/v1/families/" + requireFamily() + "/conversations/" + conversationId + "/clear",
                "{}", Void.class);
    }

    public PrivateChatDtos.PrivateMessageExport export(String conversationId) throws ApiException {
        return api.get("api/v1/families/" + requireFamily() + "/conversations/" + conversationId + "/export",
                PrivateChatDtos.PrivateMessageExport.class);
    }

    private String requireFamily() {
        String family = session.getFamilyId();
        if (family == null || family.isBlank()) {
            throw new IllegalStateException("No hay familia en la sesion");
        }
        return family;
    }
}
