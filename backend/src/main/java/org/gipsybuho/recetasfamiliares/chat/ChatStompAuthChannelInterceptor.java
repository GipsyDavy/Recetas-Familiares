package org.gipsybuho.recetasfamiliares.chat;

import org.gipsybuho.recetasfamiliares.dm.PrivateConversationRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.presence.PresencePublisher;
import org.gipsybuho.recetasfamiliares.presence.PresenceRegistry;
import org.gipsybuho.recetasfamiliares.security.InvalidJwtException;
import org.gipsybuho.recetasfamiliares.security.JwtService;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Seguridad del canal STOMP entrante (unico interceptor registrado para todo
 * el endpoint {@code /ws}, no solo para chat):
 * <ul>
 *   <li>CONNECT: exige JWT valido en la cabecera Authorization (nunca en la URL,
 *       que se loggea). Resuelve el userId y lo fija como Principal de la sesion.</li>
 *   <li>SUBSCRIBE: valida membership de familia contra el destino
 *       {@code /topic/families/{familyId}/chat} o
 *       {@code /topic/families/{familyId}/presence}. Se re-valida en cada nueva
 *       suscripcion, de modo que un usuario expulsado no puede resuscribirse.
 *       Una suscripcion de presencia autorizada, ademas, registra la conexion
 *       en {@link PresenceRegistry} y difunde el snapshot actualizado.</li>
 *   <li>SEND: se rechaza siempre. Los clientes publican por REST; permitir un
 *       SEND directo al broker simple dejaria inyectar mensajes falsos en el
 *       topic de cualquier familia saltandose ownership, persistencia y rate
 *       limit. El broadcast legitimo lo emite el servidor via
 *       {@link ChatRealtimePublisher} / {@link PresencePublisher}, que no pasan
 *       por este canal entrante.</li>
 * </ul>
 */
@Component
public class ChatStompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String TOPIC_PREFIX = "/topic/families/";
    private static final String CHAT_SUFFIX = "/chat";
    private static final String PRESENCE_SUFFIX = "/presence";
    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";
    private static final String INBOX_TOPIC_PREFIX = "/topic/users/";
    private static final String INBOX_TOPIC_SUFFIX = "/inbox";

    private final JwtService jwtService;
    private final FamilyMemberRepository familyMemberRepository;
    private final PresenceRegistry presenceRegistry;
    private final PresencePublisher presencePublisher;
    private final PrivateConversationRepository privateConversationRepository;

    public ChatStompAuthChannelInterceptor(
            JwtService jwtService,
            FamilyMemberRepository familyMemberRepository,
            PresenceRegistry presenceRegistry,
            @Lazy PresencePublisher presencePublisher,
            PrivateConversationRepository privateConversationRepository
    ) {
        this.jwtService = jwtService;
        this.familyMemberRepository = familyMemberRepository;
        this.presenceRegistry = presenceRegistry;
        this.presencePublisher = presencePublisher;
        this.privateConversationRepository = privateConversationRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            // Los clientes nunca publican por STOMP (envian por REST). Un SEND
            // al broker simple burlaria ownership, persistencia y rate limit.
            throw new MessagingException("Client SEND not allowed on chat channel");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessagingException("Missing bearer token on STOMP CONNECT");
        }
        try {
            String userId = jwtService.validateAndGetUserId(authorization.substring("Bearer ".length()));
            accessor.setUser(new StompPrincipal(userId));
        } catch (InvalidJwtException exception) {
            throw new MessagingException("Invalid token on STOMP CONNECT");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String userId = currentUserId(accessor);
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("Subscription destination not allowed");
        }
        if (destination.startsWith(TOPIC_PREFIX)) {
            authorizeFamilyTopic(accessor, userId, destination);
        } else if (destination.startsWith(CONVERSATION_TOPIC_PREFIX)) {
            authorizeConversationTopic(userId, destination);
        } else if (destination.startsWith(INBOX_TOPIC_PREFIX) && destination.endsWith(INBOX_TOPIC_SUFFIX)) {
            authorizeInboxTopic(userId, destination);
        } else {
            throw new MessagingException("Subscription destination not allowed");
        }
    }

    private void authorizeFamilyTopic(StompHeaderAccessor accessor, String userId, String destination) {
        String familyId = extractFamilyId(destination);
        if (familyId == null) {
            throw new MessagingException("Subscription destination not allowed");
        }
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new MessagingException("Family subscription denied");
        }
        if (destination.endsWith(PRESENCE_SUFFIX)) {
            presenceRegistry.subscribe(accessor.getSessionId(), familyId, userId);
            presencePublisher.publish(familyId);
        }
    }

    private void authorizeConversationTopic(String userId, String destination) {
        String conversationId = destination.substring(CONVERSATION_TOPIC_PREFIX.length());
        if (conversationId.isBlank() || conversationId.contains("/")) {
            throw new MessagingException("Subscription destination not allowed");
        }
        if (!privateConversationRepository.existsByIdAndParticipant(conversationId, userId)) {
            throw new MessagingException("Conversation subscription denied");
        }
    }

    private void authorizeInboxTopic(String userId, String destination) {
        String targetUserId = destination.substring(
                INBOX_TOPIC_PREFIX.length(), destination.length() - INBOX_TOPIC_SUFFIX.length());
        // Solo el propio usuario puede suscribirse a su bandeja: sin esto, cualquier
        // sesion autenticada podria enterarse de que otro usuario recibio un mensaje
        // privado nuevo (metadata, no contenido, pero sigue siendo informacion ajena).
        if (!targetUserId.equals(userId)) {
            throw new MessagingException("Inbox subscription denied");
        }
    }

    private String currentUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof StompPrincipal principal) {
            return principal.userId();
        }
        throw new MessagingException("Unauthenticated STOMP session");
    }

    private String extractFamilyId(String destination) {
        if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        String suffix;
        if (destination.endsWith(CHAT_SUFFIX)) {
            suffix = CHAT_SUFFIX;
        } else if (destination.endsWith(PRESENCE_SUFFIX)) {
            suffix = PRESENCE_SUFFIX;
        } else {
            return null;
        }
        String familyId = destination.substring(TOPIC_PREFIX.length(), destination.length() - suffix.length());
        return familyId.isBlank() ? null : familyId;
    }
}
