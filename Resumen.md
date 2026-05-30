Resumen Descriptivo Completo - "Recetas Familia"
"Recetas Familia" es una aplicacion premium multiplataforma (Android, Desktop e iOS) disenada para ayudar a las familias a organizar, compartir y disfrutar de su cocina de forma moderna, emocional y eficiente.

## Proposito Principal

Crear un espacio digital familiar donde se puedan guardar, descubrir, planificar y cocinar recetas de forma colaborativa, manteniendo vivas las tradiciones culinarias familiares y facilitando la organizacion diaria de las comidas.

## Caracteristicas Principales

### Gestion de Recetas

- Creacion y edicion avanzada de recetas con ingredientes, pasos detallados, tiempos de preparacion/coccion, dificultad, porciones.
- Soporte para multiples fotos por receta.
- Etiquetas y categorias.
- Sistema de valoracion y comentarios familiares.

### Ingredientes y Stock Familiar

- Base de datos compartida de ingredientes con control de stock.
- Alertas de bajo stock o caducidad proxima.
- Lista de la compra generada automaticamente desde menus planificados.

### Planificacion Familiar

- Menus semanales y mensuales colaborativos.
- Calendario de comidas.
- Sugerencias automaticas de menus.

### Aspecto Social y Familiar

- Sistema de recetas favoritas compartidas.
- Notas personales y anecdotas asociadas a cada receta.
- Modo Cocina en Familia con temporizadores compartidos.

## Experiencia por Plataforma

### Desktop (JavaFX) - Experiencia Principal

- Interfaz completa pensada para uso en cocina o mesa.
- Sidebar lateral con navegacion rapida.
- Dashboard visual con recetas destacadas, menus de la semana y stock critico.
- Modo Cocina (letra grande, temporizadores, pasos a paso).
- Gestion avanzada (filtros, busqueda global, exportacion).

### Android - Experiencia Movil

- Disenio Material You 3 dinamico.
- Bottom Navigation + Navigation Drawer.
- Acceso rapido desde la cocina.
- Modo offline completo con sincronizacion cuando hay conexion.

### iOS - Experiencia Movil Apple (EN DESARROLLO)

- Kotlin Multiplatform + Compose Multiplatform.
- Navegacion nativa iOS (TabView NavigationBar 5 tabs).
- Offline-first con SQLDelight 2.0.2 y Ktor.
- UI coherente con Android, adaptada a las convenciones iOS.
- Sprint 19 completado: SQLDelight cache offline + hápticos UIKit + SwipeToReveal (commit 8853bd0).

## Estilo Visual y UX

- Estilo: Calido, moderno, premium y emocional (Notion + Material You + Apple Design).
- Paleta de colores: Tonos tierra, verdes suaves, naranjas y amarillos apetecibles.
- Dark Mode espectacular y Light Mode acogedor.
- Micro-interacciones suaves y satisfactorias.

## Diferenciadores Clave

- Enfoque familiar real (no solo individual).
- Historia y memoria emocional de las recetas.
- Inteligencia practica (sugerencias segun stock, temporada, preferencias).
- Experiencia coherente y sincronizada entre movil (Android + iOS) y escritorio.
- Privacidad y control total de los datos familiares.

---

## Estado del Proyecto por Modulo

### Backend Spring Boot (SPRINT 36 COMPLETO)

- Spring Boot 3.5.14 + Java 21 + MySQL + Flyway.
- Auth JWT + refresh tokens + rate limiting.
- CRUD completo: recetas, ingredientes, pasos, stock, menus, listas de compra, favoritos, notas, fotos, valoraciones.
- Sync pull/push con tombstones, LWW y deteccion de conflictos.
- Gestión miembros familia: listMembers, updateMemberRole, removeMember (Sprint 33) + inviteMember con anti-enumeración completa OWASP (Sprint 34+36).
- Revocación refresh tokens al expulsar miembro. Validación magic bytes JPEG/PNG/WebP en uploads.
- **76 tests, 0 fallos**.
- Hardening HTTP: CSP, HSTS, CORS deny-by-default. Fix SSRF en validateHttpsUrl.
- OpenAPI/Swagger desactivado en produccion.
- Seed de desarrollo: demo@recetas.local / Demo1234!Familia

Arranque dev:
```bash
java -jar "backend/target/recetas-familiares-backend-0.1.0-SNAPSHOT.jar" \
  --spring.profiles.active=dev \
  "--spring.datasource.password=Recetas2024!" \
  "--app.dev.seed-data.enabled=true" \
  "--app.dev.seed-data.email=demo@recetas.local" \
  "--app.dev.seed-data.password=Demo1234!Familia" \
  "--app.dev.seed-data.display-name=Demo" \
  "--app.dev.seed-data.family-name=FamiliaDemo"
```

Contratos API criticos (no cambiar sin revisar Android y Desktop):
- PageResponse<T>: campos `items`, `page`, `size`, `totalItems`, `totalPages`
- Notas: /api/v1/families/{id}/notes (NO /family-notes)
- Stock: /api/v1/families/{id}/stock-items (NO /stock)
- StockItemResponse: campo `name` (NO `ingredientName`)
- RecipeIngredientResponse: `position`, `name`, `quantity` (BigDecimal), `note`
- RecipeStepResponse: `position`, `instruction`, `timerMinutes`

### Android Kotlin + Compose (SPRINT 34 COMPLETO — 2026-05-30)

Stack completo verificado:
- AGP 9.2.0 + Kotlin 2.3.20 + KSP 2.3.7
- Compose + Material 3 (BOM 2026.05.00) + DataStore Preferences 1.1.4
- Retrofit 3 + OkHttp 5 + Room 2.8.4 (v2) + WorkManager 2.11.2
- MVVM + AppContainer (DI manual) + EncryptedSharedPreferences

Sistema de temas (Sprint 25A):
- `AppTheme.kt` — 10 temas × 2 modos = 20 ColorSchemes Material3 completos (Bosque, Terracota, Ocaso, Mediterráneo, Lavanda, Oliva, Canela, Menta, Frambuesa, Noche de Verano)
- `ThemePreference.kt` — DataStore persistencia tema + modo (LIGHT/DARK/SYSTEM)
- `Theme.kt` — `RecetasTheme(appTheme, themeMode)` con `AppTypography` (13 tokens)
- `ThemePickerDialog.kt` — cuadrícula 5×2 de swatches de color + chips modo
- `RecetasApp.kt` — botón paleta 🎨 en TopAppBar abre el diálogo

Pantallas implementadas (Sprint 1-15 + UI):
- **LoginScreen** rediseñada — ícono brand circular, tipografía centrada, botón "Entrar →"
- TopAppBar con búsqueda global unificada (Recetas + Stock + Notas)
- **RecipeListScreen** — **tarjetas visuales** con gradiente + placeholder + chips (⏱ tiempo, dificultad, porciones); paginación; búsqueda; FilterChips dificultad + **"Con mi stock"** (filtro reactivo vs stock Room); pull-to-refresh; **FAB "+"**
- **RecipeDetailScreen** — `←` IconButton back; ❤️ favorito; ⋮ menú; fotos carrusel; valoraciones; **ExtendedFAB "▶ Cocinar"** visible
- RecipeForm (SegmentedButton dificultad, filas dinámicas ingredientes/pasos)
- CookingScreen (paso a paso, temporizador countdown, keep screen on, **volumen ↑↓ cambia paso**)
- **StockScreen** — badges bajo stock, colores caducidad, Sort toggle, FAB crear, CRUD inline, notificaciones caducidad; **`←` IconButton back; botones ✏ Editar / 🗑 Eliminar con ícono**
- **ShoppingListScreen** — `←` IconButton back; check offline-resilient; **tachado en ítems marcados**; botón "Compartir"
- **NotesScreen** — `←` IconButton back; botones ✏ Editar / 🗑 Eliminar con ícono; CRUD completo; búsqueda; empty states
- GlobalSearchScreen (resultados agrupados entre tabs)
- **ProfileScreen** (6º tab "Perfil" — avatar con iniciales, nombre, email, botón cerrar sesión)
- **MenuScreen** (5º tab "Menú", navegación ← → semanas, CRUD assign/remove, "Ver receta")
- Widgets: RecipeWidget (receta del día) + StockWidget (ítems críticos)
- Bottom Navigation: **6 tabs** (RECIPES, STOCK, SHOPPING, NOTES, MENU, PROFILE)
- Snackbar feedback en todas las mutaciones
- **SyncWorker pushThenPull: 7 tipos** — Recetas + Ingredientes + Pasos + Stock + Shopping items + Favoritos + Notas
- **Notificaciones caducidad**: HOY urgente (PRIORITY_HIGH) + esta semana 1-7 días (PRIORITY_DEFAULT)

Compilar y desplegar:
```
# Desde android/
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

SDK: C:\Users\GipsyDavy\AndroidSDK
AVD: Pixel_9_Pro (API 36)
API base URL en emulador: http://10.0.2.2:8080/

### Desktop JavaFX (SPRINT 36 COMPLETO — 2026-05-30)

JavaFX 21 + OkHttp 4.12.0 + Gson. Compila y genera fat JAR (13.3 MB).
mvn compile — EXITOSO.

Sistema de temas (Sprint 25A):
- `ThemeManager.java` — singleton, Java Preferences API, carga CSS dinámico, detección dark mode Windows
- `style.css` — refactorizado con looked-up colors JavaFX (`recetas-primary`, `recetas-bg`, etc.)
- `themes/` — 20 archivos CSS (10 temas × light/dark), cada uno sobreescribe solo `.root { recetas-*: #valor }`
- `MainWindow.java` — `ThemeManager.attach(scene)`; diálogo Ajustes con RadioButton modo + ComboBox tema

Pantallas implementadas (Sprint 1-15):
- LoginView
- DashboardView (GridPane 2 columnas: recetas recientes + stock expirando + **acciones rápidas: Stock familiar / Notas familiares**)
- RecipeListView (SplitPane filtrable, búsqueda, paginación incremental 30/pág, botón "Actualizar")
- RecipeDetailView (ingredientes, pasos, fotos async, Editar, Eliminar, Modo Cocina, **"📋 Copiar"**, **"💾 Exportar" → .txt**)
- RecipeFormDialog (modal crear/editar)
- CookingView (Stage maximizado, paso a paso, temporizador JavaFX Timeline)
- StockView (TableView con toolbar CRUD, búsqueda, **paginación client-side PAGE_SIZE=50, "Cargar más"**, botón "Actualizar")
- WeeklyMenuView (calendario semanal, navegación ← semanas →, CRUD assign/remove, botón "Actualizar")
- ShoppingListView (empty state, botón "Actualizar" → sync completo)
- NotesView (SplitPane lista + editor inline, CRUD, búsqueda, paginación 30/pág, botón "Actualizar")
- GlobalSearchView (búsqueda unificada Recetas/Stock/Notas desde sidebar)
- **ExpiryNotificationService**: toast transparente bottom-right tras cada sync si hay stock ≤3 días

Sidebar: Búsqueda global | Inicio | Recetas | Stock | Menú semanal | Lista de la compra | Notas

Ejecutar: `mvn javafx:run -Dapi.base.url=http://localhost:8080/`

### iOS KMP + Compose Multiplatform (SPRINT 35.C COMPLETADO — 2026-05-30)

Stack implementado:
- Kotlin Multiplatform + Compose Multiplatform (Kotlin 2.0.21, Compose 1.7.0)
- Ktor 3.0.3 (HttpClient Darwin — iOS engine)
- SQLDelight 2.0.2 + NativeSqliteDriver (cache offline + sync_metadata)
- Keychain (SecItemAdd/Copy/Delete) para tokens — Sprint 18.4
- Targets: iosX64, iosArm64, iosSimulatorArm64

Pantallas implementadas (Sprint 16-20):
- `LoginScreen` (Compose M3, coroutines, error handling)
- `RecipeListScreen` (LazyColumn, paginado Ktor, haptic.selection() al tocar)
- `StockScreen` (SwipeToReveal por item con Animatable, badge bajo stock)
- `NotesScreen` (preview 80 chars, pin emoji, haptic.selection() al tocar)
- `ShoppingListScreen` (Sprint 20 — 2 niveles: lista de listas → items drill-down, tachado read-only)
- `MenuScreen` (Sprint 20 — cards por día, chips tipo comida Desayuno/Almuerzo/Merienda/Cena)
- `MainTabScreen` (NavigationBar 5 tabs — todos operativos)

Infraestructura (Sprint 18-20):
- `DatabaseDriverFactory` expect/actual + `AppDatabase.sq` (recipes + stock_items + sync_metadata)
- `RecipeRepository` + `StockRepository`: cache-first offline con SQLDelight
- `SyncRepository` (Sprint 20): `pullIncremental()` — GET /sync/pull?since=X, upsert SQLDelight; disparo silencioso al login
- `HapticFeedback` expect/actual — UIImpactFeedbackGenerator / UISelectionFeedbackGenerator / UINotificationFeedbackGenerator

Arquitectura ios/ (proyecto Gradle KMP independiente):
- `composeApp/src/commonMain/` — UI Compose + repositorios + DTOs + SQLDelight schemas
- `composeApp/src/iosMain/` — MainViewController + SessionStore.ios + DatabaseDriverFactory.ios + HapticFeedback.ios
- `iosApp/` — Entry point SwiftUI: iosApp.swift + ContentView.swift

Sistema de temas (Sprint 25A):
- `theme/AppTheme.kt` (commonMain) — 10 temas, `lightColors()` / `darkColors()`, `AppTypography`
- `theme/ThemePreference.kt` (commonMain) + `ThemePreference.ios.kt` (iosMain, NSUserDefaults) — expect/actual
- `App.kt` — `MaterialTheme(colorScheme, typography)` con el tema seleccionado
- `ui/SettingsScreen.kt` — pantalla ajustes con **sección usuario** (avatar iniciales + nombre + email) + swatches tema + chips modo + botón logout
- `ui/MainTabScreen.kt` — 6º tab "Ajustes" con ícono `Settings`

Compilacion: requiere macOS + Xcode para generar binario/framework.
En Windows: edicion Kotlin completa via Android Studio.
Nota: build Gradle falla en Windows por issue pre-existente SQLDelight plugin + Gradle 9.5.1 (no relacionado con Sprint 25A).

### Base de Datos MySQL

- MySQL80 service en localhost:3306
- Usuario: recetas_app / lee 
- Base de datos: recetas_familiares
- 9 migraciones Flyway. 14 tablas principales con soft delete, syncVersion y UUID como PK.

---

## Sprints Completados

| Sprint | Fecha | Contenido |
|--------|-------|-----------|
| 1 | 2026-05 | Login + RecipeList + StockView Desktop; Login + RecipeList Android |
| 2 | 2026-05-27 | Dashboard Desktop, RecipeFormDialog, RecipeDetailView |
| 3 | 2026-05-28 | RecipeDetail Android, StockScreen mejorada, WeeklyMenuView Desktop |
| 4 | 2026-05-28 | Persistencia tokens Desktop, CRUD menú semanal, MIGRATION_1_2 Android |
| 5 | 2026-05-28 | ShoppingListView Desktop+Android, FavoriteRepository Desktop+Android |
| 6 | 2026-05-28 | NotesView Desktop + NotesScreen Android (CRUD completo) |
| fix | 2026-05-28 | 8 bugs contratos DTO Desktop + URLs endpoints (commit 5404a7b) |
| 7.1 | 2026-05-28 | CRUD Stock Items Desktop: StockFormDialog + toolbar StockView |
| 7.2 | 2026-05-28 | CRUD Stock Items Android: FAB + StockForm + StockDetail |
| 7.3 | 2026-05-28 | Crear/Editar Receta Android: RecipeForm SegmentedButton + filas dinámicas |
| 7.4 | 2026-05-28 | SyncWorker push offline-resilient (syncVersion=0 + pushThenPull) |
| 8.1 | 2026-05-28 | Snackbar feedback + Pull-to-refresh Android |
| 8.2 | 2026-05-28 | Búsqueda global Android (recetas, stock, notas) + contador filtro Desktop |
| 8.3 | 2026-05-28 | Modo Cocina Android (paso a paso, temporizador, keep screen on) |
| 8.4 | 2026-05-28 | Modo Cocina Desktop (CookingView, Stage maximizado, Timeline JavaFX) |
| 8.5 | 2026-05-28 | Empty states ilustrados Android + Desktop (notas, stock, compra) |
| 8.6 | 2026-05-28 | CRUD update/delete offline-resilient Android (stock + notas) |
| 8.7 | 2026-05-28 | Fotos de receta: upload multipart Backend + carrusel Android + galería Desktop |
| 9.1 | 2026-05-28 | Notificaciones de caducidad stock Android (WorkManager diario) |
| 9.2 | 2026-05-28 | Valoraciones familiares: endpoint backend + 5 tests + UI Android |
| 9.3 | 2026-05-28 | Paginación de recetas Android (carga incremental) |
| audit | 2026-05-28 | Auditoría completa Sprint 1-9: seguridad, tests, calidad, limpieza |
| fix-audit | 2026-05-28 | Fixes auditoría: CancellationException Android, timeouts+Authorization Desktop, timeouts Android |
| 10.1 | 2026-05-28 | Widgets Android: RecipeWidget (receta del día) + StockWidget (ítems críticos) |
| 10.2 | 2026-05-28 | Búsqueda global Desktop: GlobalSearchView + filterBy() en RecipeList/Stock/Notes |
| 10.3 | 2026-05-28 | CRUD offline-resilient recetas Android (create/update/delete + pushThenPull) |
| 10.4 | 2026-05-28 | Design tokens Spacing.* + extracción composables (1887→250 líneas RecetasApp.kt) |
| 11.1 | 2026-05-28 | Búsqueda global Android: TopAppBar + GlobalSearchScreen entre tabs |
| 11.2 | 2026-05-28 | Pull-to-refresh Desktop: botón "Actualizar" en todas las vistas → triggerSync |
| 11.3 | 2026-05-28 | Offline-resilient shopping Android: checkItem con fallback + push en SyncWorker |
| 11.4 | 2026-05-28 | Paginación recetas Desktop: PAGE_SIZE=30, carga incremental, botón "Cargar más" |
| 12.1 | 2026-05-28 | Offline-resilient favoritos Android: toggle() con fallback + push en SyncWorker (7 tipos) |
| 12.2 | 2026-05-28 | Notificaciones caducidad Desktop: toast JavaFX bottom-right, auto-dismiss 5s |
| 12.3 | 2026-05-28 | Paginación notas Desktop: NoteRepository.loadPage() + NotesView PAGE_SIZE=30 |
| 12.4 | 2026-05-28 | Exportar receta Desktop: botón "📋 Copiar" copia texto completo al portapapeles |
| 13.1 | 2026-05-28 | Compartir receta Android: Intent.ACTION_SEND desde menú ⋮ de RecipeDetail |
| 13.2 | 2026-05-28 | Modo manos libres CookingScreen Android: volumen ↑↓ navega pasos sin tocar pantalla |
| 13.3 | 2026-05-28 | Dashboard acciones rápidas Desktop: botones "Stock familiar" y "Notas familiares" |
| 14.1 | 2026-05-29 | Menú semanal Android: 5º tab + MenuScreen.kt nav ← → semanas, cards por día |
| 14.2 | 2026-05-29 | Paginación stock Desktop: PAGE_SIZE=50 client-side, botón "Cargar más (N de total)" |
| 14.3 | 2026-05-29 | Exportar receta Desktop a .txt: botón "💾 Exportar" + FileChooser + Files.writeString |
| 14.4 | 2026-05-29 | Compartir lista de la compra Android: botón "Compartir" + Intent.ACTION_SEND |
| 14.5 | 2026-05-29 | Notificaciones caducidad Android mejoradas: HOY (PRIORITY_HIGH) + esta semana (7 días) |
| 15.1 | 2026-05-29 | CRUD menú semanal Android: botón "+" + AssignMenuDialog (receta + tipo comida) |
| 15.2 | 2026-05-29 | Navegar a receta desde MenuScreen: tap "Ver receta" → tab Recetas + RecipeDetail |
| 15.3 | 2026-05-29 | Filtros dificultad recetas Android: FilterChip Fácil/Media/Difícil |
| 15.4 | 2026-05-29 | Exportar lista de la compra Desktop: botón "💾 Exportar" → FileChooser .txt |
| 15.5 | 2026-05-29 | Ordenar stock por caducidad Android: icono Sort toggle, color primary cuando activo |
| 16.1 | 2026-05-29 | iOS Gradle KMP setup: settings, libs.versions.toml, wrapper local, composeApp targets |
| 16.2 | 2026-05-29 | iOS core: SwiftUI entry point, ComposeUIViewController, SessionStore expect/actual |
| 16.3 | 2026-05-29 | iOS Ktor network layer: ApiClient (Darwin engine + JWT) + 10 DTOs @Serializable |
| 16.4 | 2026-05-29 | iOS auth: AuthRepository (Ktor login/logout) + LoginScreen Compose M3 |
| 16.5 | 2026-05-29 | iOS RecipeListScreen básica: RecipeRepository (paginado) + LazyColumn M3 |
| UI-Android | 2026-05-29 | Rediseño visual Android: LoginScreen brand, RecipeCards gradiente+chips, IconButton nav, FAB Cocinar, tachado shopping, iconos Editar/Eliminar |
| 17-Android | 2026-05-29 | UX Premium Android: TooltipBox TopAppBar, semantics heading, Crossfade RecipeList, animateItem LazyColumn, SwipeToDismiss stock+notas con haptic, haptic CookingScreen pasos+timer |
| 17-Desktop | 2026-05-29 | UX Premium Desktop: FadeTransition sidebar, ScaleTransition modales, hover cards, ←/→ CookingView, tooltips botones, ContextMenu stock, status bar, Ctrl+F global |
| 18.1 | 2026-05-29 | iOS TabView 5 tabs: MainTabScreen (Recetas/Stock/Lista/Notas/Menú); App.kt actualizado |
| 18.2 | 2026-05-29 | iOS StockScreen: StockRepository (Ktor) + StockScreen composable con estados loading/error/empty |
| 18.3 | 2026-05-29 | iOS NotesScreen: NoteRepository (Ktor) + NotesScreen composable completo |
| 18.4 | 2026-05-29 | iOS Keychain: SessionStore.ios.kt migrado de NSUserDefaults a SecItemAdd/Copy/Delete |
| 19-Android | 2026-05-29 | UX Polish Android: ModalBottomSheet menú ⋮, AnimatedContent timer, SkeletonRecipeCard shimmer, animateColorAsState chips+badges, animateContentSize RatingsSection |
| 19-Desktop | 2026-05-29 | UX Polish Desktop: TranslateTransition ExpiryNotification, skeleton RecipeListView, SoundPlayer (playConfirm/Delete/TimerComplete) desactivado por defecto |
| 19-iOS | 2026-05-29 | iOS SQLDelight 2.0.2: DatabaseDriverFactory expect/actual, RecipeRepo+StockRepo cache offline; HapticFeedback expect/actual UIKit; SwipeToReveal StockScreen (pointerInput+Animatable) |
| 20-iOS | 2026-05-29 | ShoppingListScreen (2 niveles: listas→items drill-down, tachado read-only); MenuScreen (cards por día, chips tipo comida); SyncRepository pullIncremental (sync_metadata SQLDelight); LaunchedEffect sync al login |
| 20-Desktop | 2026-05-29 | StockView+NotesView animateDelete() FadeTransition+colapso 150ms; MainWindow ⚙ Ajustes con PreferencesDialog toggle sonidos (Codex, BUILD SUCCESS) |
| 21.1-iOS | 2026-05-29 | Pull-to-refresh manual: syncRepo pasado App→MainTabScreen→RecipeListScreen+StockScreen; IconButton Refresh + CircularProgressIndicator + pullIncremental() + botón Reintentar |
| 21.2-Android | 2026-05-29 | Lottie 6.5.0: LottieEmptyStateView en SharedComposables; lottie_chef.json (cocinero bounce) + lottie_empty_list.json (portapapeles swing) en res/raw; RecipeList usa LottieEmptyStateView |
| 21.3-Android | 2026-05-29 | Micro-animación ❤️ favorito: Animatable scale 1→1.35→1 spring(MediumBouncy) + HapticFeedbackType.LongPress; graphicsLayer en Icon favorito en RecipeDetail |
| 22.1-iOS | 2026-05-29 | RecipeDetailScreen: DTOs ingredientes+pasos; loadIngredients/loadSteps en RecipeRepository; pantalla detalle con meta chips, descripción, ingredientes, pasos numerados; nav tap→detalle, back→lista |
| 22.2-iOS | 2026-05-29 | MenuScreen semanas: kotlinx.datetime 0.6.0; weekOffset state; weekStart/weekEnd cálculo; filtro client-side; botones ← → AutoMirrored; labels semana; loadAllItems size=200 |
| 22.3-Android | 2026-05-29 | Lottie StockList + NotesScreen: LottieEmptyStateView(lottie_empty_list) en ambas pantallas (BUILD SUCCESSFUL 5s) |
| 23.1-iOS | 2026-05-29 | Micro-animación ❤️ favorito: FavoriteRecipeDto; loadIsFavorite/addFavorite/removeFavorite Ktor; botón ❤️ header RecipeDetailScreen; Animatable 1→1.35→1 spring(MediumBouncy) + haptic.impact() |
| 23.2-Android | 2026-05-29 | SharedElementTransition RecipeList→RecipeDetail: SharedTransitionLayout+AnimatedContent(selectedRecipe); sharedBounds "recipe_bounds_{id}" en RecipeCard y RecipeDetail; fadeIn+slideInHorizontally 300ms |
| 23.3-Android | 2026-05-29 | Lottie ShoppingList: LottieEmptyStateView(lottie_empty_list) en ShoppingListScreen y ShoppingListDetail (BUILD SUCCESSFUL 4s) |
| 23.3-iOS | 2026-05-29 | AnimatedShoppingEmptyState: icono carrito pulsante con rememberInfiniteTransition+animateFloat escala 1.0→1.10 1400ms en ShoppingListScreen |
| 24.1-iOS | 2026-05-29 | CookingScreen.kt (commonMain): swipe H gestures ±80f; timer countdown AnimatedContent slideInVertically; ScreenWakeLock expect/actual (UIApplication.idleTimerDisabled); FAB Cocinar en RecipeDetailScreen; cookingMode state en RecipeListScreen |
| 24.2-iOS | 2026-05-29 | Compartir receta: ShareSheet expect/actual (UIActivityViewController keyWindow); buildShareText() título+meta+ingredientes+pasos; botón Share en RecipeDetailScreen header |
| 24.3-Android | 2026-05-29 | CookingScreen swipe gestures: detectHorizontalDragGestures + pointerInput(steps.size); swipe izq→sig/finaliza, dcha→ant; haptic LongPress (BUILD SUCCESSFUL 3s) |
| 25A-Android | 2026-05-29 | Sistema temas Android: AppTheme (10 temas×2 modos), ThemePreference (DataStore), Theme.kt refactor, ThemePickerDialog (swatches+chips), botón paleta TopAppBar (BUILD SUCCESSFUL) |
| 25A-Desktop | 2026-05-29 | Sistema temas Desktop: ThemeManager.java (singleton+CSS dinámico), style.css (looked-up colors), 20 CSS themes/, MainWindow selector tema+modo en Ajustes (BUILD SUCCESS) |
| 25A-iOS | 2026-05-29 | Sistema temas iOS: AppTheme.kt+ThemePreference expect/actual (NSUserDefaults), App.kt+MaterialTheme, SettingsScreen.kt, MainTabScreen 6º tab Ajustes |
| 25B-Android | 2026-05-29 | TopAppBar título contextual AnimatedContent; FAB NotesScreen; SkeletonStockCard+NoteCard shimmer; Crossfade; difficultyLabel(); botón Eliminar errorContainer; MenuScreen Lottie |
| 25C-Desktop | 2026-05-29 | Sidebar emojis; updateActiveSidebarButton CSS active; DashboardView loadingLabel() HBox |
| 25D-iOS | 2026-05-29 | Spacing.kt commonMain; RecipeCard rica 152dp+gradiente+MetaChips; AnimatedEmptyState 4 pantallas; animateItem(); DayMenuCard isToday; PullToRefreshBox |
| 26.A-Android | 2026-05-29 | Drag-to-reorder ingredientes+pasos RecipeForm: reorderable 2.4.3; UUID en IngredientDraft/StepDraft; ReorderableItem+draggableHandle; lookup por id estable |
| 26.B-iOS | 2026-05-29 | NotesScreen CRUD: CreateNoteRequest DTO; createNote/updateNote/deleteNote Ktor; NotesScreen reescrito con FAB overlay + when states + NoteForm + NoteDetail |
| 26.C-Desktop | 2026-05-29 | DashboardView menú del día real: loadTodayMenu() desde MenuRepository; mealTypeLabel() emojis ☀️🍽🫖🌙; sección menuTodaySection en header |
| 27.A-Android+iOS | 2026-05-29 | Onboarding primera vez: OnboardingPreference expect/actual (NSUserDefaults iOS / SharedPrefs Android); OnboardingScreen 3 páginas AnimatedContent+dots; flujo !onboarding→!login→main |
| 27.B-iOS | 2026-05-29 | StockScreen CRUD: CreateStockItemRequest DTO; create/update/delete Ktor; StockScreen reescrito FAB+StockForm (avanzada colapsable)+SwipeToReveal delete real |
| 27.C-Android | 2026-05-29 | CookingScreen swipe hint: AnimatedVisibility pill "← Desliza para navegar →" centrado; LaunchedEffect auto-oculta 3s; fadeIn 400ms / fadeOut 600ms |
| 28.A-Android+iOS | 2026-05-29 | Perfil read-only: SessionStore +displayName+email (EncryptedSharedPrefs / Keychain); AuthRepository guarda datos al login; ProfileScreen Android (6º tab); SettingsScreen iOS sección usuario |
| 28.C-Android | 2026-05-29 | Filtro "Con mi stock": RecipeIngredientDao.observeAllIngredients(); filteredRecipes combine 4 flows con LOWER(TRIM) matching; FilterChip tertiaryContainer en RecipeList |
| Desktop-Ajustes-v1.1 | 2026-05-30 | Ajustes Desktop pasa de Dialog emergente a vista central navegable; pestañas Apariencia/Acerca de/Diagnostico estilo Nemeterial; tarjetas previsualizan cada tema real; Diagnostico usa logo real de la app; instalador v1.1 regenerado |
| Desktop-Diagnostico-v1.1 | 2026-05-30 | Pestaña Diagnostico ajustada para aprovechar todo el alto disponible: sin altura fija en el ScrollPane, crecimiento vertical con VBox.setVgrow y panel lateral de logo estirable; instalador v1.1 reescrito |
| 32-Desktop | 2026-05-30 | Auth/Roles Desktop: LoginView UX premium (card+animación+toggle password), FamilyRole enum (OWNER/ADMIN/MEMBER), AppSession.isAdmin(), FamilyRepository.detectAndSaveRole() desde GET /api/v1/families, sidebar permission-gated (ADMIN ve Ajustes+Miembros; MIEMBRO solo módulos diarios), FamilyMembersView read-only. VibeSec: BUILD SUCCESS |
| 33-Backend+Desktop | 2026-05-30 | Gestión miembros familia: GET/PUT/DELETE /api/v1/families/{id}/members; FamilyMemberResponse DTO; FamilyService listMembers/updateMemberRole/removeMember con ownership+admin checks+protección OWNER; 5 tests (67 total, 0 fallos). Desktop FamilyMembersView CRUD real: TableView API, Cambiar rol (ChoiceDialog), Expulsar (confirm), isSelf por email, botones permission-gated. VibeSec: sin críticos/altos |
| 34-Backend+Android+iOS | 2026-05-30 | Invitar miembro (POST /families/{id}/members); revocar refresh tokens al expulsar; magic bytes JPEG/PNG/WebP; Android ProfileScreen invite dialog; iOS SettingsScreen invite dialog; SessionStore familyRole; 6 tests nuevos (73 total, 0 fallos). Fixes UX: feedback invitación Android, reset inviteMessage iOS, KeyboardType.Email Android. |
| 35-Backend+Android+iOS | 2026-05-30 | Anti-enumeración inviteMember (email inexistente → 201 silencioso); tests revocación refreshToken; isAdmin reactivo StateFlow Android+iOS; VibeSec: 76 tests, 0 fallos. |
| 36-Backend+Desktop | 2026-05-30 | Anti-enumeración completa (CONFLICT 409 → 201 silencioso); Desktop avatar upload (FileChooser + postMultipart + AppSession.avatarUrl + circular clip); 76 tests, 0 fallos. |

## Sprint 25B/C/D — COMPLETADOS (2026-05-29)

### Sprint 25B — Polish Android ✅
- **B-1** TopAppBar título contextual por tab + `AnimatedContent` fade 200/150ms entre tabs
- **B-2** FAB flotante en NotesScreen (idéntico a RecipeList y StockList)
- **B-3** StockList: `animateItem()` + `SkeletonStockCard` shimmer + `Crossfade`
- **B-4** NotesScreen: `animateItem()` + `SkeletonNoteCard` shimmer + `Crossfade`
- **B-5** RecipeCard: dificultad traducida (EASY→Fácil, MEDIUM→Media, HARD→Difícil)
- **B-6** StockDetail: botón Eliminar con `ButtonDefaults.buttonColors(errorContainer)`
- **B-7** MenuScreen empty state con `LottieEmptyStateView`
Build: `gradle assembleDebug` — BUILD SUCCESSFUL (48s)

### Sprint 25C — Polish Desktop ✅
- **C-1+C-3** MainWindow: iconos emoji en sidebar + `updateActiveSidebarButton` aplica CSS `sidebar-nav-button-active`
- **C-2** DashboardView: `loadingLabel()` devuelve `HBox(ProgressIndicator + Label)`
Build: `mvn compile` — BUILD SUCCESS

### Sprint 25D — Polish iOS ✅
- **D-1** Nuevo `Spacing.kt` (commonMain) — objeto Spacing idéntico al de Android
- **D-2** `RecipeListScreen.kt`: RecipeCard rica (header 152dp + gradiente + MetaChips) + `AnimatedEmptyState` (internal, reutilizada en 4 pantallas)
- **D-3** Empty states animados en RecipeList, NotesScreen, StockScreen, MenuScreen (emoji pulsante alpha 0.5→1.0)
- **D-4** `animateItem()` en RecipeList, NotesScreen, StockScreen (via `Box(Modifier.animateItem())`)
- **D-5** `DayMenuCard(isToday: Boolean)` — `primaryContainer` + chip "Hoy"
- **D-6** `PullToRefreshBox` en RecipeListScreen y StockScreen iOS

## Sprint 34 — Candidatos

### Prioridad Alta
1. **Backend + Android + iOS:** Invitar miembro a familia — `POST /api/v1/families/{id}/members` (email + rol); formulario en Android ProfileScreen + iOS SettingsScreen; flujo join-by-invite
2. **Backend + clientes:** Validar magic bytes en upload imágenes (avatar + fotos receta) — deuda VibeSec Sprint 31
3. **Backend + Desktop:** Token revocation al expulsar miembro — `RefreshTokenService.revokeAllForUser(userId)` — deuda VibeSec Sprint 33

### Prioridad Media
4. **Android:** Widget receta del día mejorado (foto + acción "Cocinar desde widget")
5. **iOS:** Notificaciones caducidad stock (iOS Background Tasks + UserNotifications)
6. **Desktop:** Avatar upload desde Desktop (paridad completa con Android/iOS)

### Prioridad Baja
7. **iOS:** SharedElementTransition lista→detalle RecipeListScreen (paridad con Android Sprint 23)
