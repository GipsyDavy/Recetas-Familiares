Resumen Descriptivo Completo - "Recetas Familia"
"Recetas Familia" es una aplicacion premium multiplataforma disenada para ayudar a las familias a organizar, compartir y disfrutar de su cocina de forma moderna, emocional y eficiente.

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

## Estilo Visual y UX

- Estilo: Calido, moderno, premium y emocional (Notion + Material You + Apple Design).
- Paleta de colores: Tonos tierra, verdes suaves, naranjas y amarillos apetecibles.
- Dark Mode espectacular y Light Mode acogedor.
- Micro-interacciones suaves y satisfactorias.

## Diferenciadores Clave

- Enfoque familiar real (no solo individual).
- Historia y memoria emocional de las recetas.
- Inteligencia practica (sugerencias segun stock, temporada, preferencias).
- Experiencia coherente y sincronizada entre movil y escritorio.
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

### Android Kotlin + Compose (SPRINT 15 COMPLETO — 2026-05-29)

Stack completo verificado:
- AGP 9.2.0 + Kotlin 2.3.20 + KSP 2.3.7
- Compose + Material 3 (BOM 2026.05.00)
- Retrofit 3 + OkHttp 5 + Room 2.8.4 (v2) + WorkManager 2.11.2
- MVVM + AppContainer (DI manual) + EncryptedSharedPreferences

Pantallas implementadas (Sprint 1-14):
- LoginScreen
- TopAppBar con búsqueda global unificada (Recetas + Stock + Notas)
- RecipeListScreen (paginación, búsqueda, pull-to-refresh, FAB crear) + RecipeDetailScreen (fotos carrusel, valoraciones, menú ⋮ con **"Compartir"**)
- RecipeForm (SegmentedButton dificultad, filas dinámicas ingredientes/pasos)
- CookingScreen (paso a paso, temporizador countdown, keep screen on, **volumen ↑↓ cambia paso**)
- StockScreen (badges bajo stock, colores caducidad, FAB crear, CRUD inline, notificaciones caducidad)
- ShoppingListScreen (check offline-resilient, **botón "Compartir" → Intent.ACTION_SEND**)
- NotesScreen (CRUD completo, búsqueda, empty states)
- GlobalSearchScreen (resultados agrupados entre tabs)
- **MenuScreen** (5º tab "Menú", navegación ← → semanas, cards por día, mealType localizado, empty state)
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

### Desktop JavaFX (SPRINT 15 COMPLETO — 2026-05-29)

JavaFX 21 + OkHttp 4.12.0 + Gson. Compila y genera fat JAR (13.3 MB).
mvn compile — EXITOSO.

Pantallas implementadas (Sprint 1-14):
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

### Base de Datos MySQL

- MySQL80 service en localhost:3306
- Usuario: recetas_app / Recetas2024!
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

## Proximos Pasos — Sprint 16

Ver CONTINUAR.md para contexto técnico completo.
