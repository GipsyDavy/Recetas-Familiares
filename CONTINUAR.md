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
- `Resumen.md`
- `MACRO-PROMPT-RECETAS-FAMILIA.md`
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

---

## Entorno de desarrollo

### Java
- Ejecutable: `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`
- Version activa: Java 26

### Gradle (Android)
- Instalacion global: `C:\tmp\tools\gradle-9.5.1\bin\gradle.bat`
- **No hay gradlew en el proyecto** — usar gradle global
- Compilar APK: ejecutar `gradle assembleDebug` desde `android/`

### Maven (Desktop + Backend)
- Disponible en PATH: `C:\Program Files\Apache NetBeans\java\maven\bin`
- Si no responde, recargar PATH:
  ```powershell
  $env:Path = [Environment]::GetEnvironmentVariable('Path','User') + ';' + [Environment]::GetEnvironmentVariable('Path','Machine')
  ```

### Android SDK
- SDK dir: `C:\Users\GipsyDavy\AndroidSDK`
- ADB: `C:\Users\GipsyDavy\AndroidSDK\platform-tools\adb.exe`
- AVD: `Pixel_9_Pro` (API 36) — arranca desde snapshot en ~5 segundos
- `android/local.properties` (no en git):
  ```properties
  sdk.dir=C\:\\Users\\GipsyDavy\\AndroidSDK
  ```

### MySQL
- Servicio Windows: `MySQL80` (corriendo)
- Host: localhost:3306
- Usuario app: `recetas_app` / `Recetas2024!`
- Base de datos: `recetas_familiares`
- Root: password desconocido (no es "root")

---

## Arranque del entorno dev

### 1. Arrancar backend (USAR BASH, no PowerShell — evita problemas con ! en passwords)

```bash
java -jar "C:\Users\GipsyDavy\MAVEN\Recetas Familiares\backend\target\recetas-familiares-backend-0.1.0-SNAPSHOT.jar" \
  --spring.profiles.active=dev \
  "--spring.datasource.password=Recetas2024!" \
  "--app.dev.seed-data.enabled=true" \
  "--app.dev.seed-data.email=demo@recetas.local" \
  "--app.dev.seed-data.password=Demo1234!Familia" \
  "--app.dev.seed-data.display-name=Demo" \
  "--app.dev.seed-data.family-name=FamiliaDemo" \
  > /tmp/backend.log 2>&1 &

# Esperar arranque:
until grep -q "Started BackendApplication" /tmp/backend.log; do sleep 3; done
```

Credenciales seed: `demo@recetas.local` / `Demo1234!Familia`
Nota: el seed NO actualiza password si el usuario ya existe. La primera ejecucion lo fija.

### 2. Arrancar emulador Android

```powershell
$emulator = "C:\Users\GipsyDavy\AndroidSDK\emulator\emulator.exe"
& $emulator -avd Pixel_9_Pro -no-snapshot-save
```

### 3. Compilar e instalar APK

```powershell
# Desde android/
gradle assembleDebug
$adb = "C:\Users\GipsyDavy\AndroidSDK\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 4. Arrancar Desktop

```powershell
cd "C:\Users\GipsyDavy\MAVEN\Recetas Familiares\desktop"
mvn javafx:run -Dapi.base.url=http://localhost:8080/
```

---

## Backend (COMPLETO Y ESTABLE)

Stack: Spring Boot 3.5.14 + Java 21 + MySQL + Flyway + JWT.

Estado: **57 tests, 0 fallos.**

Modulos implementados:
- auth (register, login, refresh, logout)
- familias
- recetas (CRUD + paginacion + soft delete)
- ingredientes y pasos (PUT replace-all + tombstones)
- stock familiar
- menus semanales
- listas de compra (con generate-from-menu)
- favoritos
- notas familiares
- fotos de receta (solo metadata/URLs)
- sincronizacion pull/push completa con tombstones y deteccion de conflictos

Seguridad:
- JWT Bearer (TTL 15min) + refresh tokens opacos (hash SHA-256)
- BCrypt(12) para passwords
- Rate limiting en auth
- CSP, HSTS, CORS deny-by-default
- OpenAPI desactivado en produccion

Migraciones Flyway V1-V9 (tablas: users, families, family_members, recipes, ingredients, steps, stock_items, menus, shopping_lists, shopping_list_items, favorite_recipes, family_notes, recipe_photos, refresh_tokens).

### Contratos API criticos (no cambiar sin revisar Android y Desktop)

- `PageResponse<T>` usa campos: `items`, `page`, `size`, `totalItems`, `totalPages`
  (NO `content` / `totalElements` — esos son Spring Page, no el DTO custom del backend)
- Notas: `GET/POST/PUT/DELETE /api/v1/families/{id}/notes`
  (NO `/family-notes`)
- Stock: `GET /api/v1/families/{id}/stock-items`
  (NO `/stock`)
- `StockItemResponse`: campo `name` (NO `ingredientName`)
- `RecipeIngredientResponse`: `position`, `name`, `quantity` (BigDecimal), `note`
- `RecipeStepResponse`: `position`, `instruction`, `timerMinutes`

---

## Android Kotlin + Compose — SPRINT 6 COMPLETO (2026-05-28)

Stack:
- AGP 9.2.0 + Kotlin 2.3.20 + KSP 2.3.7
- Compose + Material 3 (BOM 2026.05.00)
- Retrofit 3 + OkHttp 5
- Room 2.8.4 (version DB: 2, MIGRATION_1_2 no-op)
- WorkManager 2.11.2
- security-crypto 1.1.0-alpha06
- MVVM sin DI framework (AppContainer manual)

### Fixes criticos aplicados (no revertir)
- AGP 9 DSL: `kotlin { compilerOptions { jvmTarget = JVM_11 } }`, sin `kotlinOptions`, sin plugin `org.jetbrains.kotlin.android`
- KSP `2.3.7` (alineado con Kotlin 2.3.20)
- `org.gradle.jvmargs=-Xmx4g` (D8 OutOfMemoryError)
- SSL PKIX: `org.gradle.jvmargs` incluye `-Djavax.net.ssl.trustStoreType=Windows-ROOT`
- `res/xml/network_security_config.xml`: permite cleartext a `10.0.2.2`
- `AndroidManifest.xml`: `android:networkSecurityConfig="@xml/network_security_config"`

### Arquitectura Android
- `RecetasApplication` → `AppContainer` singleton
- `SessionStore` → `EncryptedSharedPreferences` (tokens + lastSyncTime)
- `ApiClient` → OkHttp con `TokenRefreshAuthenticator`
- Room v2: 10 entidades, 10 DAOs con `@Upsert`
- `SyncWorker` → WorkManager, sync incremental
- `RecetasViewModel`: StateFlows para recipes, stock, shoppingItems, favorites, notes
- `AppContainer`: contiene todos los repositories incluyendo `familyNoteRepository`

### Pantallas implementadas (Sprint 1-6)
- `LoginScreen`
- `RecipeListScreen` + `RecipeDetailScreen` (ingredientes + pasos)
- `StockScreen` (badges bajo stock, colores caducidad)
- `ShoppingListScreen`
- `NotesScreen` + `NoteCard` + `NoteDetail` + `NoteForm`
- Bottom Navigation: 4 tabs — RECIPES, STOCK, SHOPPING, NOTES

### RecetasApi.kt — endpoints implementados
- login, families
- recipes (list, detail)
- stockItems (list)
- pullSync, pushSync
- addFavorite, removeFavorite
- updateShoppingListItem
- createNote, updateNote, deleteNote (rutas correctas: `/notes`)

Build: `gradle assembleDebug` desde `android/` — EXITOSO

---

## Desktop JavaFX — SPRINT 6 + FIXES COMPLETO (2026-05-28)

Stack: Java 21 + JavaFX 21.0.2 + OkHttp 4.12.0 + Gson 2.10.1 + Maven.

Fat JAR: 13.3 MB. SSL fix: `desktop/.mvn/jvm.config` con Windows-ROOT truststore.

### Pantallas implementadas (Sprint 1-6)
- `LoginView`
- `DashboardView` — GridPane 2 columnas: recetas recientes + stock expirando + acciones
- `RecipeListView` — SplitPane lista filtrable + detalle
- `RecipeDetailView` — ingredientes, pasos, Editar + Eliminar
- `RecipeFormDialog` — modal `forCreate()` / `forEdit()`
- `StockView` — TableView (solo lectura por ahora)
- `WeeklyMenuView` — calendario 8x5, nav semanas, CRUD assign/remove
- `ShoppingListView`
- `NotesView` — SplitPane lista + editor inline, CRUD completo, NoteCell con 📌

### Sidebar completa
Inicio | Recetas | Stock | Menú semanal | Lista de la compra | Notas familiares | [Sincronizar] [Cerrar sesión]

### AppContext — dependencias completas
```java
syncRepository = new SyncRepository(apiClient, session,
    recipeRepository, stockRepository, menuRepository,
    shoppingListRepository, favoriteRepository, noteRepository);
```

### Fixes criticos aplicados en commit 5404a7b (no revertir)
- `StockDtos.java`: campo `name` (NO `ingredientName`)
- `RecipeDtos.java`: `quantity` Double, `position`, `note` en ingredientes; `instruction`/`timerMinutes` en pasos; `RecipePageResponse` usa `items`/`totalItems`/`totalPages`
- `StockRepository.java`: endpoint `/stock-items` (NO `/stock`)
- `NoteRepository.java`: endpoint `/notes` (NO `/family-notes`)
- `SyncRepository.java`: actualiza cache de menu, shopping, favorites (antes solo recipes+stock)
- `ShoppingListRepository.java`: metodo `updateFromSync()` anadido
- DashboardView, StockView, RecipeDetailView, RecipeListView, RecipeFormDialog: referencias actualizadas

mvn compile — EXITOSO

---

## Estado Git (2026-05-28)

Rama: `main` — limpio, sin cambios pendientes.

Commits recientes:
```
5404a7b fix: corregir contratos DTO Desktop y URLs de endpoints   ← HEAD
6ecf16b feat: Sprint 6 — notas familiares en Android y Desktop
dc0d655 feat: Sprint 5 — lista de la compra y favoritos en Android y Desktop
18430cc feat: Sprint 4 — persistencia tokens, CRUD menú semanal y migraciones Room
cd680c9 feat: Sprint 3 — detalle de receta Android, Stock mejorado y menú semanal Desktop
```

---

## Sprint 7 — EN CURSO (2026-05-28)

### Tarea 1 — CRUD Stock Items en Desktop ✅ COMPLETADA (2026-05-28)

Archivos modificados/creados:
- `StockDtos.java`: añadidos `lowStockThreshold`, `note` a `StockItemDto`; nuevos `CreateStockItemRequest`, `UpdateStockItemRequest`
- `StockRepository.java`: añadidos `create()`, `update()`, `delete()`
- `StockView.java`: toolbar (Nuevo/Editar/Eliminar), columna "Mín. stock" con ⚠ visual, deshabilitar botones sin selección
- `StockFormDialog.java` (nuevo): modal 500×420 con campos primarios (name, quantity, unit, expiresAt) + sección colapsable "Avanzado" (lowStockThreshold, note). Patrón forCreate()/forEdit() idéntico a RecipeFormDialog.

Compilación: `mvn compile` — EXITOSO.

### Tarea 2 — CRUD Stock Items en Android (PRIORIDAD ALTA)

`StockScreen` es solo lectura. Falta FAB crear + gesto editar/eliminar.

Archivos a tocar:
1. `android/.../data/remote/dto/ApiDtos.kt` — anadir `CreateStockItemRequestDto`, `UpdateStockItemRequestDto`
2. `android/.../data/remote/RecetasApi.kt` — anadir `@POST`, `@PUT`, `@DELETE` stock-items
3. `android/.../data/repository/Repositories.kt` — expandir `StockRepository` con create/update/delete
4. `android/.../ui/RecetasViewModel.kt` — anadir funciones de mutacion stock
5. `android/.../ui/RecetasApp.kt` — FAB en `StockScreen`, composable `StockForm`

### Tarea 3 — Crear/Editar Receta desde Android (PRIORIDAD MEDIA)

`RecipeListScreen` tiene FAB pero no navega a ningún formulario.

Archivos a tocar:
1. `RecetasApi.kt` — anadir POST/PUT/DELETE recipes
2. `ApiDtos.kt` — `CreateRecipeRequestDto`, `UpdateRecipeRequestDto`
3. `Repositories.kt` — expandir `RecipeRepository`
4. `RecetasViewModel.kt` — anadir mutaciones
5. `RecetasApp.kt` — nuevo composable `RecipeFormScreen`

Referencia de campos: ver `RecipeFormDialog.java` en Desktop.

### Tarea 4 — SyncWorker Android push (PRIORIDAD MEDIA-BAJA)

Actualmente solo hace PULL. Cambios offline nunca suben.
En `SyncRepository.kt` Android: implementar `pushPendingChanges()`.

---

## Procedimiento al retomar

1. Leer: `CLAUDE.md`, `Resumen.md`, este `CONTINUAR.md`

2. Comprobar estado git:
   ```powershell
   git status --short --branch
   ```

3. Arrancar MySQL si no esta corriendo:
   ```powershell
   Start-Service MySQL80
   ```

4. Arrancar backend (ver seccion "Arranque del entorno dev").

5. Verificar backend:
   ```bash
   curl -s http://localhost:8080/actuator/health
   ```
   Nota: actuator esta protegido en dev, respuesta 401 = backend corriendo.

6. Decir al agente que tarea del Sprint 7 quieres abordar.

---

## Deuda tecnica conocida y aceptada

- Push sync Android envia listas vacias (sin cola de cambios offline). Correcto para MVP.
- Sync pull sin paginacion: aceptable para familias pequenas.
- Login devuelve primera familia (no determinista si hay varias): limitacion documentada para MVP.
- Advertencia Mockito/Byte Buddy con Java 26: no rompe build ni tests.
- StockView Desktop es solo lectura (tarea Sprint 7).
- Crear/editar receta desde Android no implementado (tarea Sprint 7).
