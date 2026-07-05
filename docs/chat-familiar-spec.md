# Especificación: Chat Familiar por Fases

Estado: **especificación aprobada para diseño, NO implementada**.
Sprint de origen: 43 (2026-07-05). No escribir código de chat hasta que se abra un sprint dedicado.

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

## 6. Decisiones pendientes (resolver antes de implementar fase 1)

1. ¿Retención ilimitada del historial o límite (p.ej. 2 años) con export?
2. ¿Read-receipts por usuario en alguna fase? (privacidad vs utilidad familiar)
3. ¿Reacciones con emoji a mensajes (fase 2-3)? Barato en contrato si se decide pronto.
4. Broker STOMP simple embebido vs broker externo — para 1 familia/servidor doméstico, embebido basta.
5. Estrategia de tests: contrato REST completo + test de autorización del WebSocket.

---

## 7. Criterios de cierre de la fase 1 (cuando se implemente)

- Tests backend: historial paginado, envío, ownership entre familias, rate limit, WS no autorizado rechazado.
- VibeSec + security-review de la sesión de implementación.
- Android: envío/recepción en vivo con fallback polling verificado, accesibilidad (TalkBack) y estados vacíos cálidos.
- Documentación de contrato actualizada en `CONTINUAR.md` §6.
