# Spec: Chat privado 1:1 entre miembros de una familia (Backend + Desktop + Android)

Fecha: 2026-07-19 (addendum de navegación Desktop: 2026-07-22)
Estado: aprobado en diseño y confirmado por escrito el 2026-07-22 (navegación Desktop, vía
companion visual de `superpowers:brainstorming`) — listo para `writing-plans`.

## Contexto

- Punto (14) de `paraImplementar.txt`: "chat que pueda ser privado entre un usuario y otro y
  chat familiar con todos los usuarios de la familia". El chat familiar grupal (fase 1) ya
  existe y queda intacto: texto, adjuntar imágenes, editar/borrar mensaje propio, rate limit,
  exportar historial, badge de no-leídos, presencia en vivo (sprint 2026-07-19 anterior).
- Este sprint añade chat privado 1:1, con **paridad completa** de funcionalidades respecto al
  chat familiar (decisión del usuario en brainstorming).
- Reutiliza la infraestructura WebSocket/STOMP ya construida (`WebSocketConfig`, JWT en
  CONNECT, patrón de SUBSCRIBE autorizado, `FileStorageService` para adjuntos) sin tocar el
  código estable del chat familiar existente.
- iOS queda fuera de este sprint (mismo motivo que sprints anteriores: sin macOS disponible
  para compilar/ejecutar targets reales).

## Decisiones tomadas en brainstorming (2026-07-19)

1. Alcance: paridad completa con el chat familiar (texto, imágenes, editar/borrar propio,
   rate limit, exportar, badge no-leídos, presencia). No solo texto plano.
2. Alcance social: solo se puede chatear en privado con miembros de una familia a la que el
   usuario pertenece. Sin sistema de contactos global ni chat entre usuarios sin familia
   compartida.
3. Punto de entrada UI: botón "Mensaje" en cada fila de miembro (pantalla Miembros, junto al
   punto de presencia) **más** una pantalla nueva "Conversaciones" (bandeja de entrada con
   todas las conversaciones activas del usuario).
4. Nivel de "leído": solo contador de no-leídos por conversación (mismo patrón 100% cliente
   que ya usa el chat familiar), sin recibos de lectura visibles ("visto") para el otro
   usuario.
5. Arquitectura backend: paquete nuevo `dm/`, paralelo a `chat/` y `presence/` (mismo patrón
   ya validado dos veces en revisión durante el sprint de presencia). No se reutiliza
   `ChatMessageEntity` con una columna `recipient_user_id` nullable — mezclaría dos scopes
   distintos (hilo-por-familia vs. hilo-por-par-de-usuarios) en una tabla/controlador con
   lógica condicional.
6. Seguridad del topic de inbox: el ping de `/topic/users/{userId}/inbox` **no lleva texto
   del mensaje**, solo metadata mínima (`conversationId`, remitente, timestamp). El contenido
   real solo viaja por el topic de la conversación específica (ya protegido por check
   userA/userB) o por REST bajo demanda. Minimiza superficie de fuga si el check de
   autorización "solo mi propio userId" (tipo de check sin precedente en el proyecto, hasta
   ahora todo era membership-de-familia) tuviera un bug.

Enfoques descartados: reutilizar `ChatMessageEntity`/`ChatAttachmentEntity` directamente
(acopla dos scopes distintos, su FK apunta a `ChatMessageEntity` específicamente); recibos de
lectura visibles (fuera de alcance, más superficie de estado por mensaje/usuario y tráfico
WebSocket sin beneficio pedido); búsqueda de usuarios fuera de la familia activa (nuevo
concepto social no existente hoy); preview de texto en el ping de inbox (duplica contenido
privado en un canal con autorización nueva y sin precedente).

## Arquitectura

Paquete nuevo `org.gipsybuho.recetasfamiliares.dm` (paralelo a `chat/` y `presence/`), sin
modificar el código estable de `chat/` existente.

### Backend

**Entidades:**

1. **`PrivateConversationEntity`**: `id`, `familyId` (FK), `userAId`/`userBId` (par
   normalizado, `userAId < userBId` lexicográficamente, para que (A,B) y (B,A) siempre mapeen
   a la misma fila), `createdAt`, `updatedAt`, `syncVersion`, `deleted`. Restricción única
   sobre (`familyId`, `userAId`, `userBId`) — una conversación por par de usuarios y familia
   compartida (si comparten varias familias, una conversación por familia, coherente con
   familia = límite de tenant).
2. **`PrivateMessageEntity`**: mismo shape que `ChatMessageEntity` pero FK a `conversation` en
   vez de `family`: `id`, `conversation` (FK), `author` (FK a `UserEntity`), `body`,
   `createdAt`, `updatedAt`, `syncVersion`, `deleted`, `deletedAt`, lista de
   `PrivateMessageAttachmentEntity`.
3. **`PrivateMessageAttachmentEntity`**: mismo shape que `ChatAttachmentEntity` pero FK a
   `PrivateMessageEntity`. Se duplica en vez de generalizar `ChatAttachmentEntity` a un padre
   polimórfico — evita tocar código estable del chat familiar ya revisado, y el duplicado es
   pequeño (mismo criterio ya aceptado en el sprint de presencia: "duplicated boilerplate
   acceptable for N call sites").

**Endpoints REST** (`/api/v1/families/{familyId}/conversations`):

- `POST /with/{otherUserId}`: idempotente, devuelve la conversación existente o la crea.
  Requiere que `otherUserId` sea miembro activo de `familyId` (mismo check de membership que
  el resto del proyecto). Se llama al pulsar "Mensaje" en un miembro.
- `GET`: bandeja de entrada del usuario autenticado en esa familia — lista de
  `{conversationId, otherUser (id/displayName/avatarUrl), lastMessagePreview, lastMessageAt}`,
  ordenada por actividad reciente. Solo incluye conversaciones con al menos un mensaje.
- `GET /{conversationId}/messages`: historial paginado por cursor (mismo patrón que
  `ChatController.messages`).
- `POST /{conversationId}/messages`: enviar texto.
- `POST /{conversationId}/messages/images`: enviar imagen (multipart, mismo patrón que el
  chat familiar, reutiliza `FileStorageService`).
- `PUT /{conversationId}/messages/{messageId}`: editar mensaje propio (mismo verbo que
  `ChatController.editMessage`, no `PATCH`).
- `DELETE /{conversationId}/messages/{messageId}`: soft-delete propio.
- `GET /{conversationId}/export`: exportar historial.

Todos los endpoints de mensajes verifican que el usuario autenticado sea `userA` o `userB` de
`conversationId` antes de cualquier operación (404 si no, para no revelar existencia de la
conversación a terceros).

**STOMP — dos topics:**

- `/topic/conversations/{conversationId}`: mensajes nuevos/editados/borrados de esa
  conversación específica, para quien la tiene abierta. Autorización en SUBSCRIBE: el usuario
  debe ser `userA` o `userB` de esa `conversationId` (nuevo interceptor o extensión del
  existente, análogo a `ChatStompAuthChannelInterceptor` pero contra
  `PrivateConversationRepository` en vez de `FamilyMemberRepository`).
- `/topic/users/{userId}/inbox`: ping ligero (`conversationId`, remitente, timestamp — **sin
  cuerpo del mensaje**, ver decisión 6) cuando llega actividad nueva en cualquier conversación
  del usuario. Alimenta el badge global y el refresco de la bandeja sin necesidad de
  suscribirse a cada conversación individualmente. Autorización: **solo el propio `userId`**
  puede suscribirse a su topic de inbox (`accessor.getUser()` debe coincidir exactamente con
  el `userId` del destino) — clase de check nueva en el proyecto, requiere test explícito de
  que un usuario no puede suscribirse al inbox de otro.

**Rate limiting:** mismo patrón que `ChatSendRateLimiter`, aplicado por (`conversationId`,
`userId`) en vez de (`familyId`, `userId`).

### Desktop

*(estructura de navegación detallada en el addendum 2026-07-22 más abajo)*

- `ConversationsView.java` (nueva, extiende `ScrollPane`): bandeja de entrada. Contiene un
  `SplitPane` — lista de conversaciones a la izquierda, `PrivateChatView` embebido a la
  derecha (mismo patrón que `RecipeListView` + `RecipeDetailView`).
- `PrivateChatView.java` (nueva, sub-panel embebido, no vista de navegación propia): mismo
  patrón que `ChatView.java` — historial, enviar texto/imagen, editar/borrar propio, exportar,
  borrar-para-mí.
- `FamilyMembersView.java`: nuevo botón "Mensaje" en cada fila de miembro (misma zona que el
  punto de presencia) → `POST /with/{userId}` → navega a `ConversationsView` con esa
  conversación ya seleccionada en el panel derecho.
- `ChatSocket.java`: ya soporta múltiples SUBSCRIBE por conexión (chat + presence). Se
  extiende para suscribirse al topic de inbox propio al iniciar sesión (badge global en la
  sidebar, mismo mecanismo que `MainWindow.updateChatBadge()`), y al topic de una conversación
  específica solo mientras esa conversación está seleccionada en `ConversationsView`.

### Android

- `ConversationsScreen.kt` (nueva, bandeja de entrada).
- `ProfileScreen.kt`: nuevo botón "Mensaje" en cada fila de miembro (misma zona que el punto
  de presencia) → `POST /with/{userId}` → abre `PrivateChatScreen.kt`.
- `PrivateChatScreen.kt` (nueva): mismo patrón que `ChatScreen.kt`.
- `ChatSocket.kt`: mismo tratamiento que Desktop — suscripción al inbox propio al iniciar
  sesión, suscripción a la conversación específica mientras esa pantalla está abierta.
- Badge global: mismo mecanismo que `RecetasViewModel._chatUnread` (`RecetasViewModel.kt:897-940`),
  ahora indexado por `conversationId` en vez de un único contador global.

## Seguridad

- Membership de familia obligatoria para iniciar conversación (`POST /with/{otherUserId}`
  falla si `otherUserId` no es miembro activo de `familyId`).
- Toda operación sobre mensajes de una conversación (REST y STOMP) verifica que el usuario
  autenticado sea `userA` o `userB` de esa `conversationId`. 404 (no 403) para no revelar
  existencia de conversaciones ajenas.
- Topic de inbox `/topic/users/{userId}/inbox`: autorización nueva "solo mi propio userId",
  sin precedente en el proyecto (todo lo anterior era membership-de-familia) — requiere
  atención especial en implementación y test explícito de aislamiento.
- Ping de inbox sin contenido de mensaje (decisión 6): minimiza superficie de fuga ante un
  eventual bug del check anterior.
- Sin datos sensibles nuevos más allá del propio contenido del chat privado (mismo nivel de
  sensibilidad que el chat familiar ya existente, ahora con expectativa de privacidad más
  alta al ser 1:1 — ningún otro miembro de la familia, ni siquiera el propietario, debe poder
  leer una conversación privada ajena).
- VibeSec sobre el diff completo antes del cierre (autorización WS + REST nuevos, datos
  personales sensibles).

## Testing y validación

- Backend (JUnit, patrón de tests existente en `chat/`):
  - `PrivateConversationRepository`/servicio: creación idempotente de conversación (llamar
    `POST /with/{userId}` dos veces devuelve el mismo `conversationId`), normalización del
    par de usuarios (A,B) y (B,A) mapean a la misma fila.
  - Autorización REST: 404 para un tercer usuario intentando leer/enviar en una conversación
    ajena; 404 si `otherUserId` no es miembro de la familia.
  - Autorización SUBSCRIBE `/conversations/{id}`: rechazado para quien no es userA/userB.
  - Autorización SUBSCRIBE `/users/{userId}/inbox`: rechazado si el `userId` del destino no
    coincide con el usuario autenticado de la sesión STOMP (caso nuevo, test explícito
    obligatorio).
  - Envío, edición, borrado propio, rate limit, export: mismos casos que
    `ChatControllerTest`, adaptados a conversación.
- Desktop: `mvn test` suite existente + tests nuevos análogos a los de chat familiar +
  compilación.
- Android: `gradlew testDebugUnitTest` + tests análogos + `assembleDebug`.
- Prueba manual: dos sesiones (dos cuentas) en la misma familia, verificar mensaje en tiempo
  real, badge se actualiza con la pantalla de esa conversación cerrada, un tercer usuario no
  ve ni puede acceder a la conversación.

## Addendum 2026-07-22: navegación Desktop confirmada

Brainstorming visual (companion en navegador) con 3 opciones de navegación para el cliente
Desktop, comparadas como mockups:

- **A. Item propio en el sidebar** ("Chat privado" junto a "Chat familiar"), que abre una
  bandeja de conversaciones (avatar/nombre/preview del último mensaje) en un `SplitPane`, con
  el panel de mensajes de la conversación seleccionada a la derecha — mismo patrón ya
  establecido por `RecipeListView` + `RecipeDetailView`.
- B. Pestañas "Familiar / Privados" dentro de un único item "Chat" en el sidebar.
- C. Sin item propio en el sidebar: solo se accede vía el botón "Mensaje" en Miembros, el chat
  abre como diálogo flotante, sin bandeja central de conversaciones.

**Decisión del usuario: opción A.** Confirma y concreta el punto 3 de "Decisiones tomadas en
brainstorming (2026-07-19)": la "pantalla nueva Conversaciones" se implementa como
`ConversationsView` (extiende `ScrollPane`, mismo patrón que las demás vistas de página del
sidebar) conteniendo un `SplitPane` — lista de conversaciones a la izquierda,
`PrivateChatView` embebido a la derecha — no como dos pantallas de navegación separadas.

El botón "Mensaje" en `FamilyMembersView` (ya spec'd en el punto 3 original) sigue existiendo
como atajo: crea/recupera la conversación (`POST /with/{otherUserId}`) y navega directamente a
`ConversationsView` con esa conversación ya seleccionada en el panel derecho.

## Fuera de alcance

- iOS (sprint propio cuando haya macOS disponible).
- Recibos de lectura visibles ("visto") — solo contador de no-leídos.
- Búsqueda de usuarios fuera de la familia activa / sistema de contactos global.
- Borrar o archivar la conversación completa (solo mensajes individuales propios, como el
  chat familiar).
- Notificaciones push nativas fuera de la app (el chat familiar tampoco las tiene, solo badge
  in-app).
- Grupos privados de más de 2 personas (eso ya es el chat familiar existente).
- Preview de texto en el ping del topic de inbox (decisión 6, solo metadata).
