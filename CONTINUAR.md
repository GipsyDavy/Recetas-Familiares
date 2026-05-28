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

---

## Android Kotlin + Compose (VERIFICADO EN EMULADOR — 2026-05-27)

Stack:
- AGP 9.2.0 + Kotlin 2.3.20 + KSP 2.3.7
- Compose + Material 3 (BOM 2026.05.00)
- Retrofit 3 + OkHttp 5
- Room 2.8.4 (version DB: 2)
- WorkManager 2.11.2
- security-crypto 1.1.0-alpha06
- MVVM sin DI framework (AppContainer manual)

### Verificado funcionando (2026-05-27)
- Login contra backend real: OK
- Lista de recetas cargada desde API: OK
- Bottom Navigation (Recetas / Stock): OK
- Cleartext HTTP a emulador (10.0.2.2): OK via network_security_config.xml

### Fixes aplicados (no revertir)
- AGP 9 DSL completa: `kotlin { compilerOptions { jvmTarget = JVM_11 } }`, sin `kotlinOptions`, sin plugin `org.jetbrains.kotlin.android`
- KSP: `2.3.7` (alineado con Kotlin 2.3.20)
- `org.gradle.jvmargs=-Xmx4g` (D8 OutOfMemoryError)
- SSL PKIX: `org.gradle.jvmargs` incluye `-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NUL`
- `res/xml/network_security_config.xml`: permite cleartext a `10.0.2.2`
- `AndroidManifest.xml`: `android:networkSecurityConfig="@xml/network_security_config"`

### Arquitectura Android implementada
- `RecetasApplication` → `AppContainer` singleton (sessionStore, database)
- `SessionStore` → `EncryptedSharedPreferences` (tokens + lastSyncTime)
- `ApiClient` → OkHttp con `TokenRefreshAuthenticator` (cliente refresh separado)
- Room v2: 10 entidades, 10 DAOs con `@Upsert`
- `SyncWorker` → WorkManager, sync incremental con `lastSyncTime`
- `isLoggedIn`: `StateFlow<Boolean>` reactivo
- `SyncPullDto` con las 11 colecciones del backend
- Logging: `BASIC` en debug, `NONE` en release

### Pantallas implementadas
- `LoginScreen` — campos email/password, boton Entrar, error reactivo
- `RecipeListScreen` — lista de recetas desde Room/API, boton Actualizar
- Bottom Navigation: tabs Recetas y Stock

### Completado Android (Sprint 3 — 2026-05-28)
1. `RecipeDetail` — tap en receta → pantalla con ingredientes + pasos desde Room (flows reactivos via ViewModel) ✓
2. `StockScreen` — pantalla Stock mejorada: badges "Bajo stock", color expiry (rojo ≤3d, naranja ≤7d), empty state ✓
3. ViewModel: `ingredientsFor(recipeId)` y `stepsFor(recipeId)` exponen flows de Room ✓

### Pendiente Android (Sprint 4)
1. WorkManager sync automatico (ya programado cada 30 min, falta activar constraints en background real)
2. Reemplazar `fallbackToDestructiveMigration` con migraciones Room explicitas (antes de beta)

---

## Desktop JavaFX (SPRINT 2 COMPLETO — 2026-05-27)

Stack: Java 21 + JavaFX 21.0.2 + OkHttp 4.12.0 + Gson 2.10.1 + Maven.

Fat JAR: 13.3 MB (`desktop/target/recetas-familiares-desktop-*.jar`).

SSL fix: `desktop/.mvn/jvm.config` con Windows-ROOT truststore.

### Pantallas implementadas (Sprint 1 + 2)
- `LoginView` — formulario login con feedback de error
- `DashboardView` — GridPane 2 columnas: recetas recientes (60%) + stock expirando + acciones (40%)
- `RecipeListView` — SplitPane lista filtrable + detalle
- `RecipeDetailView` — ingredientes, pasos, botones Editar + Eliminar con confirmacion
- `RecipeFormDialog` — modal unico con `forCreate()` / `forEdit()` (pre-rellena campos en edicion)
- `StockView` — TableView con ingrediente, cantidad, fecha caducidad
- `MainWindow` — sidebar "Inicio / Recetas / Stock", boton Sincronizar, logout

### Completado Desktop (Sprint 3 — 2026-05-28)
1. `WeeklyMenuView` — calendario GridPane 8×5 (Lun-Dom × Desayuno/Comida/Cena/Merienda), nav semana anterior/hoy/siguiente, highlight del día actual, celdas rellenas con recipeTitle + note ✓
2. `MenuRepository` — llama a `/api/v1/families/{id}/menu-items?weekStart=...` ✓
3. Sidebar: botón "Menú semanal" agregado ✓
4. `SyncDtos.MenuDtos.MenuItemDto` expandido con todos los campos reales ✓

### Pendiente Desktop (Sprint 4)
1. Persistencia de tokens entre reinicios (OS keystore)
2. Asignación de recetas desde WeeklyMenuView (CRUD sobre menu-items)

---

## Estado Git

Rama: `main`

Commits locales pendientes de push (no se ha hecho push):
```
ff73840 Scaffold Android client
a58ebca Update continuation notes after backend stabilization
2aad060 Stabilize backend OpenAPI and dev seed data
40cb14d v1
ea353f0 Harden backend and add creator logo
```

Los cambios de las sesiones de Sprint 2 Desktop y verificacion Android **no estan commiteados todavia**.

Archivos modificados sin commit:
- `android/app/build.gradle.kts` (AGP 9 migration)
- `android/build.gradle.kts` (KSP 2.3.7)
- `android/gradle.properties` (Xmx4g + SSL fix)
- `android/app/src/main/AndroidManifest.xml` (networkSecurityConfig)
- `android/app/src/main/res/xml/network_security_config.xml` (nuevo)
- `desktop/` — todo el modulo Desktop (Sprint 1 + 2)

---

## Procedimiento al retomar

1. Abrir raiz: `C:\Users\GipsyDavy\MAVEN\Recetas Familiares`

2. Leer: `CLAUDE.md`, `Resumen.md`, este `CONTINUAR.md`

3. Comprobar estado git:
   ```powershell
   git status --short --branch
   ```

4. Arrancar MySQL (si no esta corriendo):
   ```powershell
   Start-Service MySQL80
   ```

5. Arrancar backend desde Bash tool (ver seccion "Arranque del entorno dev").

6. Verificar backend:
   ```bash
   curl -s http://localhost:8080/actuator/health
   ```
   Nota: actuator esta protegido en dev, respuesta 401 = backend corriendo.

7. Continuar con el Sprint 3 segun prioridad (ver pendientes Android/Desktop arriba).

---

## Deuda tecnica conocida y aceptada

- `fallbackToDestructiveMigration` en Room: cambiar por migraciones explicitas antes de beta.
- Push sync Android envia listas vacias (sin cola de cambios offline). Correcto para MVP.
- Sync pull sin paginacion: aceptable para familias pequenas.
- Tokens Desktop solo en memoria: se pierden al reiniciar. Pendiente keystore OS.
- Login devuelve primera familia (no determinista si hay varias): limitacion documentada para MVP.
- Advertencia Mockito/Byte Buddy con Java 26: no rompe build ni tests.
