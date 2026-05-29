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

### Backend Spring Boot (COMPLETO — SIN TOCAR)

- Spring Boot 3.5.14 + Java 21 + MySQL + Flyway.
- Auth JWT + refresh tokens + rate limiting.
- CRUD completo: recetas, ingredientes, pasos, stock, menus, listas de compra, favoritos, notas, fotos, valoraciones.
- Sync pull/push con tombstones, LWW y deteccion de conflictos.
- **62 tests, 0 fallos** (incluye RecipeRatingControllerTest x5).
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

### Android Kotlin + Compose (SPRINT 19 COMPLETO — 2026-05-29)

Stack completo verificado:
- AGP 9.2.0 + Kotlin 2.3.20 + KSP 2.3.7
- Compose + Material 3 (BOM 2026.05.00)
- Retrofit 3 + OkHttp 5 + Room 2.8.4 (v2) + WorkManager 2.11.2
- MVVM + AppContainer (DI manual) + EncryptedSharedPreferences

Pantallas implementadas (Sprint 1-15 + UI):
- **LoginScreen** rediseñada — ícono brand circular, tipografía centrada, botón "Entrar →"
- TopAppBar con búsqueda global unificada (Recetas + Stock + Notas)
- **RecipeListScreen** — **tarjetas visuales** con gradiente + placeholder + chips (⏱ tiempo, dificultad, porciones); paginación; búsqueda; FilterChips dificultad; pull-to-refresh; **FAB "+"**
- **RecipeDetailScreen** — `←` IconButton back; ❤️ favorito; ⋮ menú; fotos carrusel; valoraciones; **ExtendedFAB "▶ Cocinar"** visible
- RecipeForm (SegmentedButton dificultad, filas dinámicas ingredientes/pasos)
- CookingScreen (paso a paso, temporizador countdown, keep screen on, **volumen ↑↓ cambia paso**)
- **StockScreen** — badges bajo stock, colores caducidad, Sort toggle, FAB crear, CRUD inline, notificaciones caducidad; **`←` IconButton back; botones ✏ Editar / 🗑 Eliminar con ícono**
- **ShoppingListScreen** — `←` IconButton back; check offline-resilient; **tachado en ítems marcados**; botón "Compartir"
- **NotesScreen** — `←` IconButton back; botones ✏ Editar / 🗑 Eliminar con ícono; CRUD completo; búsqueda; empty states
- GlobalSearchScreen (resultados agrupados entre tabs)
- **MenuScreen** (5º tab "Menú", navegación ← → semanas, CRUD assign/remove, "Ver receta")
- Widgets: RecipeWidget (receta del día) + StockWidget (ítems críticos)
- Bottom Navigation: **5 tabs** (RECIPES, STOCK, SHOPPING, NOTES, MENU)
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

### Desktop JavaFX (SPRINT 20 COMPLETO — 2026-05-29)

JavaFX 21 + OkHttp 4.12.0 + Gson. Compila y genera fat JAR (13.3 MB).
mvn compile — EXITOSO.

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

### iOS KMP + Compose Multiplatform (SPRINT 20 COMPLETADO — 2026-05-29)

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

Compilacion: requiere macOS + Xcode para generar binario/framework.
En Windows: edicion Kotlin completa via Android Studio.

Pendiente Sprint 21:
- Pull-to-refresh manual en RecipeListScreen y StockScreen
- Navegación semanas en MenuScreen (requiere kotlinx.datetime)

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

## Proximos Pasos — Sprint 25

Prioridad alta:
- **Android**: Drag-to-reorder ingredientes y pasos en `RecipeForm`.
- **iOS**: `NotesScreen` crear/editar notas (actualmente solo lectura).
- **Desktop + Android**: DashboardView con menú del día actual real.

Prioridad media:
- **iOS + Android**: Onboarding primera vez (3 pantallas, mostrar una sola vez).
- **iOS**: `StockScreen` CRUD crear/editar items.
- **Android**: Hint visual swipe en `CookingScreen`.
