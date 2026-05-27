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
- Android nativo Kotlin + Compose;
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

Estado: **COMPLETO Y ESTABLE**. 57 tests, 0 fallos.

## Configuracion backend

Archivos:

- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/src/test/resources/application-test.yml`

## Seguridad backend implementada

- seguridad stateless;
- JWT Bearer access token (TTL 15 min por defecto);
- refresh tokens opacos almacenados como hash SHA-256;
- rotacion de refresh token;
- logout con revocacion;
- BCrypt(12) para passwords;
- errores JSON para 401 y 403;
- filtros JWT;
- endpoints publicos limitados a auth, health y Swagger/OpenAPI;
- permisos familiares por rol: `OWNER` y `ADMIN` escriben/borran/sync push; `MEMBER` lee;
- rate limiting configurable en auth endpoints;
- respuestas `429` con codigo `rate_limited` y cabecera `Retry-After`;
- cabeceras defensivas HTTP: CSP, nosniff, frame deny, referrer policy y permissions policy;
- HSTS configurado para HTTPS;
- CORS deny-by-default salvo origenes configurados en `CORS_ALLOWED_ORIGINS`.

## Modulos backend implementados

- auth (register, login, refresh, logout);
- familias;
- recetas (CRUD + paginacion + soft delete);
- ingredientes y pasos (PUT replace-all + tombstones);
- stock familiar;
- menus semanales;
- listas de compra (con generate-from-menu);
- favoritos;
- notas familiares;
- fotos de receta (solo metadata/URLs, nunca binarios);
- sincronizacion pull/push completa con tombstones y deteccion de conflictos.

## Migraciones Flyway

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

## Tests backend

- 57 tests, 0 fallos, 0 errores.
- Cubren: contexto, health, auth, familias, recetas, contenidos, stock, menus, compra, favoritos, notas, fotos, sync pull/push, permisos, rate limiting, hardening HTTP/CORS, tombstones.

## Estado Git actual

- Rama main por delante de origin/main en 3 commits.
- No se ha hecho push todavia.
- Commits locales pendientes de push:
  - `ff73840 Scaffold Android client`
  - `a58ebca Update continuation notes after backend stabilization`
  - `2aad060 Stabilize backend OpenAPI and dev seed data`

## Android - Estado actual (Sprint 2 completado)

Ruta:

```text
android/
```

Stack:

- Android Gradle Plugin 9.2.0.
- Kotlin 2.3.20.
- Compose + Material 3.
- Retrofit 3.
- OkHttp logging.
- Room 2.8.4 (version DB: 2).
- KSP para Room.
- WorkManager.
- security-crypto 1.1.0-alpha06.
- MVVM ligero sin DI framework externo.

### Corregido en Sprint 2

1. **`isLoggedIn` reactivo** — Ahora es `StateFlow<Boolean>`. La navegacion login → main funciona correctamente.
2. **`SyncPullDto` completo** — Las 11 colecciones del backend: recipes, ingredients, steps, stockItems, menuItems, shoppingLists, shoppingListItems, favoriteRecipes, familyNotes, recipePhotos.
3. **`SyncPushRequestDto` tipado** — Contrato push alineado con `SyncPushRequest` del servidor. Las colecciones requeridas (recipes, ingredients, steps) se envian siempre como lista vacia si no hay cambios.
4. **`TokenRefreshAuthenticator`** — OkHttp Authenticator que detecta 401, llama a `/api/v1/auth/refresh`, actualiza tokens y reintenta. Limpia sesion si el refresh falla.
5. **`SessionStore` con `EncryptedSharedPreferences`** — Tokens cifrados con AES256-GCM. Incluye campo `lastSyncTime` para sync incremental.
6. **`AppContainer` singleton** — `sessionStore` y `database` publicos. `SyncWorker` usa el `AppContainer` del `RecetasApplication` (singleton).
7. **Logging condicional** — `HttpLoggingInterceptor.Level.BASIC` en debug, `NONE` en release.
8. **Room version 2** — Todas las entidades: recetas, ingredientes, pasos, stock, menus, listas de compra, items de lista, favoritos, notas, fotos. `fallbackToDestructiveMigration` para desarrollo.
9. **DAOs completos** — 10 DAOs con queries tipadas y `@Upsert`.
10. **Mappers completos** — Todos los DTOs a entidades Room.
11. **Sync incremental** — `SyncRepository.pullOnce()` pasa `lastSyncTime` como `since` y guarda el nuevo `serverTime`.
12. **`allowBackup=false`** — Seguridad: no se hace backup de tokens cifrados.

### Bloqueo Android actual

- No existe `ANDROID_HOME` / `ANDROID_SDK_ROOT`.
- Falta crear `android/local.properties` con `sdk.dir=...`.
- Sin SDK no compila.

### Proximo paso Android

1. Instalar/configurar Android SDK.
2. Crear `android/local.properties`:
   ```properties
   sdk.dir=C\:\\Users\\GipsyDavy\\AppData\\Local\\Android\\Sdk
   ```
3. Ejecutar:
   ```powershell
   cd android
   gradle :app:compileDebugKotlin
   gradle :app:assembleDebug
   ```
4. Corregir errores de compilacion si aparecen (KSP version, AGP flags).
5. Probar en emulador con backend en dev.

### Advertencia tecnica Android

- AGP 9.2 + KSP: `android.builtInKotlin=false` / `android.newDsl=false` son workarounds temporales.
- KSP version `2.3.0` puede estar desalineada con Kotlin `2.3.20`. Verificar y alinear cuando compile.
- `fallbackToDestructiveMigration` activo: la DB Room se reinicia ante cambios de schema. Aceptable en desarrollo.

## Desktop JavaFX (Sprint 1 completado)

Scaffold completo creado. Ruta:

```text
desktop/
```

Stack:

- Java 21 + JavaFX 21.0.2.
- OkHttp 4.12.0 (cliente HTTP con TokenRefresh Authenticator).
- Gson 2.10.1.
- Maven con javafx-maven-plugin.

### Implementado en Sprint 1

1. **`pom.xml`** — JavaFX 21, OkHttp, Gson, maven-shade-plugin para fat jar.
2. **`DesktopApp`** — `Application` JavaFX. Punto de entrada.
3. **`AppSession`** — tokens en memoria, `familyId` y `lastSyncTime` en `java.util.prefs`.
4. **`AppContext`** — singleton container sin DI framework.
5. **`ApiClient`** — OkHttp con `Authenticator` para refresh JWT automático en 401. Cliente de refresh separado para evitar bucles.
6. **DTOs** — `AuthDtos`, `RecipeDtos`, `StockDtos`, `SyncDtos` con Java records.
7. **`SimpleCache<T>`** — `ObservableList` para binding directo en JavaFX.
8. **Repositorios** — `AuthRepository`, `RecipeRepository`, `StockRepository`, `SyncRepository`.
9. **`LoginView`** — formulario login con feedback de error y llamada en hilo virtual.
10. **`RecipeListView`** — `SplitPane` lista filtrable + `RecipeDetailView` con ingredientes y pasos.
11. **`StockView`** — `TableView` con ingrediente, cantidad y fecha de caducidad.
12. **`MainWindow`** — `BorderPane` con sidebar oscuro, navegacion Recetas/Stock, boton Sincronizar, logout.
13. **`style.css`** — paleta calida: tonos tierra (`#3D2B1F`, `#C17D52`, `#FAF7F2`).
14. **`module-info.java`** — modulo Java 9+.

### Ejecutar Desktop

```powershell
cd "C:\Users\GipsyDavy\MAVEN\Recetas Familiares\desktop"
mvn javafx:run -Dapi.base.url=http://localhost:8080/
```

### Deuda tecnica Desktop

- Tokens no se persisten entre reinicios (solo en memoria). Pendiente: keystore OS o cifrado similar a EncryptedSharedPreferences.
- Push sync envia listas vacias — sin cola de cambios offline todavia.
- Sin paginacion en scroll infinito — aceptable para MVP.
- Modo Cocina (letra grande, temporizadores) no implementado todavia.

## Procedimiento al retomar

1. Abrir la raiz: `C:\Users\GipsyDavy\MAVEN\Recetas Familiares`

2. Leer: `CLAUDE.md`, `MACRO-PROMPT-RECETAS-FAMILIA.md`, `Resumen.md`, `CONTINUAR.md`, `backend/README.md`

3. Comprobar estado:
   ```powershell
   git status --short --branch
   ```

4. Validar backend:
   ```powershell
   $env:Path = [Environment]::GetEnvironmentVariable('Path','User') + ';' + [Environment]::GetEnvironmentVariable('Path','Machine'); mvn verify
   ```

5. Continuar con SDK Android o compilar Desktop con `mvn javafx:run`.

## Deuda tecnica conocida y aceptada

- Push sync envia listas vacias (no hay cola de cambios offline todavia). Correcto para MVP.
- `fallbackToDestructiveMigration` en Room: cambiar por migraciones explicitas antes de beta.
- KSP version desalineada con Kotlin: verificar cuando compile.
- SyncService.push() sin batch: O(n) queries individuales. Documentado, no urgente para MVP.
- Sync pull sin paginacion: aceptable para familias pequenas.
- Login devuelve primera familia (no determinista si hay varias): limitacion documentada para MVP.
