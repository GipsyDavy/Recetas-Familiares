package org.gipsybuho.recetasfamiliares.chat;

import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.security.InvalidJwtException;
import org.gipsybuho.recetasfamiliares.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Seguridad del canal STOMP entrante:
 * <ul>
 *   <li>CONNECT: exige JWT valido en la cabecera Authorization (nunca en la URL,
 *       que se loggea). Resuelve el userId y lo fija como Principal de la sesion.</li>
 *   <li>SUBSCRIBE: valida membership de familia contra el destino
 *       {@code /topic/families/{familyId}/chat}. Se re-valida en cada nueva
 *       suscripcion, de modo que un usuario expulsado no puede resuscribirse.</li>
 * </ul>
 */
@Component
public class ChatStompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String TOPIC_PREFIX = "/topic/families/";
    private static final String TOPIC_SUFFIX = "/chat";

    private final JwtService jwtService;
    private final FamilyMemberRepository familyMemberRepository;

    public ChatStompAuthChannelInterceptor(JwtService jwtService, FamilyMemberRepository familyMemberRepository) {
        this.jwtService = jwtService;
        this.familyMemberRepository = familyMemberRepository;
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
        String familyId = extractFamilyId(destination);
        if (familyId == null) {
            throw new MessagingException("Subscription destination not allowed");
        }
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new MessagingException("Family subscription denied");
        }
    }

    private String currentUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof StompPrincipal principal) {
            return principal.userId();
        }
        throw new MessagingException("Unauthenticated STOMP session");
    }

    private String extractFamilyId(String destination) {
        if (destination == null
                || !destination.startsWith(TOPIC_PREFIX)
                || !destination.endsWith(TOPIC_SUFFIX)) {
            return null;
        }
        String familyId = destination.substring(TOPIC_PREFIX.length(), destination.length() - TOPIC_SUFFIX.length());
        return familyId.isBlank() ? null : familyId;
    }
}
