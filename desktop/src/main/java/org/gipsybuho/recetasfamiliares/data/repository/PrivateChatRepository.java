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

    private String requireFamily() {
        String family = session.getFamilyId();
        if (family == null || family.isBlank()) {
            throw new IllegalStateException("No hay familia en la sesion");
        }
        return family;
    }
}
