package org.gipsybuho.recetasfamiliares.chat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.gipsybuho.recetasfamiliares.dm.PrivateConversationEntity;
import org.gipsybuho.recetasfamiliares.dm.PrivateConversationRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.presence.PresencePublisher;
import org.gipsybuho.recetasfamiliares.presence.PresenceRegistry;
import org.gipsybuho.recetasfamiliares.security.InvalidJwtException;
import org.gipsybuho.recetasfamiliares.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class ChatStompAuthChannelInterceptorTest {

    private static final String USER_ID = "user-123";
    private static final String FAMILY_ID = "fam-abc";
    private static final String TOPIC = "/topic/families/" + FAMILY_ID + "/chat";
    private static final String PRESENCE_TOPIC = "/topic/families/" + FAMILY_ID + "/presence";
    private static final String CONVERSATION_ID = "conv-xyz";
    private static final String OTHER_USER_ID = "user-456";
    private static final String CONVERSATION_TOPIC = "/topic/conversations/" + CONVERSATION_ID;
    private static final String INBOX_TOPIC = "/topic/users/" + USER_ID + "/inbox";

    private JwtService jwtService;
    private FamilyMemberRepository familyMemberRepository;
    private PresenceRegistry presenceRegistry;
    private PresencePublisher presencePublisher;
    private PrivateConversationRepository privateConversationRepository;
    private MessageChannel channel;
    private ChatStompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = Mockito.mock(JwtService.class);
        familyMemberRepository = Mockito.mock(FamilyMemberRepository.class);
        presenceRegistry = Mockito.mock(PresenceRegistry.class);
        presencePublisher = Mockito.mock(PresencePublisher.class);
        privateConversationRepository = Mockito.mock(PrivateConversationRepository.class);
        channel = Mockito.mock(MessageChannel.class);
        interceptor = new ChatStompAuthChannelInterceptor(
                jwtService, familyMemberRepository, presenceRegistry, presencePublisher, privateConversationRepository);
    }

    @Test
    void acceptsConnectWithValidToken() {
        when(jwtService.validateAndGetUserId("good-token")).thenReturn(USER_ID);
        Message<byte[]> connect = connect("Bearer good-token");
        assertDoesNotThrow(() -> interceptor.preSend(connect, channel));
    }

    @Test
    void rejectsConnectWithoutToken() {
        Message<byte[]> connect = connect(null);
        assertThrows(MessagingException.class, () -> interceptor.preSend(connect, channel));
    }

    @Test
    void rejectsConnectWithInvalidToken() {
        when(jwtService.validateAndGetUserId("bad-token"))
                .thenThrow(new InvalidJwtException("Invalid access token", null));
        Message<byte[]> connect = connect("Bearer bad-token");
        assertThrows(MessagingException.class, () -> interceptor.preSend(connect, channel));
    }

    @Test
    void allowsSubscribeForFamilyMember() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(eq(FAMILY_ID), eq(USER_ID)))
                .thenReturn(true);
        Message<byte[]> subscribe = subscribe(TOPIC, new StompPrincipal(USER_ID));
        assertDoesNotThrow(() -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsSubscribeForNonMember() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(eq(FAMILY_ID), eq(USER_ID)))
                .thenReturn(false);
        Message<byte[]> subscribe = subscribe(TOPIC, new StompPrincipal(USER_ID));
        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsSubscribeWithoutAuthenticatedPrincipal() {
        Message<byte[]> subscribe = subscribe(TOPIC, null);
        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsSubscribeToForeignDestination() {
        Message<byte[]> subscribe = subscribe("/topic/other/thing", new StompPrincipal(USER_ID));
        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsClientSendToBroker() {
        // Un cliente autenticado no debe poder publicar por STOMP: burlaria
        // ownership, persistencia y rate limit inyectando mensajes falsos.
        Message<byte[]> send = send(TOPIC, new StompPrincipal(USER_ID));
        assertThrows(MessagingException.class, () -> interceptor.preSend(send, channel));
    }

    @Test
    void allowsSubscribeToPresenceForFamilyMember() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(eq(FAMILY_ID), eq(USER_ID)))
                .thenReturn(true);
        Message<byte[]> subscribe = subscribe(PRESENCE_TOPIC, new StompPrincipal(USER_ID));

        assertDoesNotThrow(() -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsSubscribeToPresenceForNonMember() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(eq(FAMILY_ID), eq(USER_ID)))
                .thenReturn(false);
        Message<byte[]> subscribe = subscribe(PRESENCE_TOPIC, new StompPrincipal(USER_ID));

        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void registersPresenceAndPublishesOnAuthorizedSubscribe() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(eq(FAMILY_ID), eq(USER_ID)))
                .thenReturn(true);
        Message<byte[]> subscribe = subscribe(PRESENCE_TOPIC, new StompPrincipal(USER_ID));

        interceptor.preSend(subscribe, channel);

        Mockito.verify(presenceRegistry).subscribe(Mockito.anyString(), eq(FAMILY_ID), eq(USER_ID));
        Mockito.verify(presencePublisher).publish(FAMILY_ID);
    }

    @Test
    void chatSubscribeDoesNotTouchPresenceRegistry() {
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(eq(FAMILY_ID), eq(USER_ID)))
                .thenReturn(true);
        Message<byte[]> subscribe = subscribe(TOPIC, new StompPrincipal(USER_ID));

        interceptor.preSend(subscribe, channel);

        Mockito.verifyNoInteractions(presenceRegistry, presencePublisher);
    }

    @Test
    void allowsSubscribeToConversationForParticipant() {
        PrivateConversationEntity conversation = Mockito.mock(PrivateConversationEntity.class);
        when(conversation.hasParticipant(USER_ID)).thenReturn(true);
        when(conversation.getFamilyId()).thenReturn(FAMILY_ID);
        when(privateConversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(FAMILY_ID, USER_ID))
                .thenReturn(true);
        Message<byte[]> subscribe = subscribe(CONVERSATION_TOPIC, new StompPrincipal(USER_ID));

        assertDoesNotThrow(() -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsSubscribeToConversationForNonParticipant() {
        PrivateConversationEntity conversation = Mockito.mock(PrivateConversationEntity.class);
        when(conversation.hasParticipant(USER_ID)).thenReturn(false);
        when(privateConversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));
        Message<byte[]> subscribe = subscribe(CONVERSATION_TOPIC, new StompPrincipal(USER_ID));

        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsSubscribeToConversationForRemovedFamilyMember() {
        // Regresion: el usuario sigue siendo participante de la conversacion (nunca
        // se elimina esa relacion), pero ya no pertenece a la familia. Debe perder
        // el acceso igual que en la via REST (requireParticipantConversation).
        PrivateConversationEntity conversation = Mockito.mock(PrivateConversationEntity.class);
        when(conversation.hasParticipant(USER_ID)).thenReturn(true);
        when(conversation.getFamilyId()).thenReturn(FAMILY_ID);
        when(privateConversationRepository.findByIdAndDeletedFalse(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));
        when(familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(FAMILY_ID, USER_ID))
                .thenReturn(false);
        Message<byte[]> subscribe = subscribe(CONVERSATION_TOPIC, new StompPrincipal(USER_ID));

        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void allowsSubscribeToOwnInboxTopic() {
        Message<byte[]> subscribe = subscribe(INBOX_TOPIC, new StompPrincipal(USER_ID));

        assertDoesNotThrow(() -> interceptor.preSend(subscribe, channel));
    }

    @Test
    void rejectsSubscribeToAnotherUsersInboxTopic() {
        // USER_ID intenta suscribirse a la bandeja de OTHER_USER_ID: debe rechazarse
        // sin siquiera consultar el repositorio de conversaciones.
        Message<byte[]> subscribe = subscribe("/topic/users/" + OTHER_USER_ID + "/inbox", new StompPrincipal(USER_ID));

        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe, channel));
        Mockito.verifyNoInteractions(privateConversationRepository);
    }

    private Message<byte[]> send(String destination, StompPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        if (principal != null) {
            accessor.setUser(principal);
        }
        return MessageBuilder.createMessage("{\"body\":\"spoofed\"}".getBytes(), accessor.getMessageHeaders());
    }

    private Message<byte[]> connect(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        // En el canal entrante real Spring entrega un accessor mutable; se replica
        // aqui para que el interceptor pueda fijar el Principal en el CONNECT.
        accessor.setLeaveMutable(true);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribe(String destination, StompPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        // Un SUBSCRIBE real siempre lleva el sessionId de la conexion STOMP que lo envia.
        accessor.setSessionId("session-1");
        if (principal != null) {
            accessor.setUser(principal);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
