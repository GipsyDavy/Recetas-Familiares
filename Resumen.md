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
- Seed de desarrollo: demo@recetas.local / Demo1234!Familia

**Arranque dev:**
```
java -jar backend/target/recetas-familiares-backend-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  --spring.datasource.password=Recetas2024! \
  --app.dev.seed-data.enabled=true \
  --app.dev.seed-data.email=demo@recetas.local \
  --app.dev.seed-data.password=Demo1234!Familia \
  --app.dev.seed-data.display-name=Demo \
  --app.dev.seed-data.family-name=FamiliaDemo
```
O desde bash (evita problemas con ! en PowerShell):
```bash
java -jar "backend/target/..." --spring.profiles.active=dev \
  "--spring.datasource.password=Recetas2024!" \
  "--app.dev.seed-data.password=Demo1234!Familia" ...
```

### Android Kotlin + Compose (SPRINT 2 COMPLETO — VERIFICADO EN EMULADOR)

Stack completo verificado end-to-end el 2026-05-27:
- Login exitoso contra backend real
- Pantalla Recetas con lista cargada desde API
- Bottom Navigation con tabs Recetas y Stock

Fixes aplicados en esta sesion:
- AGP 9 DSL migration completa (kotlin compilerOptions, sin kotlinOptions deprecated)
- KSP 2.3.0 → 2.3.7 (alineado con Kotlin 2.3.20)
- org.gradle.jvmargs=-Xmx4g (D8 OutOfMemoryError)
- SSL PKIX fix: Windows-ROOT truststore en gradle.properties
- `network_security_config.xml` → cleartext HTTP permitido a 10.0.2.2 (emulador)
- AndroidManifest.xml con android:networkSecurityConfig

Arquitectura Android:
- MVVM + Repository Pattern
- Retrofit + OkHttp (TokenRefreshAuthenticator para JWT refresh automatico)
- Room v2: 10 entidades + 10 DAOs
- WorkManager (SyncWorker incremental con lastSyncTime)
- EncryptedSharedPreferences (SessionStore)
- AppContainer singleton en RecetasApplication

Compilar y desplegar:
```
# Desde android/
gradle assembleDebug          # usa C:\tmp\tools\gradle-9.5.1
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

SDK: C:\Users\GipsyDavy\AndroidSDK
AVD: Pixel_9_Pro (API 36, Compose)
API base URL en emulador: http://10.0.2.2:8080/

Deuda tecnica pendiente:
- Reemplazar fallbackToDestructiveMigration con migraciones Room explicitas (antes de beta)
- Pantalla detalle de receta (tap en item → ver ingredientes + pasos)
- Pantalla Stock funcional
- WorkManager sync automatico en background

### Desktop JavaFX (SPRINT 2 COMPLETO)

JavaFX 21 + OkHttp + Gson. Compila y genera fat JAR (13.3 MB).

Sprint 1: Login, RecipeList (SplitPane + filtro), StockView (TableView), MainWindow (sidebar), CSS paleta calida.
Sprint 2: Dashboard GridPane 2 columnas (recetas recientes col0=60% + stock expirando+acciones col1=40%), RecipeFormDialog con forCreate()/forEdit(), RecipeDetailView con Editar+Eliminar, sidebar "Inicio/Recetas/Stock".

Ejecutar: `mvn javafx:run -Dapi.base.url=http://localhost:8080/`

SSL fix: desktop/.mvn/jvm.config con -Djavax.net.ssl.trustStoreType=Windows-ROOT

Deuda tecnica pendiente:
- Vista Menus semanales (no implementada)
- Persistencia de tokens en OS keystore (actualmente en memoria)

### Base de Datos MySQL

- MySQL80 service en localhost:3306
- Usuario: recetas_app / Recetas2024!
- Base de datos: recetas_familiares
- 9 migraciones Flyway. 14 tablas principales con soft delete, syncVersion y UUID como PK.

---

## Sprint 3 Completado (2026-05-28)

### Android
- `RecipeDetail`: tap en receta → ingredientes + pasos reactivos desde Room via ViewModel flows
- `StockScreen`: mejorada con badges "Bajo stock", colores de caducidad (rojo ≤3d, naranja ≤7d), empty state
- ViewModel: `ingredientsFor()` y `stepsFor()` métodos expuestos

### Desktop
- `WeeklyMenuView`: calendario semanal GridPane 8×5 (Lun-Dom × 4 comidas), navegación de semanas, highlight hoy
- `MenuRepository`: integración con endpoint `/api/v1/families/{id}/menu-items`
- Sidebar: botón "Menú semanal" añadido

## Proximos Pasos Recomendados (Sprint 4)

1. **Desktop — Asignar recetas al menú**: CRUD desde WeeklyMenuView (POST/DELETE menu-items)
2. **Android — WorkManager automático**: activar sync background real con constraints de red
3. **Android — Migraciones Room explícitas**: reemplazar fallbackToDestructiveMigration antes de beta
4. **Desktop — Persistencia de tokens**: guardar tokens en OS keystore entre reinicios
