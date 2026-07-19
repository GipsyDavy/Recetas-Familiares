# Spec: Presencia online de miembros (Backend + Desktop + Android)

Fecha: 2026-07-19
Estado: aprobado en diseño (conversación), pendiente de revisión escrita del usuario.

## Contexto

- Punto (20) de `paraImplementar.txt`: "icono indicando miembros en línea o activos, así
  como aviso de miembros que se han conectado y si ha habido actualizaciones de recetas,
  notas o stock". Mezcla dos funcionalidades distintas; el usuario decidió en brainstorming
  (2026-07-19) acotar este sprint solo a la primera mitad: **presencia online**. Los avisos
  de actualizaciones de recetas/notas/stock quedan fuera, como sprint propio futuro.
- Ya existe infraestructura WebSocket/STOMP para el chat familiar (fase 1):
  - Backend: `backend/src/main/java/org/gipsybuho/recetasfamiliares/chat/WebSocketConfig.java`
    (endpoint `/ws`, broker simple `/topic`), `ChatStompAuthChannelInterceptor.java`
    (autentica JWT en CONNECT, autoriza membership de familia en SUBSCRIBE al destino
    `/topic/families/{familyId}/chat`, rechaza SEND directo), `ChatRealtimePublisher.java`
    (difunde mensajes nuevos vía `SimpMessagingTemplate`).
  - Desktop: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ChatSocket.java`
    (cliente STOMP mínimo sobre OkHttp WebSocket, con reconexión exponencial) +
    `data/repository/ChatRepository.java` (crea el socket ligado al `familyId` activo).
  - Android: mismo patrón, `data/remote/ChatSocket.kt` + `data/repository/ChatRepository.kt`.
  - La conexión de chat se mantiene viva tras login (usada también para el badge de mensajes
    no leídos en `MainWindow.java` / `ProfileScreen.kt`), no solo mientras la pantalla de chat
    está abierta.
- No existe hoy: tracking de desconexión de sesión STOMP (`SessionDisconnectEvent` sin
  listener), campo `lastSeenAt`/`online` en `FamilyMemberEntity`, ni endpoint de presencia.
- iOS queda fuera de este sprint (decisión del usuario): esta máquina no puede compilar ni
  ejecutar targets iOS reales, solo `compileKotlinMetadata`, y no hay macOS disponible.

## Decisiones tomadas en brainstorming (2026-07-19)

1. Alcance: solo presencia online. Avisos de cambios de recetas/notas/stock, fuera.
2. Plataformas: Backend + Desktop + Android. iOS fuera (sprint propio cuando haya macOS).
3. Definición de "en línea": conectado ahora mismo (WebSocket activo), tiempo real. Sin
   ventana de minutos, sin `lastSeenAt` persistido.
4. Sin aviso (toast/snackbar) al conectar ni al desconectar. Solo el icono/indicador.
5. Ubicación del icono: solo en la pantalla Miembros (Desktop `FamilyMembersView.java`,
   Android `ProfileScreen.kt`). Sin contador en sidebar ni en inicio.

Enfoques descartados (contradicen las decisiones 3 y 4, o violan YAGNI sin necesidad
demostrada): heartbeat periódico con `lastSeenAt` en BD (implica "activo recientemente",
no "conectado ahora"); snapshot periódico a BD para resiliencia entre reinicios del backend
(el autocorrectivo por reconexión de los clientes ya resuelve esto sin persistencia).

## Arquitectura

Presencia se implementa como un segundo topic STOMP por familia, reutilizando la misma
conexión que el cliente ya mantiene abierta para el chat — sin conexión nueva, sin
heartbeat, sin persistencia. Estado en memoria en el backend: si el proceso reinicia, el
registro se vacía y cada cliente conectado se re-suscribe solo (misma lógica de reconexión
con backoff exponencial que ya tiene `ChatSocket`), autocorrigiendo el estado en segundos.

## Backend

Paquete nuevo `org.gipsybuho.recetasfamiliares.presence` (paralelo a `chat/`):

1. **`PresenceRegistry`** (`@Component`, en memoria, thread-safe):
   - `Map<String familyId, Map<String userId, AtomicInteger>>`: contador de conexiones
     activas por (familia, usuario). Contador y no booleano para soportar el mismo usuario
     conectado desde dos dispositivos sin que uno "apague" la presencia del otro.
   - Índice inverso `Map<String sessionId, List<PresenceKey(familyId, userId)>>` para poder
     limpiar todas las entradas de una sesión de golpe al desconectar.
   - Métodos: `subscribe(sessionId, familyId, userId)` → incrementa, devuelve si pasó de 0
     a 1 (cambio real de estado); `unsubscribeSession(sessionId)` → limpia y devuelve las
     familias afectadas cuyo contador llegó a 0; `onlineUserIds(familyId)` → snapshot actual.

2. **`ChatStompAuthChannelInterceptor`** (modificación quirúrgica): generalizar el chequeo
   de destino SUBSCRIBE para aceptar también `/topic/families/{familyId}/presence` con la
   misma autorización de membership ya usada para `/chat`
   (`FamilyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse`). Al autorizar una
   suscripción de presencia, llama a `PresenceRegistry.subscribe(...)` y, si hubo cambio de
   estado, dispara la difusión (ver punto 4).

3. **Listener nuevo** `PresenceDisconnectListener` (`@EventListener` de
   `org.springframework.web.socket.messaging.SessionDisconnectEvent`, evento que Spring ya
   emite pero que hoy no tiene listener en el proyecto): llama a
   `PresenceRegistry.unsubscribeSession(sessionId)` y difunde el snapshot actualizado de
   cada familia afectada.

4. **`PresencePublisher`** (paralelo a `ChatRealtimePublisher`): `publish(familyId)` lee
   `PresenceRegistry.onlineUserIds(familyId)` y difunde el snapshot completo (no deltas,
   evita bugs de eventos perdidos si un cliente se suscribe tarde) a
   `/topic/families/{familyId}/presence` vía `SimpMessagingTemplate`.

5. **`PresenceController`**: `GET /api/v1/families/{familyId}/presence` (mismo control de
   ownership que el resto de endpoints de familia) → `PresenceResponse(List<String>
   onlineUserIds)`. Snapshot inicial para cuando un cliente abre Miembros después de haberse
   perdido difusiones anteriores.

## Desktop

- `ChatSocket.java`: tras `CONNECTED`, además del `SUBSCRIBE` a `/chat` ya existente, enviar
  un segundo `SUBSCRIBE` a `/topic/families/{familyId}/presence`. Nuevo callback
  `onPresenceUpdate(Set<String> onlineUserIds)` para frames `MESSAGE` cuyo `destination`
  (cabecera del frame `MESSAGE`, hoy no parseada — se añade) apunte al topic de presencia en
  vez de al de chat.
- `ChatRepository.java`: expone el snapshot de presencia (último recibido) a quien lo
  necesite, mismo patrón que ya usa para mensajes/no-leídos.
- `FamilyMembersView.java`: al abrir, `GET .../presence` (nuevo método en `FamilyRepository`
  o `ChatRepository`, según dónde viva mejor) para el estado inicial; nueva columna o icono
  compuesto con el nombre (punto verde si `userId` está en el snapshot online, gris si no),
  actualizado en vivo mientras la vista y la conexión sigan abiertas.

## Android

Mismo patrón, ficheros equivalentes:

- `data/remote/ChatSocket.kt`: segundo `SUBSCRIBE` a `/topic/families/{familyId}/presence`
  tras `CONNECTED`; nuevo callback `onPresenceUpdate: (Set<String>) -> Unit`.
- `data/repository/ChatRepository.kt`: expone el snapshot de presencia igual que Desktop.
- `ui/ProfileScreen.kt` (sección de miembros): `GET .../presence` al entrar, punto
  verde/gris (`Badge` o `Box` con color, Material You) junto a cada miembro, actualizado en
  vivo.

## Seguridad

- Mismo control de ownership que el chat: nadie ve presencia de una familia a la que no
  pertenece (`FamilyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse`, tanto en
  el SUBSCRIBE STOMP como en el endpoint REST).
- Sin datos sensibles nuevos: el snapshot es una lista de `userId` que ya son visibles a
  cualquier miembro de la familia vía la propia pantalla de Miembros.
- Sin secretos, sin nueva superficie de autenticación (reutiliza el JWT del CONNECT STOMP
  existente).
- VibeSec sobre el diff antes del cierre (autorización WS + REST nuevos).

## Testing y validación

- Backend (JUnit, patrón de tests existente en `chat/`):
  - `PresenceRegistry`: contador multi-dispositivo (2 sesiones mismo usuario → sigue
    online tras cerrar una), limpieza completa por `sessionId`, snapshot correcto tras
    altas/bajas mezcladas de varias familias.
  - Autorización SUBSCRIBE a `/presence` de un no-miembro → rechazado (mismo test que ya
    debe existir para `/chat`, extendido).
  - `PresenceController`: 200 con snapshot para miembro, 403/404 para no-miembro
    (según convención ya usada en otros controladores de familia).
  - `PresenceDisconnectListener`: desconexión limpia el registro y dispara difusión.
- Desktop: `mvn test` suite existente + test de parsing del nuevo frame de presencia en
  `ChatSocket` (mismo patrón que el test de parsing de frames de chat, si existe) +
  compilación.
- Android: `gradlew testDebugUnitTest` + test equivalente de parsing +
  `assembleDebug`.
- Prueba manual: dos sesiones (dos cuentas o dos dispositivos) en la misma familia,
  verificar que el punto se enciende/apaga al conectar/desconectar cada una, y que una
  tercera familia no ve nada.

## Fuera de alcance

- iOS (sprint propio cuando haya macOS disponible).
- Avisos de actualizaciones de recetas, notas o stock (mitad restante del punto (20)
  original, sprint propio).
- Toast/snackbar al conectar o desconectar.
- Contador de "N en línea" en sidebar o pantalla de inicio.
- `lastSeenAt` / "última vez visto" para miembros offline.
- Presencia agregada entre familias (siempre está acotada a la familia activa/suscrita).
