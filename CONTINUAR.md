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
- seed de desarrollo opcional solo en perfil `dev`, activable con `DEV_SEED_DATA_ENABLED=true`;
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
- OpenAPI documenta `auth` y `health` como publicos, y el resto de `/api/v1/**` con Bearer JWT.
- permisos familiares por rol: `OWNER` y `ADMIN` pueden escribir/borrar/sync push; `MEMBER` puede leer.
- rate limiting configurable en `POST /api/v1/auth/register`, `/login`, `/refresh` y `/logout`;
- respuestas `429` con codigo `rate_limited` y cabecera `Retry-After`.
- cabeceras defensivas HTTP: CSP, nosniff, frame deny, referrer policy y permissions policy;
- HSTS configurado para HTTPS;
- CORS deny-by-default salvo origenes configurados en `CORS_ALLOWED_ORIGINS`.

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

## Menus semanales implementados

Endpoint base:

```text
/api/v1/families/{familyId}/menu-items
```

Endpoints:

```text
GET /api/v1/families/{familyId}/menu-items?weekStart=2026-06-01
POST /api/v1/families/{familyId}/menu-items
GET /api/v1/families/{familyId}/menu-items/{menuItemId}
PUT /api/v1/families/{familyId}/menu-items/{menuItemId}
DELETE /api/v1/families/{familyId}/menu-items/{menuItemId}
```

Campos:

- `recipeId` opcional, siempre validado contra la misma familia;
- `recipeTitle` en respuesta;
- `plannedDate`;
- `mealType`: `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`;
- `note`;
- `createdAt`;
- `updatedAt`;
- `syncVersion`;
- `deleted`.

Integrado en sincronizacion como `menuItems`.

## Listas de compra implementadas

Endpoint base:

```text
/api/v1/families/{familyId}/shopping-lists
```

Endpoints:

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

Campos de lista:

- `name`
- `plannedFrom`
- `plannedTo`
- `note`
- `completed`
- `createdAt`
- `updatedAt`
- `syncVersion`
- `deleted`

Campos de item:

- `shoppingListId`
- `position`
- `name`
- `quantity`
- `unit`
- `checked`
- `note`
- `createdAt`
- `updatedAt`
- `syncVersion`
- `deleted`

Al borrar una lista se aplican tombstones a sus items activos.
Integrado en sincronizacion como `shoppingLists` y `shoppingListItems`.

Generacion automatica:

- `generate-from-menu` recibe `name`, `startDate`, `endDate` y `note`;
- crea una lista nueva para ese rango;
- recorre recetas planificadas en menus semanales;
- lee ingredientes activos de esas recetas;
- agrupa por nombre y unidad, ignorando mayusculas/minusculas;
- suma cantidades cuando hay cantidad;
- crea items no marcados como comprados.

## Favoritos implementados

Endpoint base:

```text
/api/v1/families/{familyId}/favorite-recipes
```

Endpoints:

```text
GET /api/v1/families/{familyId}/favorite-recipes
POST /api/v1/families/{familyId}/favorite-recipes
GET /api/v1/families/{familyId}/favorite-recipes/{favoriteId}
DELETE /api/v1/families/{familyId}/favorite-recipes/{favoriteId}
```

Campos:

- `familyId`
- `recipeId`
- `recipeTitle`
- `createdAt`
- `updatedAt`
- `syncVersion`
- `deleted`

Comportamiento:

- favorito familiar, no individual;
- valida que la receta pertenece a la familia autenticada;
- constraint unico por familia y receta;
- si se vuelve a marcar como favorita una receta borrada logicamente del listado de favoritos, se restaura el mismo registro;
- integrado en sincronizacion como `favoriteRecipes`.

## Notas familiares implementadas

Endpoint base:

```text
/api/v1/families/{familyId}/notes
```

Endpoints:

```text
GET /api/v1/families/{familyId}/notes
POST /api/v1/families/{familyId}/notes
GET /api/v1/families/{familyId}/notes/{noteId}
PUT /api/v1/families/{familyId}/notes/{noteId}
DELETE /api/v1/families/{familyId}/notes/{noteId}
```

Campos:

- `familyId`
- `recipeId` opcional
- `recipeTitle` en respuesta cuando hay receta
- `title`
- `body`
- `pinned`
- `createdAt`
- `updatedAt`
- `syncVersion`
- `deleted`

Comportamiento:

- nota familiar, no individual;
- puede asociarse a una receta de la misma familia;
- valida ownership familiar de la receta opcional;
- listado ordenado por `pinned` y `updatedAt`;
- integrado en sincronizacion como `familyNotes`.

## Fotos de receta implementadas

Endpoint base:

```text
/api/v1/families/{familyId}/recipes/{recipeId}/photos
```

Endpoints:

```text
GET /api/v1/families/{familyId}/recipes/{recipeId}/photos
GET /api/v1/families/{familyId}/recipes/{recipeId}/photos?includeDeleted=true
POST /api/v1/families/{familyId}/recipes/{recipeId}/photos
GET /api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}
PUT /api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}
DELETE /api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}
```

Campos:

- `recipeId`
- `position`
- `url`
- `thumbnailUrl`
- `caption`
- `contentType`
- `sizeBytes`
- `createdAt`
- `updatedAt`
- `syncVersion`
- `deleted`

Reglas:

- nunca almacenar binarios en MySQL;
- solo metadata y URLs;
- URLs `http` o `https`;
- tipos permitidos: `image/jpeg`, `image/png`, `image/webp`;
- al borrar receta se soft-deletean fotos;
- integrado en sincronizacion como `recipePhotos`.

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
- `menuItems`
- `shoppingLists`
- `shoppingListItems`
- `favoriteRecipes`
- `familyNotes`
- `recipePhotos`

`push` acepta:

- recetas;
- ingredientes;
- pasos;
- stock familiar;
- menus semanales;
- listas de compra;
- favoritos;
- notas familiares;
- fotos de receta;
- IDs estables de cliente;
- tombstones con `deleted=true`.

Estrategia inicial:

- compatibilidad Last Write Wins si el cliente no envia `baseSyncVersion`;
- deteccion opcional de conflictos si el cliente envia `baseSyncVersion`;
- respuesta `409 conflict` cuando `baseSyncVersion` no coincide con `syncVersion` actual del servidor;
- `updatedAt` asignado por servidor;
- `syncVersion` asignado por servidor;
- ownership familiar obligatorio;
- `pull` permitido para cualquier miembro activo;
- `push` limitado a roles `OWNER` y `ADMIN`;
- borrados desconocidos se ignoran para no crear basura;
- no se permite mover silenciosamente contenido existente a otra receta.

## Migraciones Flyway

Implementadas:

```text
V1__create_identity_schema.sql
V2__create_recipes_schema.sql
V3__create_recipe_contents_schema.sql
V4__create_stock_schema.sql
V5__create_menu_schema.sql
V6__create_shopping_schema.sql
V7__create_favorites_schema.sql
V8__create_notes_schema.sql
V9__create_recipe_photos_schema.sql
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
- `menu_items`
- `shopping_lists`
- `shopping_list_items`
- `favorite_recipes`
- `family_notes`
- `recipe_photos`

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
- menus semanales;
- listas de compra;
- generacion menu -> compra;
- favoritos;
- notas familiares;
- fotos de receta;
- conflictos sync con `baseSyncVersion`;
- sync pull;
- sync push;
- permisos por rol familiar;
- rate limiting de auth;
- hardening HTTP/CORS;
- tombstones y soft delete.

Ultima verificacion ejecutada:

```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','User') + ';' + [Environment]::GetEnvironmentVariable('Path','Machine'); mvn verify
```

Resultado:

```text
Tests run: 57
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## Estado Git actual

Se cerraron commits locales:

```text
Stabilize backend OpenAPI and dev seed data
Update continuation notes after backend stabilization
```

No se ha hecho push.

El repositorio quedo por delante de `origin/main` en 2 commits antes de iniciar Android.
La fase Android esta sin commitear todavia.

Antes de continuar:

```powershell
git status --short --branch
git diff --stat
```

## Que falta

Prioridad backend:

1. Revisar diff final y decidir si hacer push del commit backend actual.
2. Opcional: probar manualmente Swagger UI con el perfil `dev`.

## Android iniciado

Se creo un proyecto Android real en:

```text
android/
```

Stack inicial:

- Android Gradle Plugin 9.2.0.
- Kotlin 2.3.20.
- Compose + Material 3.
- Retrofit 3.
- OkHttp logging.
- Room 2.8.4.
- KSP para Room.
- WorkManager.
- MVVM ligero sin DI framework externo.

Archivos principales:

- `android/settings.gradle.kts`
- `android/build.gradle.kts`
- `android/gradle.properties`
- `android/app/build.gradle.kts`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/RecetasApplication.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/MainActivity.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/core/AppContainer.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/core/SessionStore.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/RecetasApi.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/remote/dto/ApiDtos.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/local/RecetasDatabase.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/local/Daos.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/local/Entities.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/Repositories.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/sync/SyncWorker.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt`
- `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/theme/Theme.kt`

Implementado:

- login contra `POST /api/v1/auth/login`;
- almacenamiento local de access token, refresh token y familyId;
- interceptor Bearer JWT;
- cliente Retrofit con base URL por defecto `http://10.0.2.2:8080/`;
- Room cache para recetas y stock;
- repositorios de auth, recetas, stock y sync;
- WorkManager periodico para `sync/pull`;
- pantallas Compose iniciales: login, recetas, detalle de receta y stock;
- tema Material 3 basico;
- README Android actualizado.

Validacion ejecutada:

```powershell
cd android
gradle tasks
```

Resultado:

```text
BUILD SUCCESSFUL
```

Tambien se intento:

```powershell
gradle :app:compileDebugKotlin
```

Resultado:

```text
SDK location not found.
```

Bloqueo actual:

- no existe `ANDROID_HOME`;
- no existe `ANDROID_SDK_ROOT`;
- no existe SDK en `%LOCALAPPDATA%\Android\Sdk`;
- no existe SDK en `C:\Android\Sdk`;
- falta crear `android/local.properties` con `sdk.dir=...` o instalar/configurar Android SDK.

Advertencia tecnica:

- AGP 9.2 trae Kotlin integrado, pero Room/KSP necesito mantener temporalmente `org.jetbrains.kotlin.android` y `android.builtInKotlin=false` / `android.newDsl=false`.
- Esto permite cargar tareas Gradle, pero genera warnings de deprecacion que habra que revisar cuando KSP/Room encajen mejor con Kotlin integrado de AGP 9.

Prioridad Android siguiente:

1. Instalar/configurar Android SDK.
2. Ejecutar `gradle :app:compileDebugKotlin`.
3. Corregir errores de compilacion Kotlin/Android si aparecen.
4. Ejecutar `gradle :app:assembleDebug`.
5. Revisar UI en emulador/dispositivo.
6. Commit del scaffold Android cuando compile.

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
Revisar estado Git y hacer push del commit backend actual si se quiere cerrar la fase backend.
```

Motivo:

- ya existen todos los modulos MVP principales del backend;
- los permisos familiares por rol ya estan aplicados;
- el rate limiting de auth ya esta aplicado y testeado;
- el hardening HTTP/CORS ya esta aplicado y testeado;
- la exposicion y documentacion OpenAPI ya quedaron revisadas y testeadas;
- el seed de desarrollo opcional ya quedo implementado y testeado.

Alternativa si se quiere empezar clientes:

```text
Crear proyecto Android real y cliente Retrofit contra los contratos backend actuales.
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

5. Continuar configurando Android SDK y compilar el scaffold Android.

## Nota importante

No empezar por Android ni Desktop hasta confirmar que los contratos backend estan suficientemente estables.

El backend ya tiene base real y contratos iniciales para:

- auth;
- familias;
- recetas;
- ingredientes;
- pasos;
- stock;
- menus semanales;
- listas de compra;
- favoritos;
- notas familiares;
- fotos de receta;
- sincronizacion pull/push.

La siguiente fase debe mantener compatibilidad con Android/Desktop y no romper los contratos JSON existentes sin motivo fuerte.
