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

## Android Kotlin + Compose — SPRINT 11 COMPLETO (2026-05-28)

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

### Pantallas implementadas (Sprint 1-11)
- `LoginScreen`
- `TopAppBar` con búsqueda global unificada → `GlobalSearchScreen` (Recetas/Stock/Notas)
- `RecipeListScreen` (paginación, búsqueda, pull-to-refresh, FAB) + `RecipeDetailScreen` (fotos, valoraciones, menú ⋮)
- `RecipeForm` (SegmentedButton dificultad, filas dinámicas ingredientes/pasos)
- `CookingScreen` (paso a paso, temporizador countdown, keep screen on)
- `StockScreen` (CRUD completo, notificaciones caducidad WorkManager, búsqueda)
- `ShoppingListScreen` (check offline-resilient)
- `NotesScreen` (CRUD completo, búsqueda, empty states)
- Bottom Navigation: 4 tabs — RECIPES, STOCK, SHOPPING, NOTES
- Snackbar feedback global
- SyncWorker pushThenPull: Recetas + Ingredientes + Pasos + Stock + Notas + Shopping items
- Widgets: `RecipeWidget` (receta del día, 4×2) + `StockWidget` (ítems críticos, 2×2)

### RecetasApi.kt — endpoints implementados
- login, families
- recipes (list paginado, detail, create, update, delete)
- stockItems (list, create, update, delete)
- photos (list, upload multipart, delete)
- ratings (create, update, delete, list)
- pullSync, pushSync
- addFavorite, removeFavorite
- updateShoppingListItem
- createNote, updateNote, deleteNote (rutas: `/notes`)

Build: `gradle assembleDebug` desde `android/` — EXITOSO

---

## Desktop JavaFX — SPRINT 11 COMPLETO (2026-05-28)

Stack: Java 21 + JavaFX 21.0.2 + OkHttp 4.12.0 + Gson 2.10.1 + Maven.

Fat JAR: 13.3 MB. SSL fix: `desktop/.mvn/jvm.config` con Windows-ROOT truststore.

### Pantallas implementadas (Sprint 1-11)
- `LoginView`
- `DashboardView` — GridPane 2 columnas: recetas recientes + stock expirando + acciones
- `RecipeListView` — SplitPane filtrable, búsqueda, paginación 30/pág + "Cargar más", botón "Actualizar"
- `RecipeDetailView` — ingredientes, pasos, fotos async, Editar + Eliminar + Modo Cocina
- `RecipeFormDialog` — modal `forCreate()` / `forEdit()`
- `CookingView` — Stage maximizado, paso a paso, temporizador JavaFX Timeline
- `StockView` — TableView CRUD, búsqueda, columna "Mín. stock", botón "Actualizar"
- `WeeklyMenuView` — calendario 8x5, nav semanas, CRUD assign/remove, botón "Actualizar"
- `ShoppingListView` — dialog de items con check, botón "Actualizar" → sync completo
- `NotesView` — SplitPane lista + editor inline, búsqueda FilteredList, botón "Actualizar"
- `GlobalSearchView` — resultados agrupados Recetas/Stock/Notas desde sidebar (Sprint 10.2)

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

Rama: `main` — limpio, sin cambios pendientes. Pusheado a origin.

Commits recientes:
```
b22fdaf fix: Auditoría Fase 6 — Authorization POST + timeouts OkHttp Desktop y Android  ← HEAD
58bcf2a fix: Auditoría Codex Desktop — timeouts OkHttp + shutdown completo
f7d19eb fix: Auditoría Codex Android — CancellationException + isRefreshing try/finally
0fab8d1 audit: Auditoría completa Sprint 1-9 — seguridad, tests, calidad y limpieza
c88d359 feat: Sprint 9.3 — Paginación de recetas Android
e2461c9 feat: Sprint 9.1/9.2 — Notificaciones caducidad + Valoraciones familiares
```

---

## Sprint 7 — COMPLETADO (2026-05-28)

### Tarea 1 — CRUD Stock Items en Desktop ✅ COMPLETADA (2026-05-28)

Archivos modificados/creados:
- `StockDtos.java`: añadidos `lowStockThreshold`, `note` a `StockItemDto`; nuevos `CreateStockItemRequest`, `UpdateStockItemRequest`
- `StockRepository.java`: añadidos `create()`, `update()`, `delete()`
- `StockView.java`: toolbar (Nuevo/Editar/Eliminar), columna "Mín. stock" con ⚠ visual, deshabilitar botones sin selección
- `StockFormDialog.java` (nuevo): modal 500×420 con campos primarios (name, quantity, unit, expiresAt) + sección colapsable "Avanzado" (lowStockThreshold, note). Patrón forCreate()/forEdit() idéntico a RecipeFormDialog.

Compilación: `mvn compile` — EXITOSO.

### Tarea 2 — CRUD Stock Items en Android ✅ COMPLETADA (2026-05-28)

Archivos modificados (commit 9b4533a):
- `ApiDtos.kt`: CreateStockItemRequestDto, UpdateStockItemRequestDto
- `RecetasApi.kt`: createStockItem(), updateStockItem(), deleteStockItem()
- `Repositories.kt`: StockRepository expandido con create/update/delete + soft-delete Room
- `RecetasViewModel.kt`: createStockItem(), updateStockItem(), deleteStockItem()
- `RecetasApp.kt`: StockList con FAB + navegación inline (patrón NotesScreen) + StockDetail + StockForm (DatePickerDialog M3, sección avanzada colapsable)

Build: `gradle assembleDebug` — BUILD SUCCESSFUL.

### Tarea 3 — Crear/Editar Receta desde Android ✅ COMPLETADA (2026-05-28)

Archivos modificados (commit 7253983): ApiDtos, RecetasApi, Repositories, AppContainer, RecetasViewModel, RecetasApp.
RecipeList con FAB + navegación inline; RecipeDetail con menú ⋮; RecipeForm con SegmentedButton dificultad + filas dinámicas.
Build SUCCESSFUL.

### Tarea 4 — SyncWorker Android push ✅ COMPLETADA (2026-05-28)

Archivos modificados (commit 15079bc): Daos, Repositories, SyncWorker.
- StockRepository.create() y FamilyNoteRepository.create() son ahora offline-resilient: si el API falla, guardan con syncVersion=0.
- SyncRepository.pushThenPull() empuja pendientes (syncVersion=0) antes del pull.
- SyncWorker llama a pushThenPull() en lugar de pullOnce().
Build SUCCESSFUL.

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

6. Continuar con Sprint 10 (ver candidatos al final de este documento).

---

## Sprint 8 — COMPLETADO (2026-05-28)

### Sprint 8.1 — Feedback Snackbar + Pull-to-refresh Android ✅ COMPLETADO (2026-05-28)

Archivos modificados (commit 8a0cc18):
- `RecetasViewModel.kt`: `_isRefreshing` StateFlow, `_userMessage` SharedFlow(extraBufferCapacity=1).
  `refresh()` ahora trackea estado refreshing. Todos los CRUD emiten mensaje de exito.
- `RecetasApp.kt`: SnackbarHostState en MainShell con LaunchedEffect collector. SnackbarHost en Scaffold.
  PullToRefreshBox en RecipeList, StockList, ShoppingListScreen y NotesScreen.

Build: `gradle assembleDebug` — BUILD SUCCESSFUL.

### Sprint 8.2 — Busqueda global ✅ COMPLETADO (2026-05-28)

**Android** (commit 8a0cc18):
- `RecetasApp.kt`: Buscador OutlinedTextField en RecipeList (titulo+descripcion), StockList (nombre) y
  NotesScreen (titulo+cuerpo). Filtro local en memoria. Empty state contextual con query.

**Desktop** (commit 8a0cc18):
- `RecipeListView.java`: updateRecipeCount() reactivo via ListChangeListener. Label "Mostrando X de Y"
  al filtrar. StatusLabel reposicionado encima del boton Nuevo (mejor UX).

mvn compile — EXITOSO.

### Sprint 8.3 — Modo Cocina Android ✅ COMPLETADO (2026-05-28)

Archivos modificados (commit 8a0cc18):
- `RecetasApp.kt`: CookingScreen composable — pantalla completa con:
  - Paso a paso con LinearProgressIndicator.
  - Instruccion en MaterialTheme.typography.headlineMedium (letra grande).
  - Temporizador countdown MM:SS con play/pause; color cambia al terminar.
  - Keep screen on con FLAG_KEEP_SCREEN_ON (DisposableEffect).
  - Navegacion Anterior/Siguiente; estado final "Buen provecho!".
  - Acceso desde menu contextual de RecipeDetail (opcion "Modo Cocina").
  - cookingMode state en RecipeList con branch en when{}.

Build: `gradle assembleDebug` — BUILD SUCCESSFUL. 0 warnings.

---

### Sprint 8.4 — Modo Cocina Desktop ✅ COMPLETADO (2026-05-28)

Archivos creados/modificados (commit a5174cb):
- `CookingView.java` (nuevo): Stage maximizado con paso a paso, ProgressBar, instruccion en 34px,
  temporizador countdown MM:SS con Timeline JavaFX, play/pause/reiniciar, color rojo al finalizar,
  pantalla final "Buen provecho!".
- `RecipeDetailView.java`: campo `currentSteps`, boton "Modo Cocina" en action bar,
  metodo `openCookingMode()`.

mvn compile: BUILD SUCCESS.

---

### Sprint 8.5 — Empty states ilustrados ✅ COMPLETADO (2026-05-28)

**Android** (commit 20d5ac0):
- EmptyStateView composable: icono 72dp + titulo + subtitulo + CTA opcional.
- RecipeList, StockList, ShoppingListScreen, ShoppingListDetail, NotesScreen mejorados.
- Busqueda sin resultados mantiene texto simple.

**Desktop** (Codex):
- NotesView.java, StockView.java, ShoppingListView.java: VBox centrado con emoji 48px,
  titulo bold y subtitulo en colores del proyecto (#3D2B1F, #8B6F5E).
- mvn compile: BUILD SUCCESS.

### Sprint 8.6 — CRUD Update/Delete offline resilient Android ✅ COMPLETADO (2026-05-28)

Archivos modificados (commit 20d5ac0):
- Daos.kt: findPendingDelete() en StockDao y FamilyNoteDao (syncVersion=0 AND deleted=1).
- Repositories.kt: StockRepository.update/delete y FamilyNoteRepository.update/delete
  con try/catch; fallo -> upsert local con syncVersion=0 (y deleted=true para delete).
  SyncRepository.pushThenPull() incluye pendingStockDelete y pendingNoteDelete en el batch.

---

### Sprint 8.7 — Fotos de receta ✅ COMPLETADO (2026-05-28) [Android+Backend; Desktop via Codex]

**Backend** (commit 9a31cb8):
- FileStorageService: guarda multipart en ./uploads/, UUID filename, max 8MB.
- WebMvcConfig: sirve /uploads/** como recursos estaticos (sin auth).
- POST /api/v1/families/{familyId}/recipes/{recipeId}/photos/upload (multipart).
- SecurityConfig: /uploads/** permitAll.
- application.yml: multipart 10MB, app.upload.dir y base-url.
- 57 tests, 0 fallos.

**Android** (commit 9a31cb8):
- build.gradle.kts: coil3:coil-compose:3.0.4 + coil-network-okhttp:3.0.4.
- RecetasApi.kt: GET photos, POST upload multipart, DELETE photo.
- Repositories.kt: RecipePhotoRepository (loadPhotos, upload, delete).
- RecetasViewModel.kt: launchUploadPhoto() comprime JPEG 85% max 1080px en Dispatchers.IO.
- RecetasApp.kt: LazyRow carrusel en RecipeDetail, PhotoThumbnail long-press delete,
  menu "Añadir foto" lanza gallery picker.
- Build SUCCESSFUL.

**Desktop** (commit ee7066a — Codex):
- RecipeDtos.java: RecipePhotoResponse record.
- RecipeRepository.java: loadPhotos(), uploadPhoto() multipart OkHttp, deletePhoto().
- RecipeDetailView.java: ScrollPane horizontal, ImageView 110x80px async,
  boton Añadir foto con FileChooser, menu contextual eliminar.
- mvn compile: BUILD SUCCESS.

---

---

## Sprint 9 — COMPLETADO (2026-05-28)

### Sprint 9.1 — Notificaciones de caducidad stock Android ✅ COMPLETADO

WorkManager diario que revisa stock próximo a caducar y lanza notificación local al usuario.

### Sprint 9.2 — Valoraciones familiares ✅ COMPLETADO

**Backend** (commit e2461c9):
- Endpoint CRUD valoraciones: `GET/POST/PUT/DELETE /api/v1/families/{fid}/recipes/{rid}/ratings`
- RecipeRatingControllerTest: 5 tests (CRUD, conflicto, validación, acceso cruzado). 57→62 tests.

**Android** (commit e2461c9):
- RecipeRatingRepository + UI en RecipeDetailScreen.

### Sprint 9.3 — Paginación de recetas Android ✅ COMPLETADO (commit c88d359)

RecipeListScreen con carga incremental. PageResponse<T> ya soportado en el backend.

---

## Deuda tecnica conocida y aceptada

- Sync pull sin paginacion: aceptable para familias pequenas.
- Login devuelve primera familia (no determinista si hay varias): limitacion documentada para MVP.
- Advertencia Mockito/Byte Buddy con Java 26: no rompe build ni tests.
- Repositories.kt: fotos, ratings y shopping son online-only intencionalmente (MVP aceptable).
  Recetas, Stock y FamilyNote tienen fallback offline completo (create/update/delete, syncVersion=0).
- Desktop AppSession: tokens en java.util.prefs (Windows Registry, sin cifrar). Documentado. Para
  produccion real: migrar a Windows Credential Manager / macOS Keychain.
- RecetasViewModel.compressImage: logica de imagen en ViewModel (MVP aceptable, Dispatchers.IO).
  Para produccion: mover a use case o repositorio.

## Auditoria completada (2026-05-28)

- Fase 0: Baseline limpio. 57→62 tests backend, 0 fallos.
- Fase 1: RecipeRatingControllerTest — 5 tests (CRUD, conflicto, validacion, acceso cruzado).
- Fase 2: Fix SSRF validateHttpsUrl (bloquea localhost/IPs privadas). Doc Desktop tokens.
- Fase 3: Codex Android — fixes aplicados: _isRefreshing try/finally, CancellationException
  rethrow en 6 catch offline de Repositories.kt. Codex Desktop: pendiente.
- Fase 4: Icons.Filled.Close en RecipeForm (bug cosmético). 0 TODOs/printlns en codigo.
- Fase 5: Contratos API coherentes en las 3 plataformas (items/totalItems, name, /notes).
- Fase 6: Dependencias + arquitectura — COMPLETADA (Claude Code, 2026-05-28):
  * Backend: Spring Boot 3.5.14, jjwt 0.12.7, springdoc 2.8.17 — todos estables y actuales. Sin CVEs.
  * Desktop: JavaFX 21.0.2 (LTS aceptable), okhttp 4.12.0, gson 2.10.1, junit 5.10.2 — aceptables para MVP.
  * Android: security-crypto 1.1.0-alpha06 es la más reciente disponible (no existe stable 1.1.0). Aceptado.
  * Bug crítico corregido — ApiClient.java Desktop: post() carecía de Authorization header;
    cada POST autenticado generaba un round-trip 401 innecesario + llamada extra a /refresh.
  * Bug corregido — Timeouts OkHttp ausentes en Desktop (ambos clientes) y Android.
    commit 58bcf2a solo añadió shutdown(), no los timeouts.
    Ahora: connectTimeout 10s, readTimeout 30s (client principal), readTimeout 15s (refreshClient).

---

## Sprint 10 — COMPLETADO (2026-05-28)

### Sprint 10.1 — Widgets Android ✅ COMPLETADO (commit 4eab106)

- `RecipeWidget`: receta del día rotando por índice diario desde Room. Layout 4×2.
- `StockWidget`: contador de ítems críticos (caducan ≤3 días o bajo umbral). Layout 2×2.
- Ambos usan `goAsync()` + Room directo. Se actualizan cada 24h.
- Nuevas queries: `RecipeDao.findAll()`, `StockDao.findCriticalItems(threshold)`.
- Registrados en `AndroidManifest.xml` como receivers con `APPWIDGET_UPDATE`.

### Sprint 10.2 — Búsqueda global Desktop ✅ COMPLETADO (commit e91420a)

- `GlobalSearchView` (nuevo): resultados agrupados 📖 Recetas / 🧂 Stock / 📝 Notas.
  Sin llamada de red — filtra sobre caches en memoria. Hover warm-beige.
- `MainWindow`: TextField de búsqueda en sidebar; ≥2 chars activa búsqueda global;
  clic en resultado navega y pre-filtra la vista destino. Flag `navigating` evita re-entradas.
- `NoteRepository`: `SimpleCache` + `getCache()` + `updateCache()` para búsqueda global.
- `NotesView`: `FilteredList` + `filterField` encima de lista; CRUD migrado a `allNotes`.
- `RecipeListView`: `filterBy(String)` público. `StockView`: `FilteredList` + `filterField` + `filterBy()`.

### Sprint 10.3 — CRUD offline-resilient recetas Android ✅ COMPLETADO (commit 1b9292b)

- `RecipeRepository.create()`: fallo API → guarda receta + ingredientes + pasos con UUID local y syncVersion=0.
- `RecipeRepository.update()`: fallo API → guarda metadatos con syncVersion=0 (ing/pasos sin cambio hasta sync).
- `RecipeRepository.delete()`: fallo API → deleted=true + syncVersion=0 en Room.
- `SyncRepository.pushThenPull()`: incluye recetas/ingredientes/pasos pendientes en el push.
- Nuevas queries: `RecipeDao.findPendingCreate/Delete()`, `RecipeIngredientDao/RecipeStepDao.findByRecipeIds()`.

### Sprint 10.4 — Design tokens + refactor RecetasApp.kt ✅ COMPLETADO (commit b97c0cd)

**Design tokens** (`AppTokens.kt`):
- `Spacing.xxs/xs/sm/md/lg/xl/xxl` = 2/4/6/8/12/16/24 dp.
- 101 magic numbers reemplazados por tokens semánticos.

**Extracción composables** (1887 → 250 líneas en `RecetasApp.kt`):
- `SharedComposables.kt`: `EmptyStateView`, `MetaChip`.
- `RecipeScreens.kt`: `RecipeList`, `RecipeDetail`, `IngredientRow`, `StepRow`, `PhotoThumbnail`, `RatingsSection`, `StarRow`.
- `CookingScreen.kt`: `CookingScreen`.
- `RecipeFormScreen.kt`: `RecipeForm` + `IngredientDraft` + `StepDraft`.
- `StockScreens.kt`: `StockList`, `StockItemCard`, `StockDetail`, `StockForm`.
- `NotesScreens.kt`: `NotesScreen`, `NoteCard`, `NoteDetail`, `NoteForm`.

---

## Estado Git (2026-05-28)

Rama: `main` — limpio, pusheado a origin.

Commits Sprint 11:
```
624f900 feat: Sprint 11.4 — Paginación recetas Desktop (RecipeListView)  ← HEAD
663f973 feat: Sprint 11.3 — Offline-resilient lista de la compra Android
0bc5b46 feat: Sprint 11.2 — Pull-to-refresh Desktop en todas las vistas
3850bd9 feat: Sprint 11.1 — Búsqueda global Android
```

---

## Sprint 11 — COMPLETADO (2026-05-28)

### Sprint 11.1 — Búsqueda global Android ✅ COMPLETADO (commit 3850bd9)

- `MainShell`: `TopAppBar` con ícono de búsqueda. Al activar → `OutlinedTextField` en el top bar.
  Con ≥2 chars → `GlobalSearchScreen` reemplaza el contenido del tab activo.
  Al tocar resultado → navega al tab correspondiente y cierra búsqueda.
- `GlobalSearchScreen` (nuevo): resultados agrupados Recetas / Stock / Notas.
  Filtro local sobre StateFlows — sin llamada de red.
- `MainTab`: `private` → `internal` para ser accesible desde el nuevo fichero.

### Sprint 11.2 — Pull-to-refresh Desktop ✅ COMPLETADO (commit 0bc5b46)

- Patrón consistente: `Runnable onSync` en todas las vistas; "Actualizar" dispara `triggerSync()` completo.
- `RecipeListView`: constructor(onSync) + botón "Actualizar" añadido.
- `StockView`: constructor(onSync) + botón "Actualizar" en toolbar.
- `WeeklyMenuView`: constructor(onSync) + botón "Actualizar" en navBar.
- `NotesView` + `ShoppingListView`: constructor(onSync); botones existentes conectados a onSync.
- `MainWindow.showMain()`: pasa `this::triggerSync` a las 5 vistas.

### Sprint 11.3 — Offline-resilient lista de la compra Android ✅ COMPLETADO (commit 663f973)

- `ShoppingListRepository.checkItem()`: try/catch → fallback local `item.copy(checked=checked, syncVersion=0L)`.
- `SyncRepository.pushThenPull()`: incluye `shoppingListItems` pendientes vía `SyncShoppingListItemPushItemDto`.
- `Daos.kt`: `ShoppingListItemDao.findPendingCheck()` — `WHERE syncVersion=0 AND deleted=0`.

### Sprint 11.4 — Paginación recetas Desktop ✅ COMPLETADO (commit 624f900)

- `RecipeListView`: `PAGE_SIZE=30` (antes 100), campos `currentPage` + `hasMore`.
- `refresh()`: carga página 0, determina `hasMore = totalPages > 1`.
- `loadNextPage()`: append incremental al cache (no replace); botón deshabilitado mientras carga.
- `updateLoadMoreBtn()`: muestra "Cargar más (página N de …)" sólo sin filtro activo.
- `filterList()`: oculta botón "Cargar más" cuando hay búsqueda activa.

---

## Sprint 12 — PENDIENTE

Candidatos:

1. **Notificaciones caducidad Desktop** — equivalente al WorkManager Android; alerta de stock próximo a caducar.
2. **Offline-resilient favoritos** — `toggleFavorite()` con fallback local (patrón stock/notas).
3. **Paginación notas/stock Desktop** — las vistas cargan todo de una vez; añadir carga incremental.
4. **Modo manos libres CookingScreen Android** — control por gestos/volumen para avanzar pasos.
5. **Exportar/compartir receta** — generar texto o PDF de una receta para compartir fuera de la app.
6. **Pull-to-refresh Desktop** — sincronización manual desde la UI.
