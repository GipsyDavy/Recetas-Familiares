# CONTINUAR.md - Estado Actual del Proyecto Recetas Familiares

Este documento resume el estado real del proyecto para continuar en una nueva sesion sin perder contexto.

## Raiz correcta del proyecto

Abrir siempre la raiz del monorepo:

```text
C:\Users\GipsyDavy\MAVEN\Recetas Familiares
```

No abrir como proyecto principal:

- `android/`
- `desktop/`
- carpetas antiguas locales

## De que va la aplicacion

Recetas Familiares es una aplicacion premium multiplataforma para familias.

Objetivo:

- guardar y compartir recetas familiares;
- conservar memoria culinaria familiar;
- gestionar ingredientes, pasos y stock;
- planificar menus;
- generar listas de compra;
- funcionar en Android y Desktop;
- soportar sincronizacion offline-first;
- mantener una experiencia calida, moderna, emocional y premium.

Plataformas objetivo:

- Backend Spring Boot + MySQL;
- Android nativo;
- Desktop JavaFX;
- sincronizacion cliente-servidor.

## Reglas obligatorias

Antes de continuar, leer y cumplir:

- `CLAUDE.md`
- `MACRO-PROMPT-RECETAS-FAMILIA.md`
- `Resumen.md`
- este `CONTINUAR.md`

Reglas tecnicas clave:

- API versionada bajo `/api/v1`.
- No exponer entidades JPA directamente.
- Usar DTOs explicitos.
- Validar ownership familiar en todos los endpoints.
- MySQL es la fuente principal.
- Flyway para migraciones.
- No usar `ddl-auto=update`.
- JWT + refresh tokens.
- No hardcodear secretos de produccion.
- Entidades sincronizables con `id`, `createdAt`, `updatedAt`, `syncVersion`, `deleted`.
- Soft delete obligatorio.
- Preparar Android/Desktop para sincronizacion offline.

## Herramientas

Se dejo Maven y Gradle disponibles en PATH de usuario:

```text
C:\Program Files\Apache NetBeans\java\maven\bin
C:\tmp\tools\gradle-9.5.1\bin
```

En esta sesion puede hacer falta recargar PATH:

```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','User') + ';' + [Environment]::GetEnvironmentVariable('Path','Machine')
```

El build actual usa Java instalado en la maquina. Maven compila con release 21, aunque la JVM detectada en tests fue Java 25.

Advertencia conocida:

- Mockito/Byte Buddy muestra warning con Java 25.
- No rompe build ni tests.
- Futuro recomendado: usar JDK 21 LTS o configurar Mockito como javaagent.

## Backend implementado

Se creo backend Maven Spring Boot en:

```text
backend/
```

Stack actual:

- Spring Boot 3.5.14.
- Java configurado como 21.
- Maven.
- Spring Web.
- Spring Security.
- Spring Data JPA.
- Bean Validation.
- Flyway.
- MySQL.
- H2 para tests.
- OpenAPI/Swagger.
- JJWT.

## Configuracion implementada

Archivos:

- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/src/test/resources/application-test.yml`

Configuracion clave:

- perfil base con variables de entorno obligatorias;
- perfil `dev` para desarrollo local;
- perfil `prod` con Swagger/OpenAPI desactivado;
- perfil `test` con H2;
- `ddl-auto: validate`;
- Flyway activado;
- sin secretos de produccion hardcodeados.

## Seguridad implementada

Implementado:

- seguridad stateless;
- JWT Bearer access token;
- refresh tokens opacos;
- refresh tokens almacenados como hash SHA-256;
- rotacion de refresh token;
- logout con revocacion;
- BCrypt para passwords;
- errores JSON para 401 y 403;
- filtros JWT;
- `UserDetailsService`;
- endpoints publicos limitados a auth, health y Swagger/OpenAPI.

## Auth implementado

Endpoints:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

Registro crea:

- usuario;
- familia;
- membership familiar con rol `OWNER`.

## Familias implementado

Endpoint:

```text
GET /api/v1/families
```

Devuelve solo familias del usuario autenticado.

## Recetas implementado

Endpoints:

```text
GET /api/v1/families/{familyId}/recipes
POST /api/v1/families/{familyId}/recipes
GET /api/v1/families/{familyId}/recipes/{recipeId}
PUT /api/v1/families/{familyId}/recipes/{recipeId}
DELETE /api/v1/families/{familyId}/recipes/{recipeId}
```

Incluye:

- listado paginado;
- detalle;
- creacion;
- actualizacion;
- soft delete;
- ownership familiar;
- campos de sincronizacion.

## Ingredientes y pasos implementado

Endpoints:

```text
GET /api/v1/families/{familyId}/recipes/{recipeId}/ingredients
GET /api/v1/families/{familyId}/recipes/{recipeId}/ingredients?includeDeleted=true
PUT /api/v1/families/{familyId}/recipes/{recipeId}/ingredients
GET /api/v1/families/{familyId}/recipes/{recipeId}/steps
GET /api/v1/families/{familyId}/recipes/{recipeId}/steps?includeDeleted=true
PUT /api/v1/families/{familyId}/recipes/{recipeId}/steps
```

Comportamiento:

- los `PUT` reemplazan la lista completa;
- el orden se guarda en `position`;
- los elementos anteriores se marcan con soft delete;
- `includeDeleted=true` permite recuperar tombstones;
- al borrar receta se soft-deletean ingredientes y pasos.

## Stock familiar implementado

Endpoint base:

```text
/api/v1/families/{familyId}/stock-items
```

Endpoints:

```text
GET /api/v1/families/{familyId}/stock-items
POST /api/v1/families/{familyId}/stock-items
GET /api/v1/families/{familyId}/stock-items/{stockItemId}
PUT /api/v1/families/{familyId}/stock-items/{stockItemId}
DELETE /api/v1/families/{familyId}/stock-items/{stockItemId}
```

Campos:

- `name`
- `quantity`
- `unit`
- `lowStockThreshold`
- `expiresAt`
- `note`
- `createdAt`
- `updatedAt`
- `syncVersion`
- `deleted`

Integrado en sincronizacion como `stockItems`.

## Sincronizacion implementada

Endpoints:

```text
GET /api/v1/families/{familyId}/sync/pull?since=2026-05-27T00:00:00Z
POST /api/v1/families/{familyId}/sync/push
```

`pull` devuelve:

- `serverTime`
- `recipes`
- `ingredients`
- `steps`
- `stockItems`

`push` acepta:

- recetas;
- ingredientes;
- pasos;
- stock familiar;
- IDs estables de cliente;
- tombstones con `deleted=true`.

Estrategia inicial:

- Last Write Wins;
- `updatedAt` asignado por servidor;
- `syncVersion` asignado por servidor;
- ownership familiar obligatorio;
- borrados desconocidos se ignoran para no crear basura;
- no se permite mover silenciosamente contenido existente a otra receta.

## Migraciones Flyway

Implementadas:

```text
V1__create_identity_schema.sql
V2__create_recipes_schema.sql
V3__create_recipe_contents_schema.sql
V4__create_stock_schema.sql
```

Tablas principales actuales:

- `users`
- `families`
- `family_members`
- `refresh_tokens`
- `recipes`
- `recipe_ingredients`
- `recipe_steps`
- `stock_items`

## Tests implementados

Hay tests de:

- contexto Spring;
- health endpoint;
- auth register/login/refresh/logout;
- familias;
- recetas;
- ingredientes y pasos;
- aislamiento entre familias;
- stock familiar;
- sync pull;
- sync push;
- tombstones y soft delete.

Ultima verificacion ejecutada:

```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','User') + ';' + [Environment]::GetEnvironmentVariable('Path','Machine'); mvn verify
```

Resultado:

```text
Tests run: 25
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## Estado Git actual

Hay cambios sin commit:

```text
 M backend/README.md
?? backend/pom.xml
?? backend/src/
```

No se ha hecho commit despues de implementar el backend.

Antes de commitear:

```powershell
git status --short --branch
git diff --stat
```

## Que falta

Prioridad backend:

1. Mejorar resolucion de conflictos sync con version cliente/servidor.
2. Implementar menus semanales.
3. Implementar listas de compra.
4. Implementar favoritos.
5. Implementar notas familiares.
6. Implementar fotos como metadata/URLs, nunca binarios en MySQL.
7. Implementar roles/permisos familiares mas finos.
8. Preparar rate limiting y hardening de seguridad.
9. Revisar Swagger/OpenAPI antes de produccion.
10. Crear datos seed/dev si hace falta.

Prioridad Android:

1. Crear proyecto Android real.
2. Retrofit client.
3. Room local database.
4. Repository pattern.
5. WorkManager sync pull/push.
6. Pantallas auth, recetas, detalle y stock.
7. Material You 3.

Prioridad Desktop:

1. Crear proyecto JavaFX real con Maven.
2. HTTP API client.
3. Cache local.
4. MVVM ligero.
5. Pantallas dashboard, recetas, detalle y stock.
6. Evitar bloquear JavaFX Thread.

## Desde donde continuar

Ruta:

```text
C:\Users\GipsyDavy\MAVEN\Recetas Familiares\backend
```

Comando recomendado:

```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','User') + ';' + [Environment]::GetEnvironmentVariable('Path','Machine'); mvn verify
```

Siguiente paso tecnico recomendado:

```text
Implementar menus semanales en backend.
```

Motivo:

- depende de recetas;
- prepara listas de compra;
- encaja con planificacion familiar;
- es una pieza central del MVP.

Alternativa si se quiere cerrar el backend base primero:

```text
Revisar diff, hacer commit y push del estado actual.
```

## Procedimiento al retomar

1. Abrir la raiz:

```text
C:\Users\GipsyDavy\MAVEN\Recetas Familiares
```

2. Leer:

```text
CLAUDE.md
MACRO-PROMPT-RECETAS-FAMILIA.md
Resumen.md
CONTINUAR.md
backend/README.md
```

3. Comprobar estado:

```powershell
git status --short --branch
```

4. Validar backend:

```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','User') + ';' + [Environment]::GetEnvironmentVariable('Path','Machine'); mvn verify
```

5. Continuar con menus semanales o cerrar commit.

## Nota importante

No empezar por Android ni Desktop hasta confirmar que los contratos backend estan suficientemente estables.

El backend ya tiene base real y contratos iniciales para:

- auth;
- familias;
- recetas;
- ingredientes;
- pasos;
- stock;
- sincronizacion pull/push.

La siguiente fase debe mantener compatibilidad con Android/Desktop y no romper los contratos JSON existentes sin motivo fuerte.
