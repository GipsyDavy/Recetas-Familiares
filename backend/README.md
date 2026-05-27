# Backend

API principal de Recetas Familiares.

## Stack

- Java 21 LTS
- Spring Boot 3
- Spring Security
- Spring Data JPA
- MySQL
- Flyway
- OpenAPI/Swagger

## Ejecutar en desarrollo

El perfil base no define credenciales por defecto. Para desarrollo local, activar `dev`:

```text
SPRING_PROFILES_ACTIVE=dev
```

Variables disponibles:

```text
DB_URL=jdbc:mysql://localhost:3306/recetas_familiares?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=recetas_app
DB_PASSWORD=...
SERVER_PORT=8080
AUTH_RATE_LIMIT_ENABLED=true
AUTH_RATE_LIMIT_MAX_REQUESTS=20
AUTH_RATE_LIMIT_WINDOW_SECONDS=60
```

Comando:

```bash
mvn spring-boot:run
```

Health endpoint:

```text
GET /api/v1/health
```

Logo del creador:

```text
GET /brand/gipsy-buho-logo.png
```

Autenticacion:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

Familias:

```text
GET /api/v1/families
```

Permisos familiares:

- cualquier miembro activo puede leer recursos de su familia;
- solo `OWNER` y `ADMIN` pueden crear, editar, borrar o hacer `sync/push`;
- `MEMBER` queda como rol de solo lectura;
- `sync/pull` requiere membership activa, no rol editor.

Rate limiting de autenticacion:

- activo por defecto para `POST /api/v1/auth/register`, `/login`, `/refresh` y `/logout`;
- limita por IP de cliente y endpoint;
- responde `429` con codigo `rate_limited` y cabecera `Retry-After`;
- configurable con `AUTH_RATE_LIMIT_ENABLED`, `AUTH_RATE_LIMIT_MAX_REQUESTS` y `AUTH_RATE_LIMIT_WINDOW_SECONDS`.

Hardening HTTP:

- cabeceras defensivas activas: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy`;
- HSTS configurado para despliegues HTTPS;
- CORS deniega por defecto si no se configura `CORS_ALLOWED_ORIGINS`;
- CORS solo responde a origenes explicitamente permitidos.

Recetas:

```text
GET /api/v1/families/{familyId}/recipes
POST /api/v1/families/{familyId}/recipes
GET /api/v1/families/{familyId}/recipes/{recipeId}
PUT /api/v1/families/{familyId}/recipes/{recipeId}
DELETE /api/v1/families/{familyId}/recipes/{recipeId}
```

Ingredientes y pasos:

```text
GET /api/v1/families/{familyId}/recipes/{recipeId}/ingredients
GET /api/v1/families/{familyId}/recipes/{recipeId}/ingredients?includeDeleted=true
PUT /api/v1/families/{familyId}/recipes/{recipeId}/ingredients
GET /api/v1/families/{familyId}/recipes/{recipeId}/steps
GET /api/v1/families/{familyId}/recipes/{recipeId}/steps?includeDeleted=true
PUT /api/v1/families/{familyId}/recipes/{recipeId}/steps
```

Los `PUT` reemplazan la lista completa manteniendo orden por `position`.
Los elementos anteriores se marcan con soft delete para que Android y Desktop puedan reconciliar cambios.

Stock familiar:

```text
GET /api/v1/families/{familyId}/stock-items
POST /api/v1/families/{familyId}/stock-items
GET /api/v1/families/{familyId}/stock-items/{stockItemId}
PUT /api/v1/families/{familyId}/stock-items/{stockItemId}
DELETE /api/v1/families/{familyId}/stock-items/{stockItemId}
```

Menus semanales:

```text
GET /api/v1/families/{familyId}/menu-items?weekStart=2026-06-01
POST /api/v1/families/{familyId}/menu-items
GET /api/v1/families/{familyId}/menu-items/{menuItemId}
PUT /api/v1/families/{familyId}/menu-items/{menuItemId}
DELETE /api/v1/families/{familyId}/menu-items/{menuItemId}
```

Cada entrada de menu representa una comida planificada por fecha y tipo (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`).
Puede referenciar una receta de la misma familia o funcionar como nota de planificacion sin receta asociada.

Listas de compra:

```text
GET /api/v1/families/{familyId}/shopping-lists
POST /api/v1/families/{familyId}/shopping-lists
POST /api/v1/families/{familyId}/shopping-lists/generate-from-menu
GET /api/v1/families/{familyId}/shopping-lists/{shoppingListId}
PUT /api/v1/families/{familyId}/shopping-lists/{shoppingListId}
DELETE /api/v1/families/{familyId}/shopping-lists/{shoppingListId}
GET /api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items
POST /api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items
GET /api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items/{itemId}
PUT /api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items/{itemId}
DELETE /api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items/{itemId}
```

Las listas agrupan compras familiares por nombre y rango opcional de fechas.
Los items incluyen orden, cantidad, unidad, nota y estado `checked`.
`generate-from-menu` crea una lista nueva desde las recetas planificadas en un rango de fechas y agrupa ingredientes por nombre y unidad.

Favoritos:

```text
GET /api/v1/families/{familyId}/favorite-recipes
POST /api/v1/families/{familyId}/favorite-recipes
GET /api/v1/families/{familyId}/favorite-recipes/{favoriteId}
DELETE /api/v1/families/{familyId}/favorite-recipes/{favoriteId}
```

Los favoritos son familiares y referencian recetas de la misma familia.

Notas familiares:

```text
GET /api/v1/families/{familyId}/notes
POST /api/v1/families/{familyId}/notes
GET /api/v1/families/{familyId}/notes/{noteId}
PUT /api/v1/families/{familyId}/notes/{noteId}
DELETE /api/v1/families/{familyId}/notes/{noteId}
```

Las notas pertenecen a una familia y pueden asociarse opcionalmente a una receta de la misma familia.
Incluyen titulo, cuerpo y estado `pinned`.

Fotos de receta:

```text
GET /api/v1/families/{familyId}/recipes/{recipeId}/photos
GET /api/v1/families/{familyId}/recipes/{recipeId}/photos?includeDeleted=true
POST /api/v1/families/{familyId}/recipes/{recipeId}/photos
GET /api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}
PUT /api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}
DELETE /api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}
```

Solo se almacenan metadata y URLs: `url`, `thumbnailUrl`, `caption`, `contentType`, `sizeBytes` y `position`.
No se guardan binarios en MySQL. Se aceptan URLs `http/https` y tipos `image/jpeg`, `image/png`, `image/webp`.

Sincronizacion inicial:

```text
GET /api/v1/families/{familyId}/sync/pull?since=2026-05-27T00:00:00Z
POST /api/v1/families/{familyId}/sync/push
```

Devuelve `serverTime`, `recipes`, `ingredients`, `steps`, `stockItems`, `menuItems`, `shoppingLists`, `shoppingListItems`, `favoriteRecipes`, `familyNotes` y `recipePhotos` modificados despues de `since`.
Incluye registros con `deleted=true` para que los clientes offline puedan aplicar tombstones.
`push` acepta cambios offline de recetas, ingredientes, pasos, stock familiar, menus semanales, listas de compra, favoritos, notas familiares y fotos de receta con `id` estable del cliente.
`push` requiere rol familiar `OWNER` o `ADMIN`; `pull` esta disponible para cualquier miembro activo.
Para clientes nuevos, cada item puede enviar `baseSyncVersion`.
Si `baseSyncVersion` no coincide con la version actual del servidor, el servidor responde `409 conflict`.
Si `baseSyncVersion` no se envia, se mantiene compatibilidad legacy con Last Write Wins.
`updatedAt` y `syncVersion` los asigna siempre el servidor.

Swagger UI:

```text
/swagger-ui.html
```

## Estado actual

Base inicial con:

- arranque Spring Boot;
- endpoint health versionado;
- seguridad stateless con health y OpenAPI publicos;
- configuracion base por variables de entorno;
- perfil `dev` con valores locales no aptos para produccion;
- JPA en modo `validate`;
- Flyway preparado con esquema inicial de usuarios, familias, miembros y refresh tokens;
- ids UUID como `CHAR(36)` para preparar sincronizacion offline;
- columnas `created_at`, `updated_at`, `sync_version`, `deleted` y `deleted_at` en entidades sincronizables iniciales;
- contrato comun inicial de errores y paginacion;
- metadata OpenAPI con esquema Bearer JWT;
- Swagger/OpenAPI desactivado por defecto en perfil `prod`;
- registro de usuario con creacion de familia y rol `OWNER`;
- permisos familiares por rol: `OWNER` y `ADMIN` escriben, `MEMBER` lee;
- login con BCrypt;
- access tokens JWT Bearer;
- refresh tokens opacos almacenados como hash SHA-256;
- rotacion de refresh token en `/refresh`;
- revocacion de refresh token en `/logout`;
- errores JSON para 401, 403, validacion, 404 y errores generales;
- rate limiting configurable para endpoints de autenticacion;
- cabeceras defensivas y politica CORS explicita;
- primer endpoint privado real con filtrado por usuario autenticado;
- CRUD inicial de recetas con listado paginado, detalle, creacion, edicion y soft delete;
- ingredientes y pasos de receta como recursos anidados;
- reemplazo ordenado de ingredientes y pasos con tombstones opcionales mediante `includeDeleted=true`;
- soft delete propagado a ingredientes y pasos al borrar una receta;
- stock familiar con cantidad, unidad, umbral bajo, caducidad y notas;
- menus semanales por fecha y tipo de comida, con receta familiar opcional;
- listas de compra con items comprados/no comprados;
- generacion de listas de compra desde menus semanales y recetas planificadas;
- favoritos familiares de recetas;
- notas familiares con receta opcional;
- fotos de receta como metadata/URLs, nunca binarios en MySQL;
- `pull` incremental inicial por familia para recetas, ingredientes, pasos, stock, menus, listas de compra, favoritos, notas y fotos;
- `push` incremental inicial por familia para recetas, ingredientes, pasos, stock, menus, listas de compra, favoritos, notas y fotos;
- deteccion opcional de conflictos en `push` mediante `baseSyncVersion`;
- validacion de membership familiar para lectura y rol editor para escritura;
- tests iniciales para contexto, health endpoint, flujo de autenticacion, rate limiting de auth, hardening HTTP/CORS, aislamiento de familias, ownership de recetas, permisos por rol, contenidos de receta, stock, menus, listas de compra, favoritos, notas, fotos y sincronizacion pull/push.

## Pendiente

- Revisar Swagger/OpenAPI antes de produccion.
- Crear datos seed/dev si hace falta.
