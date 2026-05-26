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
```

Comando:

```bash
mvn spring-boot:run
```

Health endpoint:

```text
GET /api/v1/health
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

Sincronizacion inicial:

```text
GET /api/v1/families/{familyId}/sync/pull?since=2026-05-27T00:00:00Z
POST /api/v1/families/{familyId}/sync/push
```

Devuelve `serverTime`, `recipes`, `ingredients`, `steps` y `stockItems` modificados despues de `since`.
Incluye registros con `deleted=true` para que los clientes offline puedan aplicar tombstones.
`push` acepta cambios offline de recetas, ingredientes, pasos y stock familiar con `id` estable del cliente.
La estrategia inicial de conflicto es Last Write Wins con `updatedAt` y `syncVersion` asignados por el servidor.

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
- login con BCrypt;
- access tokens JWT Bearer;
- refresh tokens opacos almacenados como hash SHA-256;
- rotacion de refresh token en `/refresh`;
- revocacion de refresh token en `/logout`;
- errores JSON para 401, 403, validacion, 404 y errores generales;
- primer endpoint privado real con filtrado por usuario autenticado;
- CRUD inicial de recetas con listado paginado, detalle, creacion, edicion y soft delete;
- ingredientes y pasos de receta como recursos anidados;
- reemplazo ordenado de ingredientes y pasos con tombstones opcionales mediante `includeDeleted=true`;
- soft delete propagado a ingredientes y pasos al borrar una receta;
- stock familiar con cantidad, unidad, umbral bajo, caducidad y notas;
- `pull` incremental inicial por familia para recetas, ingredientes, pasos y stock;
- `push` incremental inicial por familia para recetas, ingredientes, pasos y stock;
- validacion de membership familiar antes de leer o escribir recetas;
- tests iniciales para contexto, health endpoint, flujo de autenticacion, aislamiento de familias, ownership de recetas, contenidos de receta, stock y sincronizacion pull/push.

## Pendiente de sincronizacion

- Afinar resolucion de conflictos con comparacion explicita de version cliente/servidor.
- Ampliar `pull` a futuros modulos sincronizables: menus, favoritos, notas y fotos.
- Ampliar `push` a futuros modulos sincronizables: menus, favoritos, notas y fotos.
- Restringir Swagger/OpenAPI por perfil antes de produccion.
