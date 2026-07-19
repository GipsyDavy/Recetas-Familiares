# Presencia online de miembros — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mostrar un punto verde/gris junto a cada miembro en la pantalla Miembros (Desktop y Android) indicando si está conectado ahora mismo, sin heartbeat ni persistencia.

**Architecture:** Segundo topic STOMP por familia (`/topic/families/{familyId}/presence`), reutilizando la conexión WebSocket que ambos clientes ya mantienen abierta para el chat tras el login. Estado en memoria en el backend (`PresenceRegistry`, contador por familia+usuario); sin tabla nueva, sin `lastSeenAt`. Snapshot completo (no deltas) vía WS y vía un endpoint REST para el estado inicial.

**Tech Stack:** Spring Boot (STOMP/`SimpMessagingTemplate`), OkHttp WebSocket (Desktop/Android, cliente STOMP casero ya existente), JavaFX (Desktop UI), Jetpack Compose (Android UI).

## Global Constraints

- Spec aprobada: `docs/superpowers/specs/2026-07-19-presencia-online-design.md`.
- Alcance: solo presencia en tiempo real ("conectado ahora"). Sin `lastSeenAt`, sin heartbeat, sin avisos toast/snackbar, sin contador en sidebar, sin iOS.
- Ownership: toda operación de presencia (SUBSCRIBE STOMP y GET REST) exige membership activa de familia (`FamilyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse`), igual que el chat.
- JSON camelCase, endpoints bajo `/api/v1/`.
- No bloquear el hilo JavaFX ni el hilo principal de Android; todo I/O de red en hilo virtual (Desktop) o corrutina (Android), igual que el resto del código existente.
- No introducir dependencias nuevas.
- Backend: sin cambios en `SecurityConfig` (mismo patrón que `RecipeRankingController`, ya cubierto por la cadena de filtros JWT existente).

---

## Task 1: Backend — `PresenceRegistry` (estado en memoria)

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceRegistry.java`
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresenceRegistryTest.java`

**Interfaces:**
- Produces: `PresenceRegistry` (`@Component`, sin dependencias) con:
  - `boolean subscribe(String sessionId, String familyId, String userId)` — registra una conexión; `true` si el usuario pasó de 0 a 1 conexiones online.
  - `Set<String> unsubscribeSession(String sessionId)` — limpia todas las entradas de una sesión cerrada; devuelve el conjunto de `familyId` cuyo snapshot cambió.
  - `List<String> onlineUserIds(String familyId)` — snapshot ordenado de `userId` online en esa familia.

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresenceRegistryTest.java`:

```java
package org.gipsybuho.recetasfamiliares.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PresenceRegistryTest {

    private final PresenceRegistry registry = new PresenceRegistry();

    @Test
    void firstSubscriptionMarksUserOnline() {
        boolean becameOnline = registry.subscribe("session-1", "family-1", "user-a");

        assertThat(becameOnline).isTrue();
        assertThat(registry.onlineUserIds("family-1")).containsExactly("user-a");
    }

    @Test
    void secondSessionSameUserDoesNotDuplicateOrResetState() {
        registry.subscribe("session-1", "family-1", "user-a");
        boolean becameOnlineAgain = registry.subscribe("session-2", "family-1", "user-a");

        assertThat(becameOnlineAgain).isFalse();
        assertThat(registry.onlineUserIds("family-1")).containsExactly("user-a");
    }

    @Test
    void userStaysOnlineWhileAnyOtherSessionOpen() {
        registry.subscribe("session-1", "family-1", "user-a");
        registry.subscribe("session-2", "family-1", "user-a");

        Set<String> changed = registry.unsubscribeSession("session-1");

        assertThat(changed).isEmpty();
        assertThat(registry.onlineUserIds("family-1")).containsExactly("user-a");
    }

    @Test
    void disconnectingLastSessionRemovesUserFromFamily() {
        registry.subscribe("session-1", "family-1", "user-a");

        Set<String> changed = registry.unsubscribeSession("session-1");

        assertThat(changed).containsExactly("family-1");
        assertThat(registry.onlineUserIds("family-1")).isEmpty();
    }

    @Test
    void disconnectingUnknownSessionIsNoop() {
        Set<String> changed = registry.unsubscribeSession("never-subscribed");

        assertThat(changed).isEmpty();
    }

    @Test
    void tracksMultipleFamiliesIndependently() {
        registry.subscribe("session-1", "family-1", "user-a");
        registry.subscribe("session-1", "family-2", "user-a");

        Set<String> changed = registry.unsubscribeSession("session-1");

        assertThat(changed).containsExactlyInAnyOrder("family-1", "family-2");
        assertThat(registry.onlineUserIds("family-1")).isEmpty();
        assertThat(registry.onlineUserIds("family-2")).isEmpty();
    }
}
```

- [ ] **Step 2: Ejecutar el test y confirmar que falla**

Run: `mvn -f backend/pom.xml -Dtest=PresenceRegistryTest test`
Expected: FAIL (compilación) — `PresenceRegistry` no existe todavía.

- [ ] **Step 3: Implementación mínima**

Crear `backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceRegistry.java`:

```java
package org.gipsybuho.recetasfamiliares.presence;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Registro en memoria de quien esta conectado a que familia via WebSocket.
 * Contador (no booleano) por (familia, usuario) para soportar el mismo
 * usuario conectado desde varios dispositivos sin que uno "apague" al otro.
 * Sin persistencia: si el backend reinicia, los clientes se reconectan solos
 * (ChatSocket ya tiene backoff exponencial) y repueblan el registro.
 */
@Component
public class PresenceRegistry {

    private record PresenceKey(String familyId, String userId) {}

    private final Map<String, Map<String, AtomicInteger>> onlineByFamily = new ConcurrentHashMap<>();
    private final Map<String, List<PresenceKey>> keysBySession = new ConcurrentHashMap<>();

    public synchronized boolean subscribe(String sessionId, String familyId, String userId) {
        Map<String, AtomicInteger> familyCounts =
                onlineByFamily.computeIfAbsent(familyId, id -> new ConcurrentHashMap<>());
        AtomicInteger counter = familyCounts.computeIfAbsent(userId, id -> new AtomicInteger(0));
        boolean becameOnline = counter.getAndIncrement() == 0;
        keysBySession.computeIfAbsent(sessionId, id -> new CopyOnWriteArrayList<>())
                .add(new PresenceKey(familyId, userId));
        return becameOnline;
    }

    public synchronized Set<String> unsubscribeSession(String sessionId) {
        List<PresenceKey> keys = keysBySession.remove(sessionId);
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        Set<String> changedFamilies = new HashSet<>();
        for (PresenceKey key : keys) {
            Map<String, AtomicInteger> familyCounts = onlineByFamily.get(key.familyId());
            if (familyCounts == null) {
                continue;
            }
            AtomicInteger counter = familyCounts.get(key.userId());
            if (counter == null) {
                continue;
            }
            if (counter.decrementAndGet() <= 0) {
                familyCounts.remove(key.userId());
                changedFamilies.add(key.familyId());
            }
            if (familyCounts.isEmpty()) {
                onlineByFamily.remove(key.familyId());
            }
        }
        return changedFamilies;
    }

    public List<String> onlineUserIds(String familyId) {
        Map<String, AtomicInteger> familyCounts = onlineByFamily.get(familyId);
        if (familyCounts == null) {
            return List.of();
        }
        return familyCounts.keySet().stream().sorted().collect(Collectors.toList());
    }
}
```

- [ ] **Step 4: Ejecutar el test y confirmar que pasa**

Run: `mvn -f backend/pom.xml -Dtest=PresenceRegistryTest test`
Expected: `BUILD SUCCESS`, 6 tests, 0 fallos.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceRegistry.java backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresenceRegistryTest.java
git commit -m "feat(backend): PresenceRegistry en memoria para presencia online"
```

---

## Task 2: Backend — `PresenceResponse` DTO + `PresencePublisher`

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceResponse.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresencePublisher.java`
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresencePublisherTest.java`

**Interfaces:**
- Consumes: `PresenceRegistry.onlineUserIds(String familyId): List<String>` (Task 1).
- Produces: `PresenceResponse(List<String> onlineUserIds)` (record, reusado luego por `PresenceController`, Task 5); `PresencePublisher.publish(String familyId): void` — difunde el snapshot actual a `/topic/families/{familyId}/presence`.

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresencePublisherTest.java`:

```java
package org.gipsybuho.recetasfamiliares.presence;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class PresencePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PresenceRegistry registry;

    private PresencePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PresencePublisher(messagingTemplate, registry);
    }

    @Test
    void publishesCurrentSnapshotToFamilyPresenceTopic() {
        when(registry.onlineUserIds("family-1")).thenReturn(List.of("user-a", "user-b"));

        publisher.publish("family-1");

        verify(messagingTemplate).convertAndSend(
                eq("/topic/families/family-1/presence"),
                eq(new PresenceResponse(List.of("user-a", "user-b"))));
    }
}
```

- [ ] **Step 2: Ejecutar el test y confirmar que falla**

Run: `mvn -f backend/pom.xml -Dtest=PresencePublisherTest test`
Expected: FAIL (compilación) — `PresenceResponse` y `PresencePublisher` no existen todavía.

- [ ] **Step 3: Implementación mínima**

Crear `backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceResponse.java`:

```java
package org.gipsybuho.recetasfamiliares.presence;

import java.util.List;

public record PresenceResponse(List<String> onlineUserIds) {}
```

Crear `backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresencePublisher.java`:

```java
package org.gipsybuho.recetasfamiliares.presence;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Difunde el snapshot completo de presencia de una familia (no deltas: evita
 * bugs de eventos perdidos si un cliente se suscribe tarde).
 */
@Component
public class PresencePublisher {

    static final String TOPIC_PREFIX = "/topic/families/";
    static final String TOPIC_SUFFIX = "/presence";

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceRegistry registry;

    public PresencePublisher(SimpMessagingTemplate messagingTemplate, PresenceRegistry registry) {
        this.messagingTemplate = messagingTemplate;
        this.registry = registry;
    }

    static String topicFor(String familyId) {
        return TOPIC_PREFIX + familyId + TOPIC_SUFFIX;
    }

    public void publish(String familyId) {
        messagingTemplate.convertAndSend(topicFor(familyId), new PresenceResponse(registry.onlineUserIds(familyId)));
    }
}
```

- [ ] **Step 4: Ejecutar el test y confirmar que pasa**

Run: `mvn -f backend/pom.xml -Dtest=PresencePublisherTest test`
Expected: `BUILD SUCCESS`, 1 test, 0 fallos.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceResponse.java backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresencePublisher.java backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresencePublisherTest.java
git commit -m "feat(backend): PresenceResponse + PresencePublisher (difusion STOMP)"
```

---

## Task 3: Backend — autorizar SUBSCRIBE de presencia en `ChatStompAuthChannelInterceptor`

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptor.java`
- Modify: `backend/src/test/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptorTest.java`

**Interfaces:**
- Consumes: `PresenceRegistry.subscribe(sessionId, familyId, userId): boolean` (Task 1), `PresencePublisher.publish(familyId): void` (Task 2).
- Produces: SUBSCRIBE a `/topic/families/{familyId}/presence` autorizado con el mismo criterio de membership que `/chat`; efecto secundario: registra la conexión y difunde el snapshot actualizado.

Este interceptor es el único registrado para todo el canal STOMP de la app (`WebSocketConfig.configureClientInboundChannel`), no solo para chat — generalizar su SUBSCRIBE es el patrón ya establecido, no una reestructuración nueva.

- [ ] **Step 1: Escribir los tests que fallan**

Añadir a `backend/src/test/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptorTest.java`. Primero actualizar `setUp()` para inyectar los dos nuevos mocks (rompe la firma del constructor a propósito, es el RED de este task):

```java
import org.gipsybuho.recetasfamiliares.presence.PresenceRegistry;
import org.gipsybuho.recetasfamiliares.presence.PresencePublisher;
// ... (resto de imports existentes sin cambios)

class ChatStompAuthChannelInterceptorTest {

    private static final String USER_ID = "user-123";
    private static final String FAMILY_ID = "fam-abc";
    private static final String TOPIC = "/topic/families/" + FAMILY_ID + "/chat";
    private static final String PRESENCE_TOPIC = "/topic/families/" + FAMILY_ID + "/presence";

    private JwtService jwtService;
    private FamilyMemberRepository familyMemberRepository;
    private PresenceRegistry presenceRegistry;
    private PresencePublisher presencePublisher;
    private MessageChannel channel;
    private ChatStompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = Mockito.mock(JwtService.class);
        familyMemberRepository = Mockito.mock(FamilyMemberRepository.class);
        presenceRegistry = Mockito.mock(PresenceRegistry.class);
        presencePublisher = Mockito.mock(PresencePublisher.class);
        channel = Mockito.mock(MessageChannel.class);
        interceptor = new ChatStompAuthChannelInterceptor(
                jwtService, familyMemberRepository, presenceRegistry, presencePublisher);
    }
```

Añadir estos nuevos tests al final de la clase (antes de los métodos helper `send`/`connect`/`subscribe`):

```java
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
```

- [ ] **Step 2: Ejecutar los tests y confirmar que fallan**

Run: `mvn -f backend/pom.xml -Dtest=ChatStompAuthChannelInterceptorTest test`
Expected: FAIL (compilación) — el constructor de `ChatStompAuthChannelInterceptor` todavía solo acepta 2 argumentos.

- [ ] **Step 3: Implementación mínima**

Reemplazar el contenido completo de `backend/src/main/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptor.java`:

```java
package org.gipsybuho.recetasfamiliares.chat;

import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.presence.PresencePublisher;
import org.gipsybuho.recetasfamiliares.presence.PresenceRegistry;
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

    private final JwtService jwtService;
    private final FamilyMemberRepository familyMemberRepository;
    private final PresenceRegistry presenceRegistry;
    private final PresencePublisher presencePublisher;

    public ChatStompAuthChannelInterceptor(
            JwtService jwtService,
            FamilyMemberRepository familyMemberRepository,
            PresenceRegistry presenceRegistry,
            PresencePublisher presencePublisher
    ) {
        this.jwtService = jwtService;
        this.familyMemberRepository = familyMemberRepository;
        this.presenceRegistry = presenceRegistry;
        this.presencePublisher = presencePublisher;
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
```

- [ ] **Step 4: Ejecutar los tests y confirmar que pasan**

Run: `mvn -f backend/pom.xml -Dtest=ChatStompAuthChannelInterceptorTest test`
Expected: `BUILD SUCCESS`, 12 tests (8 existentes + 4 nuevos), 0 fallos.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptor.java backend/src/test/java/org/gipsybuho/recetasfamiliares/chat/ChatStompAuthChannelInterceptorTest.java
git commit -m "feat(backend): autoriza SUBSCRIBE de presencia y registra conexiones"
```

---

## Task 4: Backend — `PresenceDisconnectListener`

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceDisconnectListener.java`
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresenceDisconnectListenerTest.java`

**Interfaces:**
- Consumes: `PresenceRegistry.unsubscribeSession(sessionId): Set<String>` (Task 1), `PresencePublisher.publish(familyId): void` (Task 2).
- Produces: limpieza automática del registro y difusión al cerrarse cualquier sesión WebSocket (cierre de app, pérdida de red, logout).

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresenceDisconnectListenerTest.java`:

```java
package org.gipsybuho.recetasfamiliares.presence;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@ExtendWith(MockitoExtension.class)
class PresenceDisconnectListenerTest {

    @Mock
    private PresenceRegistry registry;
    @Mock
    private PresencePublisher publisher;

    private PresenceDisconnectListener listener;

    @BeforeEach
    void setUp() {
        listener = new PresenceDisconnectListener(registry, publisher);
    }

    @Test
    void cleansUpRegistryAndPublishesForEachAffectedFamily() {
        when(registry.unsubscribeSession("session-1")).thenReturn(Set.of("family-1", "family-2"));
        SessionDisconnectEvent event = disconnectEvent("session-1");

        listener.onSessionDisconnect(event);

        verify(publisher).publish("family-1");
        verify(publisher).publish("family-2");
    }

    @Test
    void noPublishWhenSessionHadNoPresenceSubscriptions() {
        when(registry.unsubscribeSession("session-2")).thenReturn(Set.of());
        SessionDisconnectEvent event = disconnectEvent("session-2");

        listener.onSessionDisconnect(event);

        verifyNoInteractions(publisher);
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId) {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
    }
}
```

- [ ] **Step 2: Ejecutar el test y confirmar que falla**

Run: `mvn -f backend/pom.xml -Dtest=PresenceDisconnectListenerTest test`
Expected: FAIL (compilación) — `PresenceDisconnectListener` no existe todavía.

- [ ] **Step 3: Implementación mínima**

Crear `backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceDisconnectListener.java`:

```java
package org.gipsybuho.recetasfamiliares.presence;

import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Limpia el registro de presencia cuando se cierra una sesion WebSocket
 * (cierre de app, perdida de red, logout). Spring emite este evento para
 * toda desconexion STOMP; hasta ahora el proyecto no tenia listener para el.
 */
@Component
public class PresenceDisconnectListener {

    private final PresenceRegistry registry;
    private final PresencePublisher publisher;

    public PresenceDisconnectListener(PresenceRegistry registry, PresencePublisher publisher) {
        this.registry = registry;
        this.publisher = publisher;
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        Set<String> changedFamilies = registry.unsubscribeSession(event.getSessionId());
        changedFamilies.forEach(publisher::publish);
    }
}
```

- [ ] **Step 4: Ejecutar el test y confirmar que pasa**

Run: `mvn -f backend/pom.xml -Dtest=PresenceDisconnectListenerTest test`
Expected: `BUILD SUCCESS`, 2 tests, 0 fallos.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceDisconnectListener.java backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresenceDisconnectListenerTest.java
git commit -m "feat(backend): limpia presencia al desconectarse la sesion WebSocket"
```

---

## Task 5: Backend — `PresenceController` (REST snapshot inicial)

**Files:**
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceController.java`
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresenceControllerTest.java`

**Interfaces:**
- Consumes: `PresenceRegistry.onlineUserIds(familyId): List<String>` (Task 1), `FamilyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse`.
- Produces: `GET /api/v1/families/{familyId}/presence` → `PresenceResponse` (200) o 403 si el usuario no pertenece a la familia. Usado por Desktop/Android para el estado inicial al abrir Miembros (Tasks 7 y 10).

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresenceControllerTest.java`:

```java
package org.gipsybuho.recetasfamiliares.presence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PresenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsEmptySnapshotForFamilyMemberWithNoActiveConnections() throws Exception {
        RegisteredUser owner = register(uniqueEmail("presence-owner"), "Familia Presencia");

        mockMvc.perform(get("/api/v1/families/{familyId}/presence", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineUserIds").isArray())
                .andExpect(jsonPath("$.onlineUserIds.length()").value(0));
    }

    @Test
    void blocksPresenceAccessAcrossFamilies() throws Exception {
        RegisteredUser owner = register(uniqueEmail("presence-private-owner"), "Familia Presencia Privada");
        RegisteredUser other = register(uniqueEmail("presence-private-other"), "Familia Presencia Otra");

        mockMvc.perform(get("/api/v1/families/{familyId}/presence", owner.familyId())
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isForbidden());
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Presence User",
                                  "password": "very-secure-password",
                                  "familyName": "%s"
                                }
                                """.formatted(email, familyName)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new RegisteredUser(
                response.get("accessToken").asText(),
                response.get("family").get("id").asText(),
                response.get("user").get("id").asText());
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private record RegisteredUser(String accessToken, String familyId, String userId) {}
}
```

- [ ] **Step 2: Ejecutar el test y confirmar que falla**

Run: `mvn -f backend/pom.xml -Dtest=PresenceControllerTest test`
Expected: FAIL (404/compilación) — `PresenceController` no existe todavía.

- [ ] **Step 3: Implementación mínima**

Crear `backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceController.java`:

```java
package org.gipsybuho.recetasfamiliares.presence;

import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/families/{familyId}/presence")
public class PresenceController {

    private final FamilyMemberRepository familyMemberRepository;
    private final PresenceRegistry presenceRegistry;

    public PresenceController(FamilyMemberRepository familyMemberRepository, PresenceRegistry presenceRegistry) {
        this.familyMemberRepository = familyMemberRepository;
        this.presenceRegistry = presenceRegistry;
    }

    @GetMapping
    public PresenceResponse snapshot(@PathVariable String familyId, Authentication authentication) {
        String userId = authentication.getName();
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
        return new PresenceResponse(presenceRegistry.onlineUserIds(familyId));
    }
}
```

- [ ] **Step 4: Ejecutar el test y confirmar que pasa**

Run: `mvn -f backend/pom.xml -Dtest=PresenceControllerTest test`
Expected: `BUILD SUCCESS`, 2 tests, 0 fallos.

- [ ] **Step 5: Ejecutar toda la suite backend antes de cerrar la mitad del plan**

Run: `mvn -f backend/pom.xml test`
Expected: `BUILD SUCCESS`, sin regresiones en el resto de la suite (chat, families, rankings, etc.).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/presence/PresenceController.java backend/src/test/java/org/gipsybuho/recetasfamiliares/presence/PresenceControllerTest.java
git commit -m "feat(backend): GET /families/{familyId}/presence (snapshot inicial)"
```

---

## Task 6: Desktop — `ChatSocket.java` se suscribe también a presencia

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ChatSocket.java`
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/FamilyDtos.java`
- Test: `desktop/src/test/java/org/gipsybuho/recetasfamiliares/api/ChatSocketFrameParsingTest.java`

**Interfaces:**
- Produces: `FamilyDtos.PresenceResponse(List<String> onlineUserIds)` (record, reusado también por el REST de Task 7); `ChatSocket` gana un parámetro de constructor `Consumer<Set<String>> onPresenceUpdate`; `static String ChatSocket.extractHeader(String frame, String name)` (package-private, testable).

- [ ] **Step 1: Escribir el test que falla**

Crear `desktop/src/test/java/org/gipsybuho/recetasfamiliares/api/ChatSocketFrameParsingTest.java`:

```java
package org.gipsybuho.recetasfamiliares.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatSocketFrameParsingTest {

    @Test
    void extractsHeaderValueFromFrame() {
        String frame = "MESSAGE\n"
                + "destination:/topic/families/fam-1/presence\n"
                + "subscription:sub-presence\n"
                + "\n"
                + "{\"onlineUserIds\":[\"user-a\"]}";

        assertEquals("/topic/families/fam-1/presence", ChatSocket.extractHeader(frame, "destination"));
    }

    @Test
    void returnsNullWhenHeaderMissing() {
        String frame = "MESSAGE\n"
                + "subscription:sub-chat\n"
                + "\n"
                + "{}";

        assertNull(ChatSocket.extractHeader(frame, "destination"));
    }

    @Test
    void ignoresHeaderNameAppearingOnlyInBody() {
        String frame = "MESSAGE\n"
                + "subscription:sub-chat\n"
                + "\n"
                + "{\"destination\":\"not-a-header\"}";

        assertNull(ChatSocket.extractHeader(frame, "destination"));
    }
}
```

- [ ] **Step 2: Ejecutar el test y confirmar que falla**

Run: `mvn -f desktop/pom.xml -Dtest=ChatSocketFrameParsingTest test`
Expected: FAIL (compilación) — `ChatSocket.extractHeader` no existe todavía.

- [ ] **Step 3: Añadir `PresenceResponse` a `FamilyDtos.java`**

En `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/FamilyDtos.java`, añadir junto a los demás records (por ejemplo, después de `FamilyStatsResponse`):

```java
    /** Snapshot de miembros conectados ahora mismo (WebSocket activo). */
    public record PresenceResponse(java.util.List<String> onlineUserIds) {}
```

- [ ] **Step 4: Modificar `ChatSocket.java`**

En `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ChatSocket.java`:

1. Añadir imports:

```java
import org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos;

import java.util.HashSet;
import java.util.Set;
```

2. Añadir campo `presenceTopic` y `onPresenceUpdate`, y el parámetro al constructor:

```java
    private final String topic;
    private final String presenceTopic;
    private final Gson gson;
    private final Consumer<ChatDtos.ChatMessage> onMessage;
    private final Consumer<Boolean> onConnectionChange;
    private final Consumer<Set<String>> onPresenceUpdate;
```

```java
    public ChatSocket(
            ApiClient apiClient,
            Supplier<String> tokenSupplier,
            String familyId,
            Gson gson,
            Consumer<ChatDtos.ChatMessage> onMessage,
            Consumer<Boolean> onConnectionChange,
            Consumer<Set<String>> onPresenceUpdate
    ) {
        this.apiClient = apiClient;
        this.tokenSupplier = tokenSupplier;
        this.familyId = familyId;
        this.wsUrl = toWebSocketUrl(apiClient.getBaseUrl());
        this.topic = "/topic/families/" + familyId + "/chat";
        this.presenceTopic = "/topic/families/" + familyId + "/presence";
        this.gson = gson;
        this.onMessage = onMessage;
        this.onConnectionChange = onConnectionChange;
        this.onPresenceUpdate = onPresenceUpdate;
    }
```

3. Reemplazar el caso `"CONNECTED"` dentro de `handleFrame` para suscribir también a presencia:

```java
            case "CONNECTED" -> {
                String subscribeChat = "SUBSCRIBE\n"
                        + "id:sub-chat\n"
                        + "destination:" + topic + "\n"
                        + "\n"
                        + NUL;
                socket.send(subscribeChat);
                String subscribePresence = "SUBSCRIBE\n"
                        + "id:sub-presence\n"
                        + "destination:" + presenceTopic + "\n"
                        + "\n"
                        + NUL;
                socket.send(subscribePresence);
                reconnectAttempt = 0;
                onConnectionChange.accept(true);
            }
```

4. Reemplazar el caso `"MESSAGE"` para despachar por destino:

```java
            case "MESSAGE" -> {
                String destination = extractHeader(frame, "destination");
                int split = frame.indexOf("\n\n");
                String body = split >= 0 ? frame.substring(split + 2).trim() : "";
                if (body.isEmpty()) {
                    // no-op
                } else if (presenceTopic.equals(destination)) {
                    handlePresenceMessage(body);
                } else {
                    handleChatMessage(body);
                }
            }
```

5. Añadir los métodos privados `handleChatMessage`, `handlePresenceMessage` y el estático `extractHeader` (junto a los demás métodos privados, por ejemplo tras `handleFrame`):

```java
    private void handleChatMessage(String body) {
        try {
            ChatDtos.ChatMessage message = gson.fromJson(body, ChatDtos.ChatMessage.class);
            if (message != null && message.isUsable() && familyId.equals(message.familyId())) {
                onMessage.accept(message);
            }
        } catch (RuntimeException ignored) {
            // Frame no parseable: se descarta sin romper la conexion.
        }
    }

    private void handlePresenceMessage(String body) {
        try {
            FamilyDtos.PresenceResponse presence = gson.fromJson(body, FamilyDtos.PresenceResponse.class);
            if (presence != null && presence.onlineUserIds() != null) {
                onPresenceUpdate.accept(new HashSet<>(presence.onlineUserIds()));
            }
        } catch (RuntimeException ignored) {
            // Frame no parseable: se descarta sin romper la conexion.
        }
    }

    static String extractHeader(String frame, String name) {
        int headersEnd = frame.indexOf("\n\n");
        String headerBlock = headersEnd >= 0 ? frame.substring(0, headersEnd) : frame;
        String prefix = name + ":";
        for (String line : headerBlock.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        return null;
    }
```

6. Borrar el antiguo bloque `case "MESSAGE" -> { ... }` original (el que solo llamaba a `gson.fromJson` inline) ya que su lógica se movió a `handleChatMessage`.

- [ ] **Step 5: Ejecutar el test y confirmar que pasa**

Run: `mvn -f desktop/pom.xml -Dtest=ChatSocketFrameParsingTest test`
Expected: `BUILD SUCCESS`, 3 tests, 0 fallos.

- [ ] **Step 6: Compilar todo el módulo para detectar call-sites rotos**

Run: `mvn -f desktop/pom.xml -DskipTests compile`
Expected: FALLA — `ChatRepository.java` (Task 7) todavía instancia `ChatSocket` con la firma antigua de 6 argumentos. Confirmar que el único error es ese call-site (evidencia de que el cambio está bien acotado); se corrige en el siguiente task.

- [ ] **Step 7: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ChatSocket.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/FamilyDtos.java desktop/src/test/java/org/gipsybuho/recetasfamiliares/api/ChatSocketFrameParsingTest.java
git commit -m "feat(desktop): ChatSocket se suscribe tambien a presencia por familia"
```

---

## Task 7: Desktop — `ChatRepository`/`FamilyRepository` exponen presencia

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.java`
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepository.java`
- Modify: `desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepositoryHttpTest.java`

**Interfaces:**
- Consumes: `ChatSocket` con el constructor de 7 argumentos (Task 6); `FamilyDtos.PresenceResponse` (Task 6).
- Produces: `ChatRepository.openRealtime(onMessage, onConnectionChange, onPresenceUpdate): ChatSocket`; `ChatRepository.setPresenceListener(Consumer<Set<String>>)`; `ChatRepository.lastOnlineUserIds(): Set<String>`; `FamilyRepository.loadPresence(String familyId): FamilyDtos.PresenceResponse`. Usados por `FamilyMembersView` (Task 8).

- [ ] **Step 1: Escribir el test que falla**

Añadir a `desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepositoryHttpTest.java` (mismo fichero, mismo patrón `MockWebServer` que las pruebas existentes de `createFamily`):

```java
    @Test
    void loadPresenceDevuelveSnapshotDeUsuariosOnline() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"onlineUserIds":["user-a","user-b"]}
                        """));

        FamilyDtos.PresenceResponse presence = repository.loadPresence("fam-1");

        assertEquals(java.util.List.of("user-a", "user-b"), presence.onlineUserIds());
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/families/fam-1/presence", request.getPath());
    }
```

- [ ] **Step 2: Ejecutar el test y confirmar que falla**

Run: `mvn -f desktop/pom.xml -Dtest=FamilyRepositoryHttpTest test`
Expected: FAIL (compilación) — `FamilyRepository.loadPresence` no existe todavía.

- [ ] **Step 3: Implementación mínima**

En `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepository.java`, añadir junto a `loadMembers`:

```java
    /** Snapshot inicial de miembros conectados ahora mismo (WebSocket activo). */
    public FamilyDtos.PresenceResponse loadPresence(String familyId) throws ApiException {
        return api.get("api/v1/families/" + familyId + "/presence", FamilyDtos.PresenceResponse.class);
    }
```

En `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.java`:

1. Añadir imports:

```java
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
```

2. Añadir campos de estado compartido y el listener registrable (mismo patrón single-listener que `ChatView.setUnreadListener`):

```java
    private final AtomicReference<Set<String>> lastOnlineUserIds = new AtomicReference<>(Set.of());
    private volatile Consumer<Set<String>> presenceListener;

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
```

3. Actualizar `openRealtime` para pasar el nuevo callback al `ChatSocket`:

```java
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
                onConnectionChange,
                this::handlePresenceUpdate);
        socket.connect();
        return socket;
    }
```

- [ ] **Step 4: Ejecutar el test y confirmar que pasa**

Run: `mvn -f desktop/pom.xml -Dtest=FamilyRepositoryHttpTest test`
Expected: `BUILD SUCCESS`, 3 tests (2 existentes + 1 nuevo), 0 fallos.

- [ ] **Step 5: Compilar todo el módulo (confirma que el call-site roto del Task 6 ya está resuelto)**

Run: `mvn -f desktop/pom.xml -DskipTests compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepository.java desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepositoryHttpTest.java
git commit -m "feat(desktop): ChatRepository/FamilyRepository exponen presencia online"
```

---

## Task 8: Desktop — punto de presencia en `FamilyMembersView`

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/FamilyMembersView.java`
- Modify: `desktop/src/main/resources/style.css`

**Interfaces:**
- Consumes: `FamilyRepository.loadPresence(familyId)` (Task 7), `ChatRepository.setPresenceListener(...)` / `lastOnlineUserIds()` (Task 7).

Sin test automatizado nuevo en este task: es renderizado JavaFX puro (mismo criterio que el resto de `FamilyMembersView`, sin tests de UI en el proyecto); se verifica con la suite existente + verificación manual al final del plan.

- [ ] **Step 1: Añadir estado `online` a `MemberRow`**

En `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/FamilyMembersView.java`, modificar la clase `MemberRow` (al final del fichero) para añadir un campo mutable de presencia:

```java
    public static final class MemberRow {
        private final String userId;
        private final String displayName;
        private final String email;
        private final String role;
        private final boolean self;
        private boolean online;

        public MemberRow(String userId, String displayName, String email, String role, boolean self) {
            this.userId      = userId;
            this.displayName = displayName != null ? displayName : "—";
            this.email       = email != null ? email : "—";
            this.role        = role != null ? role : "MEMBER";
            this.self        = self;
        }

        public String getUserId()      { return userId; }
        public String getDisplayName() { return displayName; }
        public String getEmail()       { return email; }
        public String getRole()        { return role; }
        public boolean isSelf()        { return self; }
        public boolean isOnline()      { return online; }
        public void setOnline(boolean online) { this.online = online; }

        /** Label shown in the table — includes "(Tú)" marker for self. */
        public String getRoleLabel() {
            String label = switch (role.toUpperCase()) {
                case "OWNER"  -> "Propietario";
                case "ADMIN"  -> "Administrador";
                default       -> "Miembro";
            };
            return self ? label + " (Tú)" : label;
        }
    }
```

- [ ] **Step 2: Añadir la columna de presencia en `buildTable()`**

Añadir el import al principio del fichero:

```java
import javafx.scene.shape.Circle;
```

Modificar `buildTable()` para insertar la nueva columna antes de `nameCol`:

```java
    private TableView<MemberRow> buildTable() {
        TableView<MemberRow> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.getStyleClass().add("data-table");
        tv.setPlaceholder(new Label("Sin miembros"));
        tv.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<MemberRow, MemberRow> onlineCol = new TableColumn<>("");
        onlineCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        onlineCol.setCellFactory(col -> new TableCell<>() {
            private final Circle dot = new Circle(5);

            @Override
            protected void updateItem(MemberRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                dot.getStyleClass().setAll(row.isOnline() ? "presence-dot-online" : "presence-dot-offline");
                setGraphic(dot);
            }
        });
        onlineCol.setSortable(false);
        onlineCol.setResizable(false);
        onlineCol.setMinWidth(28);
        onlineCol.setMaxWidth(28);

        TableColumn<MemberRow, String> nameCol = new TableColumn<>("Nombre");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("displayName"));
        nameCol.setMinWidth(160);

        TableColumn<MemberRow, String> emailCol = new TableColumn<>("Correo electrónico");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));
        emailCol.setMinWidth(200);

        TableColumn<MemberRow, String> roleCol = new TableColumn<>("Rol");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("roleLabel"));
        roleCol.setMinWidth(120);

        tv.getColumns().add(onlineCol);
        tv.getColumns().add(nameCol);
        tv.getColumns().add(emailCol);
        tv.getColumns().add(roleCol);
        return tv;
    }
```

- [ ] **Step 3: Registrar el listener de presencia y aplicar snapshots**

Modificar el constructor y añadir los métodos de aplicación de presencia:

```java
    public FamilyMembersView(AppContext context, Runnable onFamiliesChanged) {
        this.context = context;
        this.onFamiliesChanged = onFamiliesChanged;
        build();
        context.getChatRepository().setPresenceListener(online ->
                Platform.runLater(() -> applyPresence(online)));
        refresh();
    }
```

Añadir el método `applyPresence` (junto a `refresh()`):

```java
    /** Aplica un snapshot de presencia a las filas ya cargadas. Llamado en el hilo JavaFX. */
    private void applyPresence(java.util.Set<String> onlineUserIds) {
        for (MemberRow row : table.getItems()) {
            row.setOnline(onlineUserIds.contains(row.getUserId()));
        }
        table.refresh();
    }
```

Modificar `refresh()` para aplicar primero el snapshot en caché (evita parpadeo si ya llegó por WS) y luego pedir el snapshot REST autoritativo. Reemplazar el bloque `Platform.runLater(() -> { ... table.getItems().clear(); ... })` dentro de `refresh()`:

```java
    public void refresh() {
        statusLabel.setText("Cargando miembros...");
        table.getItems().clear();
        String familyId = context.getSession().getFamilyId();
        if (familyId == null || familyId.isBlank()) {
            Platform.runLater(() -> statusLabel.setText("Sin sesión de familia activa."));
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                // Load family name
                FamilyDtos.FamilyResponse[] families = context.getFamilyRepository().loadMyFamilies();
                // Load members
                FamilyDtos.FamilyMemberResponse[] members = context.getFamilyRepository().loadMembers(familyId);
                Platform.runLater(() -> {
                    if (families.length > 0) {
                        familyLabel.setText(families[0].name() != null ? families[0].name() : "—");
                    }
                    FamilyRole myRole = context.getSession().getFamilyRole();
                    roleLabel.setText(myRole != null ? myRole.displayName() : "—");

                    table.getItems().clear();
                    String myEmail = context.getSession().getEmail();
                    for (FamilyDtos.FamilyMemberResponse m : members) {
                        boolean isSelf = myEmail != null && myEmail.equalsIgnoreCase(m.email());
                        table.getItems().add(new MemberRow(
                                m.userId(), m.displayName(), m.email(), m.role(), isSelf));
                    }
                    applyPresence(context.getChatRepository().lastOnlineUserIds());
                    statusLabel.setText(members.length + " miembro(s)");
                });
                FamilyDtos.PresenceResponse presence = context.getFamilyRepository().loadPresence(familyId);
                Platform.runLater(() -> applyPresence(new java.util.HashSet<>(presence.onlineUserIds())));
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al cargar: " + ex.getMessage()));
            }
        });
    }
```

- [ ] **Step 4: Estilos del punto de presencia**

Añadir a `desktop/src/main/resources/style.css` (junto a otras clases semánticas como `.stock-expiring-date`):

```css
.presence-dot-online {
    -fx-fill: #22C55E;
}

.presence-dot-offline {
    -fx-fill: recetas-text-muted;
}
```

- [ ] **Step 5: Compilar y ejecutar la suite completa de Desktop**

Run: `mvn -f desktop/pom.xml test`
Expected: `BUILD SUCCESS`, sin regresiones (suite completa, incluye los tests nuevos de Tasks 6 y 7).

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/FamilyMembersView.java desktop/src/main/resources/style.css
git commit -m "feat(desktop): punto de presencia online en la pantalla Miembros"
```

---

## Task 9: Android — `ChatSocket.kt` se suscribe también a presencia

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocket.kt`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/dto/ApiDtos.kt`
- Test: `android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocketFrameParsingTest.kt`

**Interfaces:**
- Produces: `PresenceResponseDto(onlineUserIds: List<String>)` (data class, reusado también por el Retrofit de Task 10); `ChatSocket` gana un parámetro de constructor `onPresenceUpdate: (Set<String>) -> Unit`; `internal fun ChatSocket.extractHeader(frame: String, name: String): String?` (testable).

- [ ] **Step 1: Escribir el test que falla**

Crear `android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocketFrameParsingTest.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatSocketFrameParsingTest {

    @Test
    fun extractsHeaderValueFromFrame() {
        val frame = "MESSAGE\n" +
            "destination:/topic/families/fam-1/presence\n" +
            "subscription:sub-presence\n" +
            "\n" +
            "{\"onlineUserIds\":[\"user-a\"]}"

        assertEquals("/topic/families/fam-1/presence", extractStompHeader(frame, "destination"))
    }

    @Test
    fun returnsNullWhenHeaderMissing() {
        val frame = "MESSAGE\n" +
            "subscription:sub-chat\n" +
            "\n" +
            "{}"

        assertNull(extractStompHeader(frame, "destination"))
    }

    @Test
    fun ignoresHeaderNameAppearingOnlyInBody() {
        val frame = "MESSAGE\n" +
            "subscription:sub-chat\n" +
            "\n" +
            "{\"destination\":\"not-a-header\"}"

        assertNull(extractStompHeader(frame, "destination"))
    }
}
```

- [ ] **Step 2: Ejecutar el test y confirmar que falla**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.ChatSocketFrameParsingTest"` (desde `android/`)
Expected: FAIL (compilación) — `extractStompHeader` no existe todavía.

- [ ] **Step 3: Añadir `PresenceResponseDto` a `ApiDtos.kt`**

En `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/dto/ApiDtos.kt`, añadir junto a `ChatExportDto`:

```kotlin
data class PresenceResponseDto(
    val onlineUserIds: List<String>
)
```

- [ ] **Step 4: Modificar `ChatSocket.kt`**

En `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocket.kt`:

1. Añadir import:

```kotlin
import org.gipsybuho.recetasfamiliares.data.remote.dto.PresenceResponseDto
```

2. Añadir el campo `presenceTopic` y el parámetro `onPresenceUpdate` al constructor:

```kotlin
class ChatSocket(
    private val httpClient: OkHttpClient,
    baseUrl: String,
    private val sessionStore: SessionStore,
    private val familyId: String,
    private val gson: Gson,
    private val onMessage: (ChatMessageDto) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit,
    private val onPresenceUpdate: (Set<String>) -> Unit
) {

    private val wsUrl: String = toWebSocketUrl(baseUrl)
    private val topic: String = "/topic/families/$familyId/chat"
    private val presenceTopic: String = "/topic/families/$familyId/presence"
```

3. Reemplazar el bloque `"CONNECTED" ->` dentro de `handleFrame` para suscribir también a presencia:

```kotlin
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
                reconnectAttempt = 0
                onConnectionChange(true)
            }
```

4. Reemplazar el bloque `"MESSAGE" ->` para despachar por destino:

```kotlin
            "MESSAGE" -> {
                val destination = extractStompHeader(frame, "destination")
                val body = frame.substringAfter("\n\n", "").trim()
                if (body.isEmpty()) {
                    // no-op
                } else if (destination == presenceTopic) {
                    handlePresenceMessage(body)
                } else {
                    handleChatMessage(body)
                }
            }
```

5. Añadir los métodos privados `handleChatMessage` y `handlePresenceMessage` (junto a `handleFrame`):

```kotlin
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
```

6. Borrar el antiguo bloque `"MESSAGE" -> { ... }` original (su lógica se movió a `handleChatMessage`).

7. Añadir la función top-level `extractStompHeader` al final del fichero (fuera de la clase, junto a los demás `private companion object` — como función de fichero para que el test la importe directamente):

```kotlin
internal fun extractStompHeader(frame: String, name: String): String? {
    val headersEnd = frame.indexOf("\n\n")
    val headerBlock = if (headersEnd >= 0) frame.substring(0, headersEnd) else frame
    val prefix = "$name:"
    return headerBlock.lineSequence().firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
}
```

8. Usar esta función en el paso 4 (`extractStompHeader(frame, "destination")` ya referenciado arriba).

- [ ] **Step 5: Ejecutar el test y confirmar que pasa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.ChatSocketFrameParsingTest"` (desde `android/`)
Expected: `BUILD SUCCESSFUL`, 3 tests, 0 fallos.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocket.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/dto/ApiDtos.kt android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/remote/ChatSocketFrameParsingTest.kt
git commit -m "feat(android): ChatSocket se suscribe tambien a presencia por familia"
```

---

## Task 10: Android — `RecetasApi`/`FamilyMemberRepository`/`ViewModel` exponen presencia

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/RecetasApi.kt`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/Repositories.kt`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.kt`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt`
- Test: `android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyMemberRepositoryPresenceTest.kt`

**Interfaces:**
- Consumes: `PresenceResponseDto` (Task 9); `ChatSocket` con el constructor de 8 parámetros (Task 9).
- Produces: `FamilyMemberRepository.presence(): PresenceResponseDto`; `RecetasViewModel.onlineUserIds: StateFlow<Set<String>>`; `RecetasViewModel.loadPresence(): Unit`. Usados por `ProfileScreen.kt` (Task 11).

- [ ] **Step 1: Escribir el test que falla**

Crear `android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyMemberRepositoryPresenceTest.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.PresenceResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyMemberRepositoryPresenceTest {

    private val api = mockk<RecetasApi>()
    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val repository = FamilyMemberRepository(api, sessionStore)

    @Test
    fun presenceReturnsOnlineUserIdsForActiveFamily() = runTest {
        every { sessionStore.familyId } returns "family-1"
        coEvery { api.presence("family-1") } returns PresenceResponseDto(onlineUserIds = listOf("user-a"))

        val result = repository.presence()

        assertEquals(listOf("user-a"), result.onlineUserIds)
    }

    @Test
    fun presenceReturnsEmptyWithoutActiveFamily() = runTest {
        every { sessionStore.familyId } returns null

        val result = repository.presence()

        assertTrue(result.onlineUserIds.isEmpty())
    }
}
```

- [ ] **Step 2: Ejecutar el test y confirmar que falla**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.FamilyMemberRepositoryPresenceTest"` (desde `android/`)
Expected: FAIL (compilación) — `FamilyMemberRepository.presence()` no existe todavía.

- [ ] **Step 3: Añadir el endpoint Retrofit**

En `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/RecetasApi.kt`, el final del fichero es hoy:

```kotlin
    @GET("api/v1/families/{familyId}/chat/export")
    suspend fun exportChat(
        @Path("familyId") familyId: String
    ): ChatExportDto
}
```

Reemplazarlo por (añade el nuevo método antes de la llave de cierre de la interfaz):

```kotlin
    @GET("api/v1/families/{familyId}/chat/export")
    suspend fun exportChat(
        @Path("familyId") familyId: String
    ): ChatExportDto

    @GET("api/v1/families/{familyId}/presence")
    suspend fun presence(
        @Path("familyId") familyId: String
    ): PresenceResponseDto
}
```

- [ ] **Step 4: Implementación mínima en `FamilyMemberRepository`**

En `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/Repositories.kt`, añadir junto a `userRecipeRankings()`:

```kotlin
    suspend fun presence(): PresenceResponseDto {
        val familyId = sessionStore.familyId ?: return PresenceResponseDto(emptyList())
        return api.presence(familyId)
    }
```

Añadir el import correspondiente al principio del fichero si no está ya:

```kotlin
import org.gipsybuho.recetasfamiliares.data.remote.dto.PresenceResponseDto
```

- [ ] **Step 5: Ejecutar el test y confirmar que pasa**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.FamilyMemberRepositoryPresenceTest"` (desde `android/`)
Expected: `BUILD SUCCESSFUL`, 2 tests, 0 fallos.

- [ ] **Step 6: Enchufar el callback de presencia en `ChatRepository.kt`**

En `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.kt`, actualizar `openRealtime`:

```kotlin
    fun openRealtime(
        onMessage: (ChatMessageDto) -> Unit,
        onConnectionChange: (Boolean) -> Unit,
        onPresenceUpdate: (Set<String>) -> Unit
    ): ChatSocket? {
        val family = familyId ?: return null
        val socket = ChatSocket(
            httpClient = httpClient,
            baseUrl = baseUrlProvider(),
            sessionStore = sessionStore,
            familyId = family,
            gson = gson,
            onMessage = { msg -> onMessage(normalizeAttachments(msg)) },
            onConnectionChange = onConnectionChange,
            onPresenceUpdate = onPresenceUpdate
        )
        socket.connect()
        return socket
    }
```

- [ ] **Step 7: Estado de presencia en `RecetasViewModel.kt`**

Añadir el StateFlow junto a `_familyMembers` (alrededor de la línea 341):

```kotlin
    private val _onlineUserIds = MutableStateFlow<Set<String>>(emptySet())
    val onlineUserIds: StateFlow<Set<String>> = _onlineUserIds.asStateFlow()
```

Añadir su limpieza en `clearFamilyScopedState()`:

```kotlin
    private fun clearFamilyScopedState() {
        _familyStats.value = null
        _familyMembers.value = emptyList()
        _onlineUserIds.value = emptySet()
        _userRecipeRankings.value = emptyList()
        _familyInfo.value = null
        _recipeRatings.value = emptyList()
        _chatMessages.value = emptyList()
        _chatHasMoreOlder.value = false
        chatOldestCursor = null
        _chatUnread.value = 0
        _recipeNextPage.value = 1
        _recipeHasMore.value = false
    }
```

Añadir la función de carga inicial, junto a `loadFamilyMembers()`:

```kotlin
    /** Snapshot inicial de presencia; las actualizaciones en vivo llegan por WebSocket. */
    fun loadPresence() {
        viewModelScope.launch {
            val familyId = container.sessionStore.familyId ?: return@launch
            runCatching { container.familyMemberRepository.presence() }
                .onSuccess {
                    if (familyId == container.sessionStore.familyId) {
                        _onlineUserIds.value = it.onlineUserIds.toSet()
                    }
                }
        }
    }
```

Actualizar `startChatBadge()` y `openChat()` para pasar el tercer callback (ambos comparten el mismo handler, idempotente):

```kotlin
    fun startChatBadge() {
        if (chatBadgeSocket != null || !_isLoggedIn.value) return
        chatBadgeSocket = container.chatRepository.openRealtime(
            onMessage = { msg ->
                val firstTime = chatBadgeSeenIds.add(msg.id)
                val fromOther = msg.authorUserId != null && msg.authorUserId != myUserId
                if (firstTime && !chatScreenOpen && fromOther && !msg.deleted) {
                    _chatUnread.update { it + 1 }
                }
            },
            onConnectionChange = {},
            onPresenceUpdate = { online -> _onlineUserIds.value = online }
        )
    }
```

```kotlin
        chatSocket = container.chatRepository.openRealtime(
            onMessage = { msg -> _chatMessages.update { mergeChat(it, listOf(msg)) } },
            onConnectionChange = { connected -> _chatConnected.value = connected },
            onPresenceUpdate = { online -> _onlineUserIds.value = online }
        )
```

- [ ] **Step 8: Compilar todo el módulo**

Run: `.\gradlew.bat :app:compileDebugKotlin` (desde `android/`)
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/RecetasApi.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/Repositories.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt android/app/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyMemberRepositoryPresenceTest.kt
git commit -m "feat(android): expone presencia online en el repositorio y el ViewModel"
```

---

## Task 11: Android — punto de presencia en `ProfileScreen.kt`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/ProfileScreen.kt`

**Interfaces:**
- Consumes: `RecetasViewModel.onlineUserIds: StateFlow<Set<String>>`, `RecetasViewModel.loadPresence()` (Task 10).

Sin test automatizado nuevo en este task: es renderizado Compose puro (mismo criterio que el resto de `ProfileScreen.kt`, sin tests de UI en el proyecto); se verifica con la suite existente + verificación manual al final del plan.

- [ ] **Step 1: Cargar el snapshot inicial junto a los miembros**

En el `LaunchedEffect(Unit)` que ya llama a `loadFamilyMembers()` (alrededor de la línea 326), añadir:

```kotlin
        LaunchedEffect(Unit) {
            viewModel.loadFamilyStats()
            viewModel.loadAccountStatus()
            viewModel.loadFamilyMembers()
            viewModel.loadPresence()
        }
```

- [ ] **Step 2: Pasar el estado a `FamilyMembersSection`**

Donde se llama a `FamilyMembersSection` (alrededor de la línea 416), añadir la recolección del StateFlow y el nuevo parámetro:

```kotlin
        val familyMembers by viewModel.familyMembers.collectAsState()
        val onlineUserIds by viewModel.onlineUserIds.collectAsState()
        if (familyMembers.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            FamilyMembersSection(
                members = familyMembers,
                onlineUserIds = onlineUserIds,
                isAdmin = isAdmin,
                myUserId = myUserId,
                onChangeRole = { member, newRole ->
                    memberRoleChange = MemberRoleChange(member, newRole)
                },
                onEditMember = { member ->
                    memberToEdit = member
                },
                onRemoveMember = { member ->
                    memberToRemove = member
                }
            )
        }
```

- [ ] **Step 3: Renderizar el punto en `FamilyMembersSection`**

Modificar la firma y el avatar de cada fila (alrededor de la línea 748):

```kotlin
@Composable
private fun FamilyMembersSection(
    members: List<FamilyMemberDto>,
    onlineUserIds: Set<String>,
    isAdmin: Boolean,
    myUserId: String?,
    onChangeRole: (FamilyMemberDto, String) -> Unit,
    onEditMember: (FamilyMemberDto) -> Unit,
    onRemoveMember: (FamilyMemberDto) -> Unit
) {
```

Envolver el `Surface` del avatar (dentro del `members.forEach { member -> ... }`) en un `Box` con el punto de presencia anclado en la esquina inferior derecha:

```kotlin
                    Box {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!member.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = member.avatarUrl,
                                        contentDescription = "Foto de ${member.displayName}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        member.displayName.split(" ").filter { it.isNotBlank() }.take(2)
                                            .map { it.first().uppercaseChar() }.joinToString(""),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        val online = member.userId in onlineUserIds
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(10.dp)
                                .background(
                                    color = if (online) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .border(1.5.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        )
                    }
```

(Sustituye únicamente el `Surface(...)` original: el resto de la fila — `Spacer`, `Column` con nombre/email, rol, menú de gestión — permanece igual.)

Añadir los imports necesarios si no están ya presentes en el fichero:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
```

- [ ] **Step 4: Compilar y ejecutar la suite completa de Android**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug` (desde `android/`)
Expected: `BUILD SUCCESSFUL`, sin regresiones (suite completa, incluye los tests nuevos de Tasks 9 y 10).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/ProfileScreen.kt
git commit -m "feat(android): punto de presencia online en la seccion de miembros"
```

---

## Verificación final (tras Task 11)

- [ ] Backend: `mvn -f backend/pom.xml test` → `BUILD SUCCESS` completo (todas las suites, no solo las de presencia).
- [ ] Desktop: `mvn -f desktop/pom.xml test` → `BUILD SUCCESS` completo.
- [ ] Android: `.\gradlew.bat testDebugUnitTest assembleDebug` (desde `android/`) → `BUILD SUCCESSFUL` completo.
- [ ] `VibeSec` sobre el diff completo del sprint (nuevo endpoint REST + nuevo canal WS con datos de membership): confirmar que el control de ownership es el mismo que ya usa `/chat`, sin secretos ni datos sensibles nuevos expuestos.
- [ ] Prueba manual (dos sesiones en la misma familia — dos cuentas o backend dev local + dos clientes, patrón ya usado en el sprint de "Crear familia" del 2026-07-19): abrir Miembros en ambos, verificar que el punto se enciende al conectar la segunda sesión y se apaga al cerrarla; verificar que una tercera familia no ve ningún cambio.
- [ ] Actualizar `CONTINUAR.md` con el cierre del sprint (agentes/skills usados, validaciones ejecutadas, riesgo residual: iOS y avisos de cambios de recetas/notas/stock quedan fuera, sprints propios).
