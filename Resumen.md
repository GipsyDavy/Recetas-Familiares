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

### Backend Spring Boot (COMPLETO)

- Spring Boot 3.5.14 + Java 21 + MySQL + Flyway.
- Auth JWT + refresh tokens + rate limiting.
- CRUD completo: recetas, ingredientes, pasos, stock, menus, listas de compra, favoritos, notas, fotos.
- Sync pull/push con tombstones, LWW y deteccion de conflictos.
- 57 tests, 0 fallos.
- Hardening HTTP: CSP, HSTS, CORS deny-by-default.
- OpenAPI/Swagger desactivado en produccion.
- Seed de desarrollo opcional.

### Android Kotlin + Compose (EN PROGRESO - Sprint 2 completado)

Corregidos en Sprint 2:
- `isLoggedIn` como `StateFlow<Boolean>` — navegacion reactiva.
- `SyncPullDto` con las 11 colecciones del backend.
- `SyncPushRequestDto` tipado — contrato push correcto.
- `TokenRefreshAuthenticator` — renovacion automatica de tokens ante 401.
- `SessionStore` con `EncryptedSharedPreferences` — tokens cifrados.
- Singleton `AppContainer` en `RecetasApplication`.
- `SyncWorker` usa el singleton (no instancia nuevo `AppContainer`).
- Logging `NONE` en release.
- Room version 2: 10 entidades + 10 DAOs completos.
- Sync incremental con `lastSyncTime` y `serverTime`.
- `allowBackup=false`.

Bloqueante pendiente: instalar Android SDK y verificar compilacion.

### Desktop JavaFX (Sprint 1 completado)

Scaffold completo. JavaFX 21 + OkHttp + Gson. Login funcional, lista de recetas con SplitPane, detalle con ingredientes y pasos, stock en TableView, sidebar con navegacion y sync. CSS paleta calida. module-info Java 9+. Ejecutar: `mvn javafx:run -Dapi.base.url=http://localhost:8080/`.

### Base de Datos MySQL

9 migraciones Flyway. 14 tablas principales con soft delete, syncVersion y UUID como PK.
