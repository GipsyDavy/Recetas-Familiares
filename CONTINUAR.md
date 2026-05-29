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
- funcionar en Android, Desktop e iOS;
- soportar sincronizacion offline-first;
- mantener una experiencia calida, moderna, emocional y premium.

Plataformas objetivo:

- Backend Spring Boot + MySQL;
- Android nativo Kotlin + Compose;
- Desktop JavaFX;
- iOS Kotlin Multiplatform + Compose Multiplatform (en desarrollo);
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
- Preparar Android/Desktop/iOS para sincronizacion offline.
- iOS usa KMP + Compose Multiplatform (carpeta `ios/` ya creada en la raiz).

---

## Entorno de desarrollo

### Java
- Ejecutable: `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`
- Version activa: Java 26

### Gradle (Android)
- Instalacion global: `C:\tmp\tools\gradle-9.5.1\bin\gradle.bat`
- `gradlew` disponible en `android/` (añadido Sprint 14, commit d79e89a) — el IDE lo usa
- Compilar APK: `./gradlew assembleDebug` (o `gradle assembleDebug` global) desde `android/`

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

Estado: **62 tests, 0 fallos.**

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

## Android Kotlin + Compose — SPRINT 19 COMPLETO (2026-05-29)

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

### Pantallas implementadas (Sprint 1-16 + UI sprint)
- **`LoginScreen`** — ícono brand circular, tipografía centrada, botón "Entrar →"
- `TopAppBar` con búsqueda global unificada → `GlobalSearchScreen` (Recetas/Stock/Notas)
- **`RecipeListScreen`** — tarjetas visuales con gradiente + placeholder icon + chips (⏱ tiempo, dificultad, porciones); paginación; búsqueda; FilterChips dificultad; pull-to-refresh; FAB "+"
- **`RecipeDetailScreen`** — `←` IconButton back; ❤️ favorito; ⋮ menú (Compartir, Modo Cocina, Editar, Eliminar); fotos carrusel; valoraciones; ExtendedFAB "▶ Cocinar"
- `RecipeForm` (SegmentedButton dificultad, filas dinámicas ingredientes/pasos)
- `CookingScreen` (paso a paso, temporizador countdown, keep screen on, **volumen ↑↓ cambia paso**)
- **`StockScreen`** — Sort toggle caducidad, FAB crear, CRUD inline; `StockDetail` con `←` IconButton + ✏ Editar / 🗑 Eliminar con ícono
- **`ShoppingListScreen`** — `←` IconButton back; check offline-resilient; tachado en ítems marcados; botón "Compartir"
- **`NotesScreen`** — `←` IconButton back; `+` IconButton crear; `NoteDetail` con ✏ Editar / 🗑 Eliminar con ícono; CRUD completo; búsqueda; empty states
- `GlobalSearchScreen` (resultados agrupados entre tabs)
- **`MenuScreen`** — 5º tab; nav ← → semanas; CRUD assign/remove; tap comida → "Ver receta" / "Eliminar"
- `Widgets`: `RecipeWidget` (receta del día, 4×2) + `StockWidget` (ítems críticos, 2×2)
- Bottom Navigation: **5 tabs** (RECIPES, STOCK, SHOPPING, NOTES, MENU)
- Snackbar feedback en todas las mutaciones
- **SyncWorker pushThenPull: 7 tipos**
- **Notificaciones caducidad**: HOY (PRIORITY_HIGH, ID 1001) + esta semana (PRIORITY_DEFAULT, ID 1002)

### RecetasApi.kt — endpoints implementados
- login, families
- recipes (list paginado, detail, create, update, delete) + ingredientes + pasos
- stockItems (list, create, update, delete)
- photos (list, upload multipart, delete)
- ratings (create, update, delete, list)
- pullSync, pushSync
- addFavorite, removeFavorite
- updateShoppingListItem
- createNote, updateNote, deleteNote (rutas: `/notes`)
- **assignMenuItem, removeMenuItem** (menú semanal, Sprint 15.1)

Build: `./gradlew assembleDebug` desde `android/` — BUILD SUCCESSFUL

---

## Desktop JavaFX — SPRINT 20 COMPLETO (2026-05-29)

Stack: Java 21 + JavaFX 21.0.2 + OkHttp 4.12.0 + Gson 2.10.1 + Maven.

Fat JAR: 13.3 MB. SSL fix: `desktop/.mvn/jvm.config` con Windows-ROOT truststore.

### Pantallas implementadas (Sprint 1-19)
- `LoginView`
- `DashboardView` — GridPane 2 columnas: recetas recientes + stock expirando + acciones rápidas (Stock / Notas); hover ScaleTransition cards
- `RecipeListView` — SplitPane filtrable, búsqueda, paginación 30/pág + "Cargar más", botón "Actualizar"; **skeleton loading shimmer** (Sprint 19)
- `RecipeDetailView` — ingredientes, pasos, fotos async, Editar, Eliminar, Modo Cocina, **"📋 Copiar"**, **"💾 Exportar" → .txt**
- `RecipeFormDialog` — modal `forCreate()` / `forEdit()`; ScaleTransition apertura; **playConfirm()** al guardar (Sprint 19)
- `CookingView` — Stage maximizado, paso a paso, temporizador JavaFX Timeline; ←/→ teclado navega pasos; **playTimerComplete()** al terminar (Sprint 19)
- `StockView` — TableView CRUD, búsqueda, paginación client-side PAGE_SIZE=50, "Cargar más", ContextMenu, tooltips, Ctrl+N/Supr/Enter; **playDelete()** al eliminar (Sprint 19)
- `WeeklyMenuView` — calendario 8x5, nav semanas, CRUD assign/remove, botón "Actualizar"
- `ShoppingListView` — ítems con check, **"💾 Exportar" → .txt**, botón "Actualizar"
- `NotesView` — SplitPane lista + editor inline, búsqueda, paginación 30/pág, botón "Actualizar"
- `GlobalSearchView` — resultados agrupados Recetas/Stock/Notas desde sidebar
- `ExpiryNotificationService` — toast bottom-right; **TranslateTransition** entrada desde abajo (Sprint 19)
- `SoundPlayer` (nuevo Sprint 19) — `playConfirm/Delete/TimerComplete` via `javax.sound.sampled`; desactivado por defecto (`Preferences`)

### Sidebar completa
Búsqueda global | Inicio | Recetas | Stock | Menú semanal | Lista de la compra | Notas familiares

mvn compile — BUILD SUCCESS. Ejecutar: `mvn javafx:run -Dapi.base.url=http://localhost:8080/`

---

## Sprint 25A — SISTEMA DE TEMAS COMPLETO (2026-05-29)

### Objetivo
Sistema de 10 temas × 2 modos (Claro/Oscuro) = 20 esquemas de color en las 3 plataformas.
Usuario puede elegir tema y modo desde la UI. Preferencia persistida localmente.

### Android — COMPLETADO ✅
- **`AppTheme.kt`** (nuevo): enum `AppTheme` (10 valores), enum `ThemeMode`, extensiones `lightColors()` / `darkColors()` con todos los roles Material3, `AppTypography` con escala tipográfica completa (13 tokens).
- **`ThemePreference.kt`** (nuevo): DataStore<Preferences> para persistir tema + modo.
- **`Theme.kt`** (modificado): acepta `AppTheme` + `ThemeMode`; selecciona colors y aplica `AppTypography`.
- **`AppContainer.kt`** (modificado): añadido `val themePreference = ThemePreference(context)`.
- **`RecetasViewModel.kt`** (modificado): `selectedTheme: StateFlow<AppTheme>`, `themeMode: StateFlow<ThemeMode>`, `setTheme()`, `setThemeMode()`.
- **`MainActivity.kt`** (modificado): lee estado del ViewModel y pasa a `RecetasTheme`.
- **`ThemePickerDialog.kt`** (nuevo): diálogo `AlertDialog` con grid 5-columnas de swatches de color + chips Claro/Oscuro/Sistema.
- **`RecetasApp.kt`** (modificado): botón Paleta en TopAppBar que abre `ThemePickerDialog`.
- **`build.gradle.kts`** (modificado): añadida dependencia `datastore-preferences:1.1.4`.
- Compilación: `./gradlew assembleDebug` — BUILD SUCCESSFUL ✅

### Desktop — COMPLETADO ✅
- **`ThemeManager.java`** (nuevo): singleton, carga CSS de temas vía `Java Preferences API`, método `applyTheme(AppTheme, ThemeMode)`, detección de dark mode del sistema (Windows registry).
- **`style.css`** (refactorizado): variables looked-up JavaFX en `.root` (`recetas-primary`, `recetas-bg`, etc.); todos los hexadecimales reemplazados por referencias a variables.
- **`themes/` directorio** (nuevo): 20 archivos CSS, uno por tema×modo (bosque-light/dark, terracota-light/dark, ocaso-light/dark, mediterraneo-light/dark, lavanda-light/dark, oliva-light/dark, canela-light/dark, menta-light/dark, frambuesa-light/dark, noche-verano-light/dark). Cada fichero solo define `.root { recetas-*: #valor; ... }`.
- **`MainWindow.java`** (modificado): llama `ThemeManager.getInstance().attach(scene)` al crear la escena; `showPreferencesDialog()` ampliado con `RadioButton` modo y `ComboBox` tema.
- Compilación: `mvn compile` — BUILD SUCCESS ✅

### iOS KMP — COMPLETADO (código listo, build pre-existente no compila en Windows) ✅
- **`theme/AppTheme.kt`** (nuevo, commonMain): mismas definiciones que Android — enum `AppTheme`, `ThemeMode`, `lightColors()`, `darkColors()`, `AppTypography`.
- **`theme/ThemePreference.kt`** (nuevo, commonMain): `expect class ThemePreference()` con `selectedTheme` y `themeMode`.
- **`theme/ThemePreference.ios.kt`** (nuevo, iosMain): `actual class ThemePreference` usando `NSUserDefaults`.
- **`App.kt`** (modificado): sustituye `MaterialTheme {}` por `MaterialTheme(colorScheme, typography)` usando el tema seleccionado; crea `ThemePreference` y expone estado via `mutableStateOf`.
- **`ui/SettingsScreen.kt`** (nuevo, commonMain): pantalla de ajustes con grid de swatches + chips de modo + botón logout.
- **`ui/MainTabScreen.kt`** (modificado): acepta `selectedTheme`, `themeMode`, `onThemeChange`, `onModeChange`; añade tab 6 "Ajustes" con icono `Settings`.
- Nota: build iOS en Windows falla por incompatibilidad pre-existente SQLDelight plugin + Gradle 9.5.1 (error `DefaultArtifactPublicationSet`). No relacionado con estos cambios.

### Estado Git (2026-05-29)

Rama: `main`. Sprint 25A completo, sin commit aún.

Archivos modificados: `build.gradle.kts`, `MainActivity.kt`, `AppContainer.kt`, `RecetasApp.kt`, `RecetasViewModel.kt`, `Theme.kt`, `MainWindow.java`, `style.css`, `App.kt`, `MainTabScreen.kt`.
Archivos nuevos: `AppTheme.kt` (x2), `ThemePreference.kt` (x2), `ThemePreference.ios.kt`, `ThemePickerDialog.kt`, `ThemeManager.java`, `SettingsScreen.kt`, `themes/` (20 CSS), `Interfaz.md`.

Commits recientes:
```
a1248e8 docs: Sprint 20 completado — actualizar CONTINUAR.md, Resumen.md y preparar Sprint 21  ← HEAD
6963f04 feat: Sprint 20 completo — iOS sync incremental + Desktop polish (Multi-IA)
847c528 feat: Sprint 20 iOS — ShoppingListScreen + MenuScreen implementados
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

6. Continuar con Sprint 20 (ver candidatos al final de este documento).

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

Commits Sprint 12:
```
139d6df feat: Sprint 12.4 — Exportar/copiar receta al portapapeles (Desktop)  ← HEAD
1b23583 feat: Sprint 12.3 — Paginación notas Desktop (NotesView)
ea78874 feat: Sprint 12.2 — Notificaciones caducidad Desktop (toast JavaFX)
6aaf406 feat: Sprint 12.1 — Offline-resilient favoritos Android
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

## Sprint 12 — COMPLETADO (2026-05-28)

### Sprint 12.1 — Offline-resilient favoritos Android ✅ COMPLETADO (commit 6aaf406)

- `FavoriteRepository.toggle()`: try/catch en rama add y rama remove.
  - Add offline: `FavoriteRecipeEntity` con UUID local, `syncVersion=0L`, `recipeTitle=null`.
  - Remove offline: `existing.copy(deleted=true, syncVersion=0L)`.
- `SyncRepository.pushThenPull()`: incluye favoritos pendientes vía `SyncFavoriteRecipePushItemDto`.
  SyncWorker ahora empuja **7 tipos**: recetas, ingredientes, pasos, stock, shopping, favoritos, notas.
- `Daos.kt`: `FavoriteRecipeDao.findPendingCreate/Delete()`.

### Sprint 12.2 — Notificaciones caducidad Desktop ✅ COMPLETADO (commit ea78874)

- `ExpiryNotificationService` (nuevo): filtra stock ≤3 días y muestra toast `Stage` transparente
  en la esquina bottom-right. Auto-dismiss 5s. Clic cierra inmediatamente.
  Hasta 4 ítems con etiqueta "hoy/mañana/N días"; "… y N más" si hay más.
  Paleta del proyecto (#F6E7D8/#D4A574). No requiere `java.desktop`.
- `MainWindow`: `showIfNeeded()` llamado tras `triggerInitialSync()` y cada `triggerSync()`.

### Sprint 12.3 — Paginación notas Desktop ✅ COMPLETADO (commit 1b23583)

- `NoteRepository.loadPage(page, size)` añadido. `loadAll()` se mantiene para sync/cache.
- `NotesView`: `PAGE_SIZE=30`, campos `currentPage`/`hasMore`.
  `refresh()` carga página 0. `loadNextPage()` appenda a `allNotes` (FilteredList se actualiza).
  Botón "Cargar más notas" oculto al filtrar o sin más páginas.

### Sprint 12.4 — Exportar/copiar receta Desktop ✅ COMPLETADO (commit 139d6df)

- `RecipeDetailView`: botón "📋  Copiar" en el action bar.
- `copyToClipboard()`: construye texto con secciones (🍳 título, meta, 🥗 ingredientes, 👨‍🍳 pasos).
  Usa `Clipboard.getSystemClipboard()` vía `ClipboardContent`. Confirmación en `statusLabel`.
- `currentIngredients` almacenado en `renderContent()` para tener los datos en el momento de copiar.

---

## Sprint 13 — COMPLETADO (2026-05-28)

### Sprint 13.1 — Compartir receta Android ✅ COMPLETADO (commit d6063b8)

- `RecipeScreens.kt`: opción "Compartir" en DropdownMenu de RecipeDetail.
- `shareRecipe()`: construye texto con 🍳 título, meta, 🥗 ingredientes y 👨‍🍳 pasos.
  Lanza `Intent.ACTION_SEND` (text/plain) con `Intent.createChooser`.

### Sprint 13.2 — Modo manos libres CookingScreen Android ✅ COMPLETADO (commit 8ba01b7)

- `CookingScreen.kt`: `FocusRequester` + `focusable()` + `onKeyEvent` en Surface.
  Volumen ↑ → siguiente paso (o finaliza si es el último).
  Volumen ↓ → paso anterior.
  El evento se consume (no cambia volumen del sistema).
  Timer se detiene automáticamente al cambiar paso (via `remember(currentIndex)`).

### Sprint 13.3 — Dashboard acciones rápidas Desktop ✅ COMPLETADO (commit 94bf99a)

- `DashboardView.java`: constructor ampliado con `onNavigateStock` y `onNavigateNotes`.
  Botones "Stock familiar" y "Notas familiares" en columna derecha.
- `MainWindow.java`: pasa `() -> navigateTo("stock")` y `() -> navigateTo("notes")`.

---

## Sprint 14 — COMPLETADO (2026-05-29)

### Sprint 14.1 — Historial de menús Android ✅ COMPLETADO (Claude Code)

- `MenuScreen.kt` (nuevo): LazyColumn con nav ← → semanas, cards por día, MealRow, empty state.
  Filtra `menuItems` (Room) por semana. Muestra "Esta semana / Semana pasada / Hace N semanas / Próxima semana".
- `RecetasViewModel.kt`: `menuItems` StateFlow via `database.menuItemDao().observeMenuItems()`.
- `RecetasApp.kt`: 5º tab MENU (CalendarMonth icon). `isRefreshing` colectado en MainShell.
- Build: `gradle assembleDebug` — BUILD SUCCESSFUL.

### Sprint 14.4 — Compartir lista de la compra Android ✅ COMPLETADO (Claude Code)

- `RecetasApp.kt`: botón "Compartir" (OutlinedButton) en `ShoppingListDetail` header.
  `shareShoppingList()`: construye texto con ✅/☐ + cantidad + unidad. Lanza `Intent.ACTION_SEND`.
- Build: incluido en el mismo BUILD SUCCESSFUL de 14.1.

### Sprint 14.5 — Notificaciones caducidad Android mejoradas ✅ COMPLETADO (Claude Code)

- `ExpiryNotificationWorker.kt`: `EXPIRY_DAYS_AHEAD` extendido a 7.
  Dos grupos: `todayItems` (daysLeft==0) con PRIORITY_HIGH y NOTIFICATION_ID_TODAY=1001;
  `weekItems` (1-7 días) con PRIORITY_DEFAULT y NOTIFICATION_ID_WEEK=1002.
  Cada grupo dispara su propia notificación solo si no está vacío.
- Build: incluido en el mismo BUILD SUCCESSFUL.

### Sprint 14.2 — Paginación stock Desktop ✅ COMPLETADO (Codex, commit 6ce1ba8)

- `StockView.java`: paginación client-side PAGE_SIZE=50. `displayItems` ObservableList + `refreshDisplay()`.
  Botón "Cargar más (N de total)". Se oculta al filtrar. Se resetea al cambiar filtro o recargar.
  `filteredItems` listener resetea `currentLimit` automáticamente en cada cambio.
  `refreshDisplay()` llamado en create/edit/delete y `refresh()`.
- mvn compile — BUILD SUCCESS.

### Sprint 14.3 — Exportar receta Desktop a archivo ✅ COMPLETADO (Codex, commit 6ce1ba8)

- `RecipeDetailView.java`: botón "💾 Exportar" en action bar entre Copiar y Editar.
  `exportToFile()`: FileChooser .txt + `Files.writeString` UTF-8. Feedback en `statusLabel`.
  Imports: `java.nio.charset.StandardCharsets`, `java.nio.file.Files`.
- mvn compile — BUILD SUCCESS.

---

## Sprint 15 — COMPLETADO (2026-05-29)

### Sprint 15.1 — CRUD menú semanal Android ✅ COMPLETADO (commit e67d734)

- `AssignMenuItemRequestDto` en ApiDtos.kt.
- `assignMenuItem()` / `removeMenuItem()` en RecetasApi.kt (endpoint `/menu-items`).
- `MenuItemRepository` en Repositories.kt: assign() guarda en Room; remove() soft-delete.
- `AppContainer`: menuItemRepository añadido.
- `RecetasViewModel`: assignToMenu() + removeFromMenu() con userMessage feedback.
- `MenuScreen.kt` (reescrito): botón "+" por día → AssignMenuDialog (filtro recetas + tipo comida).
  Tap en comida → AlertDialog "Ver receta" / "Eliminar del menú".
  Parámetros nuevos: recipes, onAssignToMenu, onRemoveFromMenu, onNavigateToRecipe.

### Sprint 15.2 — Navegar a receta desde MenuScreen ✅ COMPLETADO (commit e67d734)

- `RecipeScreens.kt`: `RecipeList` acepta `openRecipeId: String?` + `onRecipeOpened: () -> Unit`.
  `LaunchedEffect(openRecipeId, recipes)` abre RecipeDetail automáticamente al recibir un ID.
- `RecetasApp.kt`: `navigateToRecipeId` state. "Ver receta" en menú → tab RECIPES + ID pasado.

### Sprint 15.3 — Filtros de dificultad en RecipeList Android ✅ COMPLETADO (commit e67d734)

- `RecipeScreens.kt`: `FilterChip` row (Fácil/Media/Difícil) encima de la lista de recetas.
  Toggle: tap activa, tap de nuevo desactiva. Se combina con búsqueda por texto.
  `difficultyFilter` state + combinación con `query` en el `filtered` val.

### Sprint 15.4 — Exportar lista de la compra Desktop ✅ COMPLETADO (Codex, commit 2edfc88)

- `ShoppingListView.java`: botón "💾 Exportar" junto a "Actualizar".
  `exportToFile()`: carga ítems reales con `loadItems(selected.id())` en hilo virtual.
  `saveListToFile()`: FileChooser .txt + `Files.writeString` UTF-8.
  Formato: 🛒 nombre, ☐ pendientes, ✅ completados con cantidad y unidad.
  mvn compile — BUILD SUCCESS.

### Sprint 15.5 — Ordenar stock por caducidad Android ✅ COMPLETADO (commit e67d734)

- `StockScreens.kt`: icono Sort en header de StockList. Toggle `sortByExpiry: Boolean`.
  Ordena por `expiresAt` ASC cuando activo (null al final como "9999-99-99").
  Icono coloreado (primary) cuando activo para indicar estado visual.

---

## Sprint 16 — COMPLETADO (2026-05-29)

### Sprint 16.1 — Gradle KMP setup iOS ✅

- `ios/settings.gradle.kts`: proyecto `RecetasFamiliaresIOS`, include `:composeApp`.
- `ios/gradle/libs.versions.toml`: kotlin 2.0.21, compose 1.7.0, ktor 3.0.3, coroutines 1.9.0.
- `ios/gradle/wrapper/gradle-wrapper.properties`: distribucion local (`file:///C:/tmp/tools/gradle-9.5.1-bin.zip`).
- `ios/composeApp/build.gradle.kts`: targets `iosX64`, `iosArm64`, `iosSimulatorArm64`. Framework estatico `ComposeApp`.

### Sprint 16.2 — Core iOS ✅

- `ios/iosApp/iosApp/iosApp.swift`: `@main iOSApp: App` (SwiftUI entry point).
- `ios/iosApp/iosApp/ContentView.swift`: `ComposeView: UIViewControllerRepresentable` → `MainViewControllerKt.MainViewController()`.
- `ios/composeApp/src/iosMain/.../MainViewController.kt`: `ComposeUIViewController { App() }`.
- `ios/composeApp/src/commonMain/.../core/SessionStore.kt`: clase `expect`.
- `ios/composeApp/src/iosMain/.../core/SessionStore.ios.kt`: `actual` con `NSUserDefaults` (MVP; Keychain en Sprint 17+).
- `ios/composeApp/src/commonMain/.../App.kt`: raiz Compose con DI manual (SessionStore, ApiClient, repos) y navegacion login/main.

### Sprint 16.3 — Ktor network layer ✅

- `network/ApiDtos.kt`: 10 DTOs `@Serializable` (LoginRequest, AuthResponse, User, Family, Recipe, Stock, FamilyNote, MenuItem, ShoppingList, ShoppingListItem, PageDto<T>). Alineados con contratos del backend.
- `network/ApiClient.kt`: `HttpClient(Darwin)` con `ContentNegotiation`, `defaultRequest` URL + auth header JWT automatico.

### Sprint 16.4 — Auth flow ✅

- `auth/AuthRepository.kt`: `login()` POST Ktor → sesion; `logout()`.
- `auth/LoginScreen.kt`: Compose M3, coroutines, estados loading/error.

### Sprint 16.5 — RecipeListScreen iOS basica ✅

- `recipes/RecipeRepository.kt`: `loadRecipes()` paginado via Ktor GET.
- `recipes/RecipeListScreen.kt`: LazyColumn estados loading / error / empty / datos. `difficultyLabel()` localizado.

Commit: `d0bef39`

### Para compilar en macOS (instrucciones para cuando tengas un Mac)

```bash
# Desde ios/ en macOS con Xcode instalado:
cd ios/
./gradlew :composeApp:assembleXCFramework   # Genera el .xcframework
# Android Studio abre ios/ como proyecto Gradle independiente para editar Kotlin
# Xcode abre ios/iosApp/ para ejecutar en Simulator/dispositivo
```

### Limitaciones documentadas y aceptadas

- Compilacion binaria iOS (.ipa): requiere macOS + Xcode. En Windows solo se edita el Kotlin.
- `SessionStore.ios.kt` usa NSUserDefaults (no cifrado). Migrar a Keychain en Sprint 17+.
- Botones de volumen en CookingScreen: solo Android, no portable a iOS.
- Android Widgets: sin equivalente directo en KMP; WidgetKit requiere Swift.
- Desktop JavaFX: permanece independiente, no migra a KMP.

---

## Sprint UI-Android — Rediseño visual Android ✅ COMPLETADO (2026-05-29)

Mejoras visuales sobre Android sin cambios de arquitectura, contratos API ni sincronización.

### Cambios aplicados

**LoginScreen**
- Ícono circular brand 88dp (`primaryContainer`) centrado arriba.
- Tipografía: `headlineMedium` + `bodyMedium` muted, centrados.
- Botón "Entrar →" con `Icons.AutoMirrored.Filled.ArrowForward` de trailing.

**RecipeCard (composable nuevo en RecipeScreens.kt)**
- Reemplaza `Card { ListItem(...) }` por card visual full-width.
- Header 152dp: fondo `secondaryContainer` + `Icons.Outlined.Restaurant` semitransparente.
- `Brush.verticalGradient` oscuro → título y descripción en blanco overlay bottom-start.
- Fila de `MetaChip` bajo header: ⏱ Xm, dificultad, N porciones.
- `CardDefaults.cardElevation(2.dp)`, `MaterialTheme.shapes.large`.

**Navegación — texto → ícono**
- `Button("← Volver")` → `IconButton(Icons.AutoMirrored.Filled.ArrowBack)` en: RecipeDetail, ShoppingListDetail, StockDetail, NoteDetail.
- `Button("Actualizar")` → `IconButton(Icons.Filled.Refresh)` en: RecipeList, ShoppingList, Stock, Notes.
- `Button("Nueva nota")` → `IconButton(Icons.Filled.Add)` en header de NotesScreen.

**FABs**
- Bug fix: FAB "Nueva receta" usaba `Icons.Filled.Favorite` → corregido a `Icons.Filled.Add`.
- Nuevo `ExtendedFloatingActionButton("▶ Cocinar", Icons.Filled.PlayArrow)` visible cuando `selectedRecipe != null && !cookingMode`.

**Interacciones**
- Shopping items: `TextDecoration.LineThrough` cuando `item.checked == true`.
- StockDetail y NoteDetail: botones Editar/Eliminar con ícono leading (`Icons.Filled.Edit` / `Icons.Filled.Delete`).

### Archivos modificados
- `RecetasApp.kt`
- `RecipeScreens.kt`
- `StockScreens.kt`
- `NotesScreens.kt`

Build: `gradle assembleDebug` — **BUILD SUCCESSFUL** (1 warning deprecation Sort pre-existente).
Verificado en emulador Pixel_9_Pro API 36: LoginScreen, RecipeList, RecipeDetail+FAB Cocinar, ShoppingList+tachado, StockDetail+iconos.

> ⚠️ **Pendiente commit y push**: los cambios están en el working tree sin commitar. Hacer commit antes de continuar mañana.

---

## Sprint 17 — UX Premium ✅ COMPLETADO (2026-05-29) — commit 9fa25c3

### Sprint 17 Android (Claude Code) ✅

- `RecetasApp.kt`: `TooltipBox + PlainTooltip` en botón Buscar TopAppBar; `semantics { heading() }` en título.
- `RecipeScreens.kt`: `Crossfade` lista↔empty-state; `Modifier.animateItem()` en LazyColumn items; `modifier` param en RecipeCard.
- `StockScreens.kt`: `SwipeToDismissBox` EndToStart + `AlertDialog` confirmación + haptic `LongPress`; `TooltipBox` Sort/Refresh.
- `NotesScreens.kt`: `SwipeToDismissBox` + `AlertDialog` + haptic `LongPress` al eliminar.
- `CookingScreen.kt`: haptic en cambio de paso (volumen + botones Anterior/Siguiente/Finalizar) y al terminar timer.

### Sprint 17 Desktop (Codex) ✅

- `MainWindow.java`: `FadeTransition` sidebar 180ms, status bar Label, Ctrl+F global, `setCenterWithFade()`.
- `RecipeFormDialog.java` + `StockFormDialog.java`: `ScaleTransition` 0.95→1.0 al abrir (200ms EaseOut).
- `StockView.java`: `ContextMenu`, tooltips Nuevo/Editar/Eliminar, atajos Delete/Enter/Ctrl+N.
- `CookingView.java`: ←/→ teclado navega pasos; `prevStep()`/`nextStep()` extraídos.
- `DashboardView.java`: hover `ScaleTransition` recipe cards (1.0→1.02, 100ms).

### Pendiente Sprint 17 (Prioridad Media — COMPLETADO en Sprint 19)

---

## Sprint 19 — UX Polish + SQLDelight iOS + Sonidos Desktop ✅ COMPLETADO (2026-05-29) — commit 8853bd0

### Sprint 19 Android (Claude Code) ✅

- `RecipeScreens.kt`: `ModalBottomSheet` reemplaza `DropdownMenu` en menú ⋮ RecipeDetail.
- `RecipeScreens.kt`: `AnimatedContent` timer CookingScreen (slide+fade por segundo).
- `RecipeScreens.kt`: `SkeletonRecipeCard` shimmer en primera carga (4 cards pulsantes con `rememberInfiniteTransition`).
- `RecipeScreens.kt`: `animateColorAsState` en FilterChips dificultad via `FilterChipDefaults.filterChipColors`.
- `RecipeScreens.kt`: `animateContentSize()` en `RatingsSection` al abrir/cerrar formulario de valoración.
- `StockScreens.kt`: `animateColorAsState` en color badge caducidad (`expiryColor`).
- `CookingScreen.kt`: `AnimatedContent` en texto del timer (slide vertical por segundo).

### Sprint 19 Desktop (Codex) ✅

- `ExpiryNotificationService.java`: `TranslateTransition` entrada toast desde abajo (Y+60→0, 220ms EaseOut).
- `RecipeListView.java`: skeleton loading con `StackPane` + 5 placeholders + shimmer `Timeline`.
- `SoundPlayer.java` (nuevo): `playConfirm/Delete/TimerComplete` via `javax.sound.sampled` onda sinusoidal.
  Sonidos desactivados por defecto (`Preferences`), playback en hilos virtuales.
- Sonidos integrados en `CookingView.java`, `RecipeFormDialog.java`, `StockFormDialog.java`, `StockView.java`.

### Sprint 19 iOS (Claude Code + diseño Gemini) ✅

- `libs.versions.toml` + `build.gradle.kts`: SQLDelight 2.0.2 plugin + deps (runtime, coroutines, native-driver).
- `AppDatabase.sq` (nuevo): tablas `recipes` + `stock_items` con queries insertOrReplace + selectAll.
- `DatabaseDriverFactory.kt` (expect) + `DatabaseDriverFactory.ios.kt` (actual, NativeSqliteDriver).
- `RecipeRepository.kt`: cache offline — API→BD→devuelve; fallo API→devuelve desde BD.
- `StockRepository.kt`: mismo patrón cache offline.
- `App.kt` + `MainTabScreen.kt`: `DatabaseDriverFactory` instanciado en App y pasado a repos.
- `HapticFeedback.kt` (expect) + `HapticFeedback.ios.kt` (actual): UIImpactFeedbackGenerator / UISelectionFeedbackGenerator / UINotificationFeedbackGenerator.
- `RecipeListScreen.kt`, `NotesScreen.kt`: `rememberHapticFeedback()` + `haptic.selection()` al tocar card.
- `StockScreen.kt`: `SwipeToReveal` per item via `pointerInput + Animatable` (commonMain, sin APIs Android).

---

## Sprint 18 — iOS Expansion ✅ COMPLETADO (2026-05-29) — commit 9fa25c3

### Sprint 18.1 — TabView 5 tabs iOS ✅

- `App.kt`: actualizado para usar `MainTabScreen` con `onLogout`.
- `ui/MainTabScreen.kt` (nuevo): 5 tabs `NavigationBar` (Recetas, Stock, Lista, Notas, Menú). Shopping y Menú son placeholders para Sprint 19.

### Sprint 18.2 — StockScreen iOS ✅

- `stock/StockRepository.kt` (nuevo): `loadStockItems()` paginado via Ktor GET `/api/v1/families/{fid}/stock-items`.
- `stock/StockScreen.kt` (nuevo): `LazyColumn` con estados loading/error/empty/datos. Badge bajo stock. Formato cantidad+unidad+caducidad.

### Sprint 18.3 — NotesScreen iOS ✅

- `notes/NoteRepository.kt` (nuevo): `loadNotes()` via Ktor GET `/api/v1/families/{fid}/notes`.
- `notes/NotesScreen.kt` (nuevo): `LazyColumn` M3 con pin emoji, preview 80 chars, estados completos.

### Sprint 18.4 — Keychain SessionStore iOS ✅

- `core/SessionStore.ios.kt`: reemplaza NSUserDefaults por Keychain (`SecItemAdd`/`SecItemCopyMatching`/`SecItemDelete`) via `NSMutableDictionary`. Requiere `@OptIn(ExperimentalForeignApi::class)`.
- Servicio: `com.gipsybuho.recetasfamiliares`. Compilación binaria verificable solo en macOS+Xcode.

### iOS pendiente Sprint 19

- Shopping y Menú screens iOS (StockScreen pattern).
- ~~SQLDelight offline cache (recipes + stock).~~ ✅ Sprint 19
- ~~Hápticos `expect/actual` (UIImpactFeedbackGenerator).~~ ✅ Sprint 19
- ~~SwipeToReveal items lista.~~ ✅ Sprint 19
- ShoppingListScreen + MenuScreen iOS (placeholder → implementación real).
- Sync incremental SQLDelight (pull y push desde iOS).

---

## Sprint 18 (prev) — iOS Expansion original plan

### Prioridad Alta

#### Android (Compose)

- **Tooltips** — `TooltipBox + PlainTooltip` en todos los botones de `TopAppBar` (Buscar, Sort, Refresh, etc.).
- **AnimatedVisibility** en todas las transiciones show/hide de paneles (forms, details, filtros expandibles).
- **animateItemPlacement()** en `LazyColumn` al añadir/eliminar items (deslizamiento suave, no salto brusco).
- **Crossfade** entre estado loading → contenido en `RecipeList`, `StockList`, `NotesScreen`.
- **SwipeToDismiss** en listas de notas y stock items (swipe izquierda → eliminar con confirmación).
- **Haptic feedback** en acciones clave:
  - `HapticFeedbackType.LongPress` al mantener pulsado un item.
  - Vibración suave (1 pulso) en `CookingScreen` al cambiar de paso (via `LocalHapticFeedback`).
  - Vibración doble cuando el timer llega a cero.
  - `HapticFeedbackType.Confirm` al guardar receta/stock/nota con éxito.
- **contentDescription** exhaustivo en todos los iconos que aún no lo tienen.
- **semantics { heading() }** en todos los títulos de sección para TalkBack.
- **Focus order explícito** en formularios (`RecipeForm`, `StockForm`, `NoteForm`).

#### Desktop (JavaFX)

- **Tooltip en TODOS los botones** — cualquier botón sin label visible necesita tooltip con delay 400ms. Formato: `"Guardar (Ctrl+S)"`, `"Eliminar (Supr)"`, `"Buscar (Ctrl+F)"`.
- **Keyboard shortcuts**:
  - `Ctrl+N` → nueva receta / nuevo stock item (según vista activa)
  - `Ctrl+F` → foco en campo de búsqueda
  - `Ctrl+S` → guardar formulario abierto
  - `Escape` → cerrar modal / volver a lista
  - `Supr` → eliminar item seleccionado (con confirmación)
  - `←/→` en `CookingView` → paso anterior/siguiente
- **FadeTransition** al cambiar de vista en el sidebar (250ms).
- **ScaleTransition** al abrir modales (`RecipeFormDialog` crece desde 0.95x → 1.0x, 200ms EaseOut).
- **Hover ScaleTransition** en recipe cards del Dashboard (1.0 → 1.02x, 100ms).
- **SequentialTransition** al eliminar item de tabla (FadeOut 150ms → colapso 150ms).
- **Status bar** inferior contextual: `"3 recetas filtradas"`, `"Stock cargado"`, `"Cambios guardados"`.
- **ContextMenu** click derecho en tabla de stock y lista de recetas.

### Prioridad Media

#### Android

- **animateContentSize()** en cards de receta al expandirse.
- **spring()** physics en el FAB al aparecer/desaparecer al hacer scroll.
- **AnimatedContent** en el temporizador de `CookingScreen` (cambios de número animados).
- **ModalBottomSheet** para el menú ⋮ de `RecipeDetail` (más táctil que `DropdownMenu`).
- **Skeleton loading (shimmer)** en `RecipeListScreen` mientras carga (3-5 cards placeholder).
- **animateColorAsState** en chips de dificultad y badges de caducidad del stock.
- **RichTooltip** en el botón Sort del stock (explica estado activo/inactivo).
- **Swipe left/right** en `CookingScreen` para navegar pasos (alternativa táctil a botones).
- **SoundPool** para tick del temporizador en `CookingScreen` (opcional, toggle en preferencias).
- **Onboarding de primera vez**: 3 pantallas explicando tabs principales (se muestra una sola vez con `SharedPreferences`).

#### Desktop

- **Skeleton placeholders** en `RecipeListView` mientras carga (rectángulos animados con CSS animatedfill).
- **TranslateTransition** en notificaciones `ExpiryNotificationService` (entra deslizando desde abajo-derecha).
- **AudioClip sonidos desactivables** en preferencias:
  - Confirmar guardado: "pop" suave.
  - Eliminar: tono neutro discreto.
  - `CookingView` timer completado: acorde corto.
  - Notificación caducidad: tono amable, no alarmante.

#### iOS

- **spring()** y **AnimatedVisibility** en todas las transiciones (ya disponibles en Compose Multiplatform).
- **SwipeToReveal** en items de lista (acciones inline al deslizar).
- **Hápticos vía expect/actual**:
  - `UIImpactFeedbackGenerator` al confirmar acciones (guardar, marcar compra).
  - `UISelectionFeedbackGenerator` al navegar entre pasos de `CookingScreen`.
  - `UINotificationFeedbackGenerator` al guardar con éxito o error.
- **.help() modifier** en botones (tooltip VoiceOver + hover iPadOS).
- **.accessibilityLabel() y .accessibilityHint()** completos en todos los elementos interactivos.
- **Long press context menu** en cards de receta (menú flotante nativo iOS).

### Prioridad Baja

- **Lottie en empty states Android** (cocinero animado, lista vacía — vía `com.airbnb.android:lottie-compose:6.x`).
- **SharedElementTransition** entre `RecipeListScreen` y `RecipeDetailScreen` Android (Compose 1.5+, API experimental).
- **Micro-animación ❤️** al marcar favorito: escala + partículas (Android + iOS).
- **Hero transitions** lista → detalle en iOS.
- **Drag to reorder** en ingredientes y pasos del `RecipeForm` (Android).

---

### Reglas técnicas para Sprint 17

- Toda animación Compose corre en `Dispatchers.Main`. Toda animación JavaFX en el JavaFX Application Thread. Nunca en hilos de fondo.
- Sonidos y hápticos: siempre con toggle en pantalla de preferencias. Hápticos ON por defecto, sonidos OFF por defecto.
- `contentDescription` completo en todos los elementos interactivos antes de cerrar cada tarea.
- No introducir nuevas dependencias sin necesidad. Animaciones Compose son parte del BOM ya incluido.
- Tooltips Desktop son JavaFX estándar — sin librerías externas.
- Para Lottie Android (prioridad baja): añadir `com.airbnb.android:lottie-compose:6.x` al `build.gradle.kts`.
- Para hápticos iOS: implementar `expect class HapticFeedback` con `actual` en `iosMain`.

---

## Sprint 20 — COMPLETADO (2026-05-29) — commits 847c528 + 6963f04

### iOS — Claude Code ✅

- `shopping/ShoppingListRepository.kt` (nuevo): `loadLists()` + `loadItems(listId)` via Ktor.
- `shopping/ShoppingListScreen.kt` (nuevo): lista de listas → drill-down items. Back button, haptic, tachado+Checkbox read-only.
- `menu/MenuRepository.kt` (nuevo): `loadCurrentWeek()` sin weekStart (backend auto-selecciona semana actual). Orden por fecha + tipo de comida.
- `menu/MenuScreen.kt` (nuevo): cards por día, chips tipo comida (Desayuno/Almuerzo/Merienda/Cena), empty state con icono.
- `sync/SyncRepository.kt` (nuevo): `pullIncremental()` — GET /sync/pull?since=X, upsert recipes+stockItems en SQLDelight. Silent en fallo de red.
- `AppDatabase.sq`: tabla `sync_metadata` (key/value) + queries `getMetadata`/`setMetadata`.
- `ApiDtos.kt`: `SyncPullResponseDto` (serverTime + recipes + stockItems + familyNotes).
- `App.kt`: `LaunchedEffect(isLoggedIn)` dispara sync en background al hacer login.
- `ui/MainTabScreen.kt`: PlaceholderScreen eliminado — ambas tabs con implementación real.

### Desktop — Codex, BUILD SUCCESS ✅

- `StockView.java`: `animateDelete()` — FadeTransition 150ms + colapso altura 150ms antes de borrar.
- `NotesView.java`: misma secuencia de animación al eliminar nota.
- `MainWindow.java`: botón ⚙ Ajustes en sidebar + `showPreferencesDialog()` con CheckBox "Efectos de sonido". Persistencia en `Preferences`, tooltip + atajo Ctrl+,.

### Decisiones arquitectónicas Sprint 20 (Gemini)

- `lastSyncTime` en SQLDelight `sync_metadata` (atomicidad con los datos).
- Push diferido: iOS es read-only, push no necesario hasta que haya pantallas de creación.
- Triggers: al login (App.kt) + pull-to-refresh manual (futuro Sprint 21).
- Conflictos: ninguno por ahora (iOS solo lee; LWW ya en backend).

## Sprint 21 — COMPLETADO (2026-05-29)

### Sprint 21.1 — Pull-to-refresh iOS ✅ COMPLETADO

- `App.kt`: pasa `syncRepo` a `MainTabScreen` (parámetro nuevo).
- `MainTabScreen.kt`: recibe `syncRepo: SyncRepository` + import + pasa a `RecipeListScreen` y `StockScreen`.
- `RecipeListScreen.kt`: `IconButton(Refresh)` en header; `isRefreshing` state; al pulsar → `syncRepo.pullIncremental()` + `repository.loadRecipes()`; indicador `CircularProgressIndicator` mientras carga; botón "Reintentar" en estado de error.
- `StockScreen.kt`: mismo patrón. Import `Icons.Filled.Refresh` añadido.

### Sprint 21.2 — Lottie Empty States Android ✅ COMPLETADO

- `build.gradle.kts`: `com.airbnb.android:lottie-compose:6.5.0`.
- `res/raw/lottie_chef.json`: animación cocinero bounce (sombrero + cabeza + cuerpo + vapor).
- `res/raw/lottie_empty_list.json`: animación portapapeles swing.
- `SharedComposables.kt`: `LottieEmptyStateView` con `rememberLottieComposition` + `LottieConstants.IterateForever` + tamaño 160dp + mismo layout que `EmptyStateView`.
- `RecipeScreens.kt`: empty state recetas (sin filtro) usa `LottieEmptyStateView(R.raw.lottie_chef)`.

Build: `gradle assembleDebug` — **BUILD SUCCESSFUL** (41s, 0 errores).

### Sprint 21.3 — Micro-animación ❤️ favorito Android ✅ COMPLETADO

- `RecipeScreens.kt`: en `RecipeDetail`:
  - `val favoriteScale = remember { Animatable(1f) }`.
  - Al hacer click: `haptic.performHapticFeedback(HapticFeedbackType.LongPress)` + `scope.launch { animateTo(1.35f, spring(MediumBouncy)) → animateTo(1f, spring(MediumBouncy)) }`.
  - `Icon` del favorito con `Modifier.graphicsLayer { scaleX/scaleY = favoriteScale.value }`.
- Imports añadidos: `Animatable`, `Spring`, `spring`, `graphicsLayer`, `HapticFeedbackType`, `LocalHapticFeedback`, `rememberCoroutineScope`, `launch`.

Build final: **BUILD SUCCESSFUL**.

## Sprint 22 — COMPLETADO (2026-05-29)

### Sprint 22.1 — iOS RecipeDetailScreen ✅ COMPLETADO

- `ApiDtos.kt`: `RecipeIngredientDto` (id, position, name, quantity, unit, note) + `RecipeStepDto` (id, position, instruction, timerMinutes).
- `RecipeRepository.kt`: `loadIngredients(recipeId)` GET `/ingredients` + `loadSteps(recipeId)` GET `/steps`; ambos con fallback `emptyList()`.
- `RecipeDetailScreen.kt` (nuevo): header con ← back + haptic; meta chips; descripción; ingredientes ordenados por `position`; pasos con círculo numerado + timer.
- `RecipeListScreen.kt`: `var selectedRecipe`; tap en card → navega al detalle; back → vuelve a lista.

### Sprint 22.2 — iOS MenuScreen navegación semanas ✅ COMPLETADO

- `libs.versions.toml`: `datetime = "0.6.0"` + entrada `kotlinx-datetime`.
- `build.gradle.kts`: `implementation(libs.kotlinx.datetime)` en `commonMain.dependencies`.
- `MenuRepository.kt`: `loadAllItems()` con `size=200`.
- `MenuScreen.kt`: `weekOffset` state; calcula `weekStart/weekEnd` con `kotlinx.datetime`; filtro client-side; botones ← → con `Icons.AutoMirrored`; labels "Esta semana / Semana pasada / Próxima semana / Hace N semanas / En N semanas".

### Sprint 22.3 — Android Lottie en StockList y NotesScreen ✅ COMPLETADO

- `StockScreens.kt`: `LottieEmptyStateView(R.raw.lottie_empty_list)` en empty state stock.
- `NotesScreens.kt`: `LottieEmptyStateView(R.raw.lottie_empty_list)` en empty state notas.

Build: `gradle assembleDebug` — **BUILD SUCCESSFUL** (5s, 0 errores).

## Sprint 23 — COMPLETADO (2026-05-29)

### Sprint 23.1 — iOS Micro-animación ❤️ favorito ✅ COMPLETADO

- `ApiDtos.kt`: `FavoriteRecipeDto` (recipeId, recipeTitle, createdAt).
- `RecipeRepository.kt`: `loadIsFavorite(recipeId)` GET /favorites size=200; `addFavorite()` POST; `removeFavorite()` DELETE.
- `RecipeDetailScreen.kt`: botón ❤️ en header tras el título; `isFavorite` cargado en `LaunchedEffect`; toggle optimista con rollback en error; `Animatable(1f→1.35f→1f, spring MediumBouncy)` + `haptic.impact()`.

### Sprint 23.2 — Android SharedElementTransition ✅ COMPLETADO

- `RecipeScreens.kt`: `SharedTransitionLayout + AnimatedContent(selectedRecipe)` reemplaza las ramas `selectedRecipe→else` del `when`; transición `fadeIn(300ms)+slideInHorizontally` / `fadeOut(250ms)+slideOutHorizontally`.
- `sharedBounds` por clave `"recipe_bounds_${id}"` en `RecipeCard` y `RecipeDetail` (via `rememberSharedContentState`).
- `RecipeDetail` acepta `modifier: Modifier = Modifier` aplicado al `LazyColumn`.
- OptIn: `ExperimentalSharedTransitionApi` a nivel de fichero.

### Sprint 23.3 — Lottie/animación ShoppingListScreen ✅ COMPLETADO

- `RecetasApp.kt` Android: `EmptyStateView` → `LottieEmptyStateView(lottie_empty_list)` en `ShoppingListScreen` (sin listas) y `ShoppingListDetail` (lista vacía).
- `ShoppingListScreen.kt` iOS: `AnimatedShoppingEmptyState` — icono carrito pulsante con `rememberInfiniteTransition + animateFloat` (escala 1.0→1.10, 1400ms).

Build: `gradle assembleDebug` — **BUILD SUCCESSFUL** (4s, 0 errores; 1 warning pre-existente tooltip).

## Sprint 24 — COMPLETADO (2026-05-29)

### Sprint 24.1 — iOS CookingScreen ✅ COMPLETADO

- `CookingScreen.kt` (commonMain, nuevo): paso a paso; `detectHorizontalDragGestures` (swipe izq→sig, dcha→ant, umbral 80f); temporizador countdown `MM:SS` con `AnimatedContent slideInVertically`; `LinearProgressIndicator`; loading desde `repository.loadSteps()`; pantalla final "¡Buen provecho!"; botones Anterior/Siguiente/Finalizar.
- `ScreenWakeLock.kt` (expect) + `ScreenWakeLock.ios.kt` (actual: `UIApplication.sharedApplication.idleTimerDisabled`); `DisposableEffect` en CookingScreen activa/desactiva.
- `RecipeListScreen.kt`: `cookingMode` state; early-return a `CookingScreen`; `onCookingMode` lambda a `RecipeDetailScreen`.
- `RecipeDetailScreen.kt`: param `onCookingMode: (() -> Unit)? = null`; `ExtendedFloatingActionButton("Cocinar")` flotante (solo si steps no vacíos).

### Sprint 24.2 — iOS compartir receta ✅ COMPLETADO

- `ShareSheet.kt` (expect: `fun shareText(text: String)`) + `ShareSheet.ios.kt` (actual: `UIActivityViewController` via `keyWindow.rootViewController.presentViewController`).
- `buildShareText()` en `RecipeDetailScreen`: texto con título, meta, 🥗 ingredientes, 👨‍🍳 pasos.
- Botón `Icons.Filled.Share` en header de `RecipeDetailScreen`.

### Sprint 24.3 — Android swipe gestures CookingScreen ✅ COMPLETADO

- `CookingScreen.kt` (Android): `pointerInput(steps.size) { detectHorizontalDragGestures }`; swipe izquierda → siguiente paso (o finaliza), swipe derecha → anterior; haptic `LongPress` en cada cambio.

Build: `gradle assembleDebug` — **BUILD SUCCESSFUL** (3s, 0 errores).

## Sprint 25B — Polish Android ✅ COMPLETADO (2026-05-29)

- **B-1** `RecetasApp.kt`: TopAppBar con título contextual por tab + `AnimatedContent(targetState=tab)` fade 200/150ms entre tabs.
- **B-2** `NotesScreens.kt`: FAB flotante (Box + `FloatingActionButton` BottomEnd) consistente con RecipeList y StockList.
- **B-3** `StockScreens.kt`: `animateItem()` en `SwipeToDismissBox`, `SkeletonStockCard` shimmer, `Crossfade` empty/list, skeleton cuando `stockItems.isEmpty() && isRefreshing`.
- **B-4** `NotesScreens.kt`: `animateItem()` + `SkeletonNoteCard` shimmer + `Crossfade` same pattern.
- **B-5** `RecipeScreens.kt`: `difficultyLabel()` helper — EASY→Fácil, MEDIUM→Media, HARD→Difícil (ambas ocurrencias: RecipeCard + RecipeDetail).
- **B-6** `StockScreens.kt:328`: Botón Eliminar `StockDetail` → `Button(colors=ButtonDefaults.buttonColors(errorContainer, onErrorContainer))`.
- **B-7** `MenuScreen.kt`: Empty state → `LottieEmptyStateView(lottie_empty_list, "Sin comidas esta semana", ...)`.

Build: `gradle assembleDebug` — **BUILD SUCCESSFUL** (48s, solo warnings pre-existentes).

## Sprint 25C — Polish Desktop ✅ COMPLETADO (2026-05-29)

- **C-1+C-3** `MainWindow.java`: campos de instancia `btnDashboard...btnNotes`, emojis en sidebar (`🏠 Inicio`, `📖 Recetas`, `🧂 Stock`, `📅 Menú semanal`, `🛒 Lista de la compra`, `📝 Notas familiares`), `updateActiveSidebarButton(view)` aplica/quita CSS `sidebar-nav-button-active` al navegar.
- **C-2** `DashboardView.java`: `loadingLabel()` devuelve `HBox(ProgressIndicator + Label)` en vez de texto plano. Return type `HBox`, callers usan `Node`.

Build: `mvn compile` — **BUILD SUCCESS**.

## Sprint 25D — Polish iOS ✅ COMPLETADO (2026-05-29)

- **D-1** `Spacing.kt` (nuevo, commonMain, base package): objeto `Spacing` idéntico al de Android (xxs/xs/sm/md/lg/xl/xxl = 2/4/6/8/12/16/24 dp).
- **D-2** `RecipeListScreen.kt`: `RecipeCard` rica — header 152dp con secondaryContainer + icono Restaurant semitransparente + gradiente vertical + MetaChips (tiempo, dificultad, porciones). `RecipeMetaChip` composable.
- **D-3** `AnimatedEmptyState` (en `RecipeListScreen.kt`, `internal`): emoji pulsante con `rememberInfiniteTransition` + `animateFloat` alpha 0.5→1.0 1400ms. Usada en RecipeListScreen, NotesScreen, StockScreen, MenuScreen.
- **D-4** `animateItem()`: RecipeCard + NotesScreen cards + StockScreen items (via `Box(Modifier.animateItem())`).
- **D-5** `MenuScreen.kt` iOS: `DayMenuCard(isToday: Boolean)` — `CardDefaults.cardColors(primaryContainer)` + chip "Hoy" cuando es hoy.
- **D-6** `PullToRefreshBox` en `RecipeListScreen.kt` y `StockScreen.kt` iOS (`@OptIn(ExperimentalMaterial3Api::class)`).

Nota: build iOS en Windows falla por issue pre-existente SQLDelight + Gradle 9.5.1. Cambios iOS son Kotlin editable en Android Studio; compilación binaria requiere macOS + Xcode.

## Sprint 26 — COMPLETADO (2026-05-29)

### Sprint 26.A — Android: Drag-to-reorder RecipeForm ✅ COMPLETADO

- **`build.gradle.kts`**: añadida dependencia `sh.calvin.reorderable:reorderable:2.4.3`.
- **`RecipeFormScreen.kt`** (reescrito):
  - `IngredientDraft` y `StepDraft` ahora tienen `id: String = UUID.randomUUID().toString()` (último campo, con default; compatibilidad posicional conservada).
  - `rememberLazyListState()` + `rememberReorderableLazyListState` con callback de intercambio seguro por tipo (prefijos `"ing_"` / `"step_"` en keys — cross-section drag ignorado silenciosamente).
  - `items(ingredients, key = { "ing_${it.id}" })` y `items(steps, key = { "step_${it.id}" })` con `ReorderableItem` y `Modifier.draggableHandle()` en icono `DragHandle`.
  - Todos los `item { }` fijos llevan `key` explícita para estabilidad del LazyColumn.
  - Los onChange de cada campo usan `indexOfFirst { it.id == X }` para lookups estables.
  - Número de paso calculado con `steps.indexOfFirst { it.id == step.id } + 1` dentro de `ReorderableItem` (se actualiza con cada recomposición).

Build: `./gradlew assembleDebug` — **BUILD SUCCESSFUL** (27s).

### Sprint 26.B — iOS: NotesScreen CRUD ✅ COMPLETADO

- **`ApiDtos.kt`**: añadido `CreateNoteRequest(@Serializable data class)` antes de `SyncPullResponseDto`.
- **`NoteRepository.kt`** (ampliado): `createNote(title, body, pinned)` POST, `updateNote(id, title, body, pinned)` PUT, `deleteNote(id)` DELETE. Todos usan `session.familyId ?: error("Sin sesión activa")`.
- **`NotesScreen.kt`** (iOS, reescrito): CRUD completo con estados `selectedNote`, `editingNote`, `showCreate`. `when` ramifica en: loading, error vacío, formulario crear, formulario editar, detalle nota, lista. FAB flotante en overlay `Box` (oculto cuando hay formulario/detalle abierto). `NoteForm` y `NoteDetail` como composables privados. `haptic.error()` al eliminar, `haptic.selection()` al seleccionar.

### Sprint 26.C — Desktop: Dashboard con menú del día real ✅ COMPLETADO

- **`DashboardView.java`**: campo `menuTodaySection: VBox(4)`, incluido en `buildHeader()` tras el subtítulo. `loadTodayMenu()` llama `menuRepository.loadForWeek(lunes)`, filtra por `today.equals(i.plannedDate())`, renderiza en `menuTodaySection` con `mealTypeLabel()` (emojis ☀️🍽🫖🌙). Si no hay items o hay error, la sección queda vacía (sin ruido visual). Llamado desde `refresh()`. `mealTypeLabel()` private helper para etiquetas con emoji.

Build: `mvn compile` — **BUILD SUCCESS** (0 output).

## Sprint 27 — Candidatos

### Prioridad Alta

1. **iOS + Android:** Onboarding primera vez — 3 pantallas (`SharedPreferences` / `NSUserDefaults`, mostrar una sola vez).
2. **iOS:** `StockScreen` CRUD — crear/editar stock items (actualmente solo lectura).
3. **Android:** `CookingScreen` swipe visual (mostrar hint deslizamiento con `AnimatedVisibility`).
