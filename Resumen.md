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
- CRUD completo: recetas, ingredientes, pasos, stock, menus, listas de compra, favoritos, notas, fotos.
- Sync pull/push con tombstones, LWW y deteccion de conflictos.
- 57 tests, 0 fallos.
- Hardening HTTP: CSP, HSTS, CORS deny-by-default.
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

### Android Kotlin + Compose (SPRINT 6 COMPLETO — 2026-05-28)

Stack completo verificado:
- AGP 9.2.0 + Kotlin 2.3.20 + KSP 2.3.7
- Compose + Material 3 (BOM 2026.05.00)
- Retrofit 3 + OkHttp 5 + Room 2.8.4 (v2) + WorkManager 2.11.2
- MVVM + AppContainer (DI manual) + EncryptedSharedPreferences

Pantallas implementadas (Sprint 1-6):
- LoginScreen
- RecipeListScreen + RecipeDetailScreen (ingredientes + pasos desde Room)
- StockScreen (badges bajo stock, colores caducidad)
- ShoppingListScreen
- NotesScreen (CRUD completo: crear, editar, eliminar notas familiares)
- Bottom Navigation: 4 tabs (RECIPES, STOCK, SHOPPING, NOTES)

Compilar y desplegar:
```
# Desde android/
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

SDK: C:\Users\GipsyDavy\AndroidSDK
AVD: Pixel_9_Pro (API 36)
API base URL en emulador: http://10.0.2.2:8080/

### Desktop JavaFX (SPRINT 6 + FIXES COMPLETO — 2026-05-28)

JavaFX 21 + OkHttp + Gson. Compila y genera fat JAR (13.3 MB).
mvn compile — EXITOSO.

Pantallas implementadas (Sprint 1-6):
- LoginView
- DashboardView (GridPane 2 columnas: recetas recientes + stock expirando)
- RecipeListView (SplitPane filtrable + detalle + edicion)
- RecipeDetailView (ingredientes, pasos, Editar, Eliminar)
- RecipeFormDialog (modal crear/editar)
- StockView (TableView — solo lectura, CRUD pendiente Sprint 7)
- WeeklyMenuView (calendario semanal, CRUD assign/remove)
- ShoppingListView
- NotesView (SplitPane lista + editor inline, CRUD completo)

Sidebar: Inicio | Recetas | Stock | Menu semanal | Lista de la compra | Notas familiares

Ejecutar: `mvn javafx:run -Dapi.base.url=http://localhost:8080/`

Fixes criticos en commit 5404a7b (no revertir):
- StockDtos: name (NO ingredientName)
- RecipeDtos: campos alineados con backend (quantity Double, position, instruction, timerMinutes, items/totalItems)
- StockRepository: /stock-items (NO /stock)
- NoteRepository: /notes (NO /family-notes)
- SyncRepository: actualiza cache menu+shopping+favorites
- AppContext: SyncRepository recibe 6 repos

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
| 4 | 2026-05-28 | Persistencia tokens Desktop, CRUD menu semanal, MIGRATION_1_2 Android |
| 5 | 2026-05-28 | ShoppingListView Desktop+Android, FavoriteRepository Desktop+Android |
| 6 | 2026-05-28 | NotesView Desktop + NotesScreen Android (CRUD completo) |
| fix | 2026-05-28 | 8 bugs contratos DTO Desktop + URLs endpoints (commit 5404a7b) |
| 7.1 | 2026-05-28 | CRUD Stock Items Desktop: StockFormDialog + StockRepository create/update/delete + toolbar StockView |

## Proximos Pasos — Sprint 7 (EN CURSO)

Orden recomendado por prioridad:

1. **CRUD Stock Items Desktop** ✅ — StockFormDialog, create/update/delete en StockRepository, toolbar + columna "Min. stock" en StockView.
2. **CRUD Stock Items Android** — StockScreen solo es lectura. Anadir FAB + StockForm composable, metodos en RecetasApi + StockRepository + ViewModel.
3. **Crear/Editar Receta Android** — RecipeListScreen tiene FAB sin accion. Implementar RecipeFormScreen composable.
4. **SyncWorker Android push** — Actualmente solo hace PULL. Implementar pushPendingChanges() para subir cambios offline.

Ver CONTINUAR.md para detalle exacto de archivos a tocar en cada tarea.
