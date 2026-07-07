package org.gipsybuho.recetasfamiliares.chat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
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

    private JwtService jwtService;
    private FamilyMemberRepository familyMemberRepository;
    private MessageChannel channel;
    private ChatStompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = Mockito.mock(JwtService.class);
        familyMemberRepository = Mockito.mock(FamilyMemberRepository.class);
        channel = Mockito.mock(MessageChannel.class);
        interceptor = new ChatStompAuthChannelInterceptor(jwtService, familyMemberRepository);
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
        if (principal != null) {
            accessor.setUser(principal);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
