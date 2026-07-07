# Especificación: Chat Familiar por Fases

Estado: **fase 1 implementada (backend + Android) en rama `feat/chat-fase-1` (2026-07-07)**. Fases 2-4 siguen sin implementar.
Sprint de origen: 43 (2026-07-05). Fase 1 ejecutada en el sprint de chat (2026-07-07); ver §10.

Referencias: `CONTINUAR.md` §6 y §7 (funcionalidad futura documentada), `CLAUDE.md` (seguridad, sincronización, contratos API).

---

## 1. Objetivo de producto

Un espacio de conversación cálido y privado por familia: coordinar la cocina ("compra pan", "la abuela hace el cocido el domingo"), compartir fotos de platos y conservar memoria emocional. No es un clon de WhatsApp: es un canal único por familia, sin grupos, sin contactos externos.

Principios:
- Un solo hilo por familia (fase 1). Nada de canales múltiples hasta demostrar necesidad (YAGNI).
- Privacidad familiar: ningún dato sale del tenant familiar.
- Debe degradar bien: sin WebSocket, el chat funciona por REST + polling.

---

## 2. Fases

### Fase 1 — Texto y emojis en tiempo real (backend + Android)
- Mensajes de texto (máx. 2.000 caracteres, emojis Unicode incluidos).
- Historial paginado por cursor (`before=<messageId>`, `limit<=50`).
- Entrega en tiempo real vía WebSocket/STOMP; fallback por polling configurable (15-60 s).
- Indicador básico de mensajes nuevos (badge). Sin read-receipts por usuario en fase 1.
- Cliente: Android primero. Envío offline **no permitido** en fase 1 (botón deshabilitado sin red; simplifica el modelo, sin cola local).

### Fase 2 — Desktop + ediciones
- Cliente Desktop JavaFX con el mismo contrato.
- Editar/borrar mensajes propios (soft delete, tombstone visible: "mensaje eliminado").
- Ventana de edición: 15 minutos.

### Fase 3 — Imágenes
- Adjuntos de imagen reutilizando la infraestructura de uploads (allowlist JPEG/PNG/WebP, magic bytes, 8 MB, ownership familiar por controller — misma política que `/uploads/**`).
- Miniaturas generadas en backend (no en request thread).
- Límite: 5 imágenes por mensaje.

### Fase 4 — Vídeos + push notifications
- Vídeo MP4/H.264, máx. 60 s / 50 MB, con validación de tipo real.
- Push notifications (FCM Android; APNs iOS cuando exista el proyecto Xcode) con contenido mínimo: "Nuevo mensaje de <nombre>" — nunca el texto completo en el payload.
- Limpieza programada de adjuntos huérfanos.
- Producción seria: análisis antivirus o servicio equivalente para adjuntos.

### iOS
- Después de estabilizar refresh 401, push sync y paridad básica (COD-1/COD-2/UX-3). No abrir antes.

---

## 3. Contrato API (borrador v1)

Módulo independiente de notas. Base: `/api/v1/families/{familyId}/chat`.

```
GET    /api/v1/families/{familyId}/chat/messages?before=<id>&limit=50   → historial paginado (desc)
POST   /api/v1/families/{familyId}/chat/messages                        → { body }               (fase 1)
PUT    /api/v1/families/{familyId}/chat/messages/{id}                   → { body }               (fase 2, autor, <15 min)
DELETE /api/v1/families/{familyId}/chat/messages/{id}                   → soft delete            (fase 2, autor o ADMIN/OWNER)
WS     /ws  (STOMP)  topic: /topic/families/{familyId}/chat             → entrega en tiempo real (fase 1)
```

DTO `ChatMessageResponse`:

```json
{
  "id": "uuid",
  "familyId": "uuid",
  "authorUserId": "uuid",
  "authorDisplayName": "string",
  "body": "string|null (null si deleted)",
  "attachments": [ { "id", "url", "thumbnailUrl", "contentType", "sizeBytes" } ],
  "createdAt": "ISO-8601 UTC",
  "updatedAt": "ISO-8601 UTC",
  "syncVersion": 1,
  "deleted": false
}
```

Reglas de contrato:
- camelCase, fechas UTC ISO-8601, errores consistentes con el resto del API.
- Entidad sincronizable estándar: `id`, `createdAt`, `updatedAt`, `syncVersion`, `deleted`.
- El chat NO entra en `sync/pull` (volumen y semántica distintos); tiene su propio cursor de historial.
- Idempotencia de envío: el cliente genera el `id` (UUID v4) y el POST lo acepta; reintento con el mismo id no duplica.

---

## 4. Modelo de datos (borrador)

Tabla `chat_messages` (migración Flyway nueva, sin tocar tablas existentes):
- `id` CHAR(36) PK
- `family_id` FK → families (indexado)
- `author_user_id` FK → users
- `body` TEXT NULL (null cuando deleted)
- `created_at`, `updated_at` DATETIME(6)
- `sync_version` BIGINT
- `deleted` TINYINT(1)
- Índice compuesto `(family_id, created_at DESC, id)` para paginación por cursor.

Tabla `chat_attachments` (fase 3): `id`, `message_id` FK, `url`, `thumbnail_url`, `content_type`, `size_bytes`, `deleted`.

Sin binarios en MySQL: storage de archivos con la misma política que fotos de recetas.

---

## 5. Seguridad (obligatoria en cualquier fase)

- Ownership familiar en **cada** operación REST y en el handshake WebSocket (validar membership antes de suscribir al topic; re-validar si expulsan al usuario).
- JWT en WebSocket: token en el header del CONNECT STOMP, no en la URL (las URLs se loggean).
- Validación de entrada: longitud máxima, control de caracteres, sanitizado al renderizar (los clientes tratan el body como texto plano, nunca HTML/markdown ejecutable).
- Rate limit de envío (p.ej. 10 mensajes/10 s por usuario) para evitar spam accidental o abuso.
- Adjuntos: allowlist de tipo real (magic bytes), tamaño máximo, nombres UUID, sin path traversal, servidos con ownership (mismo mecanismo que `UploadController`).
- Logs sin contenido de mensajes (minimización de datos); solo ids y metadatos técnicos.
- Borrado de cuenta / salida de familia: los mensajes del usuario permanecen atribuidos a "miembro que se fue" (displayName congelado) — decidir en diseño detallado si se anonimiza.

---

## 6. Consideraciones UX antes de diseñar cada fase

- Estado vacío fase 1: primer mensaje cálido y útil, sin explicar tecnología; debe invitar a coordinar cocina familiar y dejar claro que el hilo es privado.
- Envío sin red: botón deshabilitado con feedback breve y recuperable; no cola offline hasta que se diseñe explícitamente.
- Indicador de escritura: fuera de fase 1 salvo que se cierre privacidad y expiración corta; no persistir este estado.
- Mensajes de sistema: entrada/salida de miembros, mensaje eliminado y adjuntos no disponibles deben tener microcopy sobrio, sin exponer emails ni datos internos.
- Accesibilidad: TalkBack/VoiceOver con autor, hora y estado del mensaje; orden de foco estable; acciones de editar/borrar disponibles sin gestos ocultos.
- Notificaciones: agrupar por familia, no incluir texto completo en payload, respetar silencio/no molestar y permitir desactivar avisos del chat.
- Tono visual: conversación cálida y familiar, no corporativa; evitar saturación de badges, contadores o colores de alerta salvo errores reales.

---

## 7. Decisiones (resueltas por el usuario 2026-07-07)

1. Retención: **ilimitada, sin purga automática**. En su lugar, cada usuario puede **borrar/limpiar su propia vista** (marca `cleared_before`, no afecta a otros miembros) y **exportar su copia**.
2. Read-receipts: **no** en fase 1 (solo indicador de mensajes nuevos). Más privado y simple.
3. Reacciones emoji a mensajes: **diferidas a fase 2**. Los emojis Unicode en el cuerpo del mensaje ya funcionan de forma nativa.
4. Broker: **STOMP simple embebido** (`enableSimpleBroker`), suficiente para 1 familia/servidor doméstico.
5. Tests: contrato REST completo + autorización del WebSocket (interceptor STOMP). Implementado: 7 tests REST + 7 tests de interceptor.

---

## 8. Criterios de cierre de la fase 1 (cuando se implemente)

- Tests backend: historial paginado, envío, ownership entre familias, rate limit, WS no autorizado rechazado.
- VibeSec + security-review de la sesión de implementación.
- Android: envío/recepción en vivo con fallback polling verificado, accesibilidad (TalkBack) y estados vacíos cálidos.
- Documentación de contrato actualizada en `CONTINUAR.md` §6.

---

## 9. Análisis de viabilidad (2026-07-06, revisión solo lectura del código)

Revisado: `pom.xml` backend, `FileStorageService`, `UploadController`, `AuthRateLimitFilter`, networking Android (OkHttp/Retrofit) y Desktop (`java.net.http`).

### Activos ya existentes que el chat reutiliza
- Backend: JWT, ownership por familia, Flyway, paginación por cursor y patrón de rate limit (`AuthRateLimitFilter`) operativos.
- Imágenes: `FileStorageService` valida allowlist + magic bytes + 8 MB + nombres UUID; `UploadController` sirve con ownership. Fase 3 es mayormente reutilización.
- Android: OkHttp incluye cliente WebSocket nativo; interceptor auth + refresh listos. Desktop: `java.net.http` trae WebSocket (fase 2).

### Huecos detectados, por dureza
1. **WebSocket (fase 1)**: no existe nada. Falta `spring-boot-starter-websocket`, config STOMP, JWT en CONNECT y validación de membership al suscribir al topic. Trabajo nuevo real pero acotado.
2. **Vídeo (fase 4) — el hueco serio**: `FileStorageService` lee el archivo completo en memoria (inviable a 50 MB) y no hay serving con `Range` para reproducción. Validar MP4/H.264 real o extraer thumbnail de frame exige ffmpeg (dependencia pesada, choca con YAGNI). Sin antivirus. Resoluble pero es un mini-proyecto.
3. **Thumbnails de imagen en backend (fase 3)**: no existen (hoy los genera el cliente). `ImageIO` cubre JPEG/PNG barato; WebP no está soportado nativo — decidir si el thumbnail WebP se degrada a JPEG o se omite.
4. **Push notifications (fase 4)**: FCM sin configurar (requiere proyecto Firebase); APNs imposible sin macOS. Sin push, el tiempo real solo funciona con la app abierta.
5. **iOS**: bloqueado hasta COD-1/COD-2, como ya indica esta spec.

### Conclusión y orden recomendado
- Viable **por fases**, no de una tacada. Fase 1 lista para arrancar en cuanto se resuelvan las decisiones de §7 (bloqueo formal, no técnico).
- Orden: decisiones §7 → fase 1 (backend + Android, 1-2 sprints, riesgo bajo) → fase 2 Desktop → fase 3 imágenes (barata) → reevaluar vídeo con límites más humildes (p. ej. 30 s / 20 MB) y streaming a disco antes que ffmpeg.
- Push FCM: intercalar entre fase 1 y 3 si importa el tiempo real con app cerrada.

---

## 10. Estado de implementación — Fase 1 (2026-07-07)

Rama: `feat/chat-fase-1`. Backend + Android. iOS y Desktop pendientes (fase 2 Desktop).

### Backend (implementado y validado)
- Módulo `chat/` independiente de notas. Migración `V14` (`chat_messages`, `chat_message_clears`).
- REST bajo `/api/v1/families/{familyId}/chat`:
  - `GET /messages?before=<id>&limit=<=50` → `{ items[], hasMore, nextBefore }` (desc por cursor `(createdAt, id)`), filtrado por la marca de limpieza del usuario.
  - `POST /messages` `{ id?, body }` (id de cliente para idempotencia; máx 2000 chars) → `ChatMessageResponse` y broadcast WS.
  - `POST /clear` → 204 (marca `cleared_before = now` para el usuario; oculta su vista sin borrar mensajes compartidos).
  - `GET /export` → `{ familyId, exportedAt, totalMessages, messages[] }` (vista del usuario, ascendente).
- WebSocket: endpoint `/ws` (STOMP, broker simple), topic `/topic/families/{familyId}/chat` (solo entrega). JWT en el CONNECT (`Authorization`), ownership de familia en el SUBSCRIBE, re-validado en cada suscripción. Rate limit de envío por usuario (10/10s por defecto).
- Modelo de borrado/export **por usuario** (decisión §7.1): difiere del borrado global; preserva memoria de los demás.
- Tests: `ChatControllerTest` (7) + `ChatStompAuthChannelInterceptorTest` (7). Suite backend: 107 tests, 0 fallos. `security-review` en sesión: 0 hallazgos de alta confianza.

### Android (implementado, compila; sin prueba manual en dispositivo aún)
- `ChatScreen` accesible desde la TopAppBar (overlay). Burbujas propias/ajenas, estados vacíos cálidos, accesibilidad por `semantics`, hora local.
- `ChatSocket`: cliente STOMP mínimo sobre el WebSocket nativo de OkHttp (sin dependencias nuevas), JWT en el CONNECT, fallback a **polling** (15 s) cuando el tiempo real no está activo.
- `ChatRepository` + endpoints REST + DTOs; estado en `RecetasViewModel` (merge sin duplicados por id, autoscroll, cierre de conexión en `onCleared`).
- Envío offline **no permitido** en fase 1 (sin cola local); degrada a polling.
- `assembleDebug` + `testDebugUnitTest` sin regresión. VibeSec manual: 0 hallazgos.

### Riesgos residuales fase 1
- Sin prueba manual end-to-end multi-dispositivo (requiere backend arrancado + emulador/dispositivo); tiempo real y fallback validados por compilación y tests de contrato, no en runtime en esta sesión.
- STOMP sin heartbeats: una caída silenciosa de conexión se detecta al fallar el socket; el polling cubre la entrega mientras tanto.
- iOS bloqueado (COD-1/COD-2, sin macOS). Desktop es la siguiente implantación (fase 2).
- Reacciones, edición/borrado por mensaje, fotos y vídeo+push: fases 2-4.
