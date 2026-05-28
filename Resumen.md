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

### Android Kotlin + Compose (SPRINT 11 COMPLETO — 2026-05-28)

Stack completo verificado:
- AGP 9.2.0 + Kotlin 2.3.20 + KSP 2.3.7
- Compose + Material 3 (BOM 2026.05.00)
- Retrofit 3 + OkHttp 5 + Room 2.8.4 (v2) + WorkManager 2.11.2
- MVVM + AppContainer (DI manual) + EncryptedSharedPreferences

Pantallas implementadas (Sprint 1-11):
- LoginScreen
- TopAppBar con búsqueda global unificada (Recetas + Stock + Notas)
- RecipeListScreen (paginación, búsqueda, pull-to-refresh, FAB crear) + RecipeDetailScreen (fotos carrusel, valoraciones, menú ⋮)
- RecipeForm (SegmentedButton dificultad, filas dinámicas ingredientes/pasos)
- CookingScreen (paso a paso, temporizador countdown, keep screen on)
- StockScreen (badges bajo stock, colores caducidad, FAB crear, CRUD inline, notificaciones caducidad)
- ShoppingListScreen (check offline-resilient)
- NotesScreen (CRUD completo, búsqueda, empty states)
- GlobalSearchScreen (resultados agrupados entre tabs)
- Widgets: RecipeWidget (receta del día) + StockWidget (ítems críticos)
- Bottom Navigation: 4 tabs (RECIPES, STOCK, SHOPPING, NOTES)
- Snackbar feedback en todas las mutaciones
- SyncWorker pushThenPull offline-resilient: Recetas + Stock + Notas + Shopping items

Compilar y desplegar:
```
# Desde android/
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

SDK: C:\Users\GipsyDavy\AndroidSDK
AVD: Pixel_9_Pro (API 36)
API base URL en emulador: http://10.0.2.2:8080/

### Desktop JavaFX (SPRINT 11 COMPLETO — 2026-05-28)

JavaFX 21 + OkHttp 4.12.0 + Gson. Compila y genera fat JAR (13.3 MB).
mvn compile — EXITOSO.

Pantallas implementadas (Sprint 1-11):
- LoginView
- DashboardView (GridPane 2 columnas: recetas recientes + stock expirando)
- RecipeListView (SplitPane filtrable, búsqueda, paginación incremental 30/pág, botón "Actualizar")
- RecipeDetailView (ingredientes, pasos, fotos async, Editar, Eliminar, Modo Cocina)
- RecipeFormDialog (modal crear/editar)
- CookingView (Stage maximizado, paso a paso, temporizador JavaFX Timeline)
- StockView (TableView con toolbar CRUD, búsqueda, botón "Actualizar")
- WeeklyMenuView (calendario semanal, CRUD assign/remove, botón "Actualizar")
- ShoppingListView (empty state, botón "Actualizar" → sync completo)
- NotesView (SplitPane lista + editor inline, CRUD, búsqueda, botón "Actualizar")
- GlobalSearchView (búsqueda unificada Recetas/Stock/Notas desde sidebar)

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

## Proximos Pasos — Sprint 12 (PENDIENTE)

Candidatos:

1. **Notificaciones caducidad Desktop** — equivalente al WorkManager Android; alerta de stock próximo a caducar.
2. **Paginación notas/stock Desktop** — las vistas cargan todo de una vez; añadir carga incremental.
3. **Offline-resilient favoritos** — `toggleFavorite()` con fallback local (patrón stock/notas).
4. **Modo manos libres CookingScreen Android** — control por voz o gestos para avanzar pasos.
5. **Exportar receta PDF/compartir** — generar un PDF o texto de la receta para compartir.
6. **Pull-to-refresh Desktop** — sincronización manual desde la UI.

Ver CONTINUAR.md para contexto técnico completo.
