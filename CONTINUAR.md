# CONTINUAR.md - Estado Operativo Recetas Familiares

Este documento sirve para retomar trabajo en una nueva sesion sin perder el estado operativo. No sustituye a `CLAUDE.md`.

- Reglas de trabajo, seguridad y cierre: `CLAUDE.md`.
- Vision de producto: `Resumen.md`.
- Sistema visual y UX: `Interfaz.md`.
- Auditoria historica: `auditoria.md`.
- Plantilla para otros agentes: `MACRO-PROMPT-RECETAS-FAMILIA.md`.

---

## 1. Raiz Correcta

Abrir siempre la raiz del monorepo:

```text
C:\Users\GipsyDavy\MAVEN\Recetas Familiares
```

No abrir como proyecto principal:
- `android/`
- `desktop/`
- `ios/`
- carpetas antiguas locales

---

## 2. Estado Actual Consolidado

Recetas Familiares es una aplicacion premium multiplataforma para familias: recetas, stock, menus, lista de compra, notas, fotos, miembros y sincronizacion. El chat familiar en tiempo real queda registrado como funcionalidad futura, no implementada todavia.

Plataformas:
- `backend/`: Spring Boot + MySQL + Flyway + JWT.
- `android/`: Kotlin + Compose + Room + WorkManager.
- `desktop/`: JavaFX + Maven + HTTP API client.
- `ios/`: KMP + Compose Multiplatform + Ktor + SQLDelight.
- `shared/`: objetivo para logica compartida Android/iOS cuando aplique. Aun no existe como modulo en el repo; iOS mantiene su propia copia de DTOs y logica bajo `ios/composeApp/`.

Estado conocido a partir de la documentacion previa:
- Backend: 92 tests, 0 fallos en la ultima validacion documentada.
- Android: funcional, con offline-first y UI avanzada.
- Desktop: funcional, instalador Windows v1.1 generado, ajustes como vista central.
- iOS: funcional parcialmente; metadata KMP compila en Windows, pero el target iOS nativo sigue con deuda de compilacion/paridad.

Antes de afirmar estado actual, ejecutar validaciones reales en la sesion.

---

## 3. Protocolo De Inicio De Sprint

Antes de ejecutar un sprint:
- Leer `CLAUDE.md`, este `CONTINUAR.md` y los archivos fuente/documentales afectados.
- Leer `Interfaz.md` si hay UI/UX, accesibilidad, animaciones, onboarding o ayuda contextual.
- Leer `auditoria.md` si el sprint toca deuda `SEC-*`, `COD-*` o `UX-*`.
- Usar `MACRO-PROMPT-RECETAS-FAMILIA.md` para preparar bloques de apoyo si se consulta otro agente IA.
- Decidir que agentes IA, skills o revisiones hacen falta antes de editar.
- Si no hacen falta agentes o skills, dejarlo justificado de forma breve.
- Definir validaciones esperadas antes de implementar.

Protocolo multiagente del IDE:
- Claude Code es siempre el agente principal.
- Codex se usa como apoyo tecnico para codigo, arquitectura, tests, build, dependencias, CI y consistencia tecnica.
- Gemini se usa como apoyo transversal para producto, interfaz, UX, documentacion, duplicidades, ruido e inconsistencias globales.
- Si Claude Code no puede invocarlos directamente, debe escribir bloques listos para copiar y pegar en Codex y Gemini.
- En auditorias o revisiones sin cambios, los bloques deben indicar expresamente `solo lectura, no modificar archivos`.
- Al finalizar una auditoria o revision, si hay cambios necesarios, Claude Code debe pedir autorizacion explicita y ofrecer opciones de alcance antes de editar.

Apoyo IA recomendado:
- Seguridad/auth/ownership: usar revision de seguridad y, si procede, segunda opinion.
- Sincronizacion/offline/API compartida: pedir apoyo si hay riesgo multiplataforma.
- UI principal o sistema visual: revisar contra `Interfaz.md` y pedir segunda opinion si el cambio es grande.
- Migraciones o cambios de datos: revisar impacto backend/clientes antes de tocar codigo.

---

## 4. Entorno de Desarrollo

### Java
- Objetivo del proyecto: Java 21 LTS.
- Evitar empaquetar releases con JDK no LTS salvo decision explicita.

### Gradle / Android
- Gradle global documentado previamente: `C:\tmp\tools\gradle-9.5.1\bin\gradle.bat`.
- `gradlew` disponible en `android/`.
- Android SDK local esperado: `C:\Users\GipsyDavy\AndroidSDK`.
- AVD documentado: `Pixel_9_Pro`.

### Maven / Desktop
- Maven disponible en PATH mediante NetBeans en este equipo.
- Desktop se ejecuta desde `desktop/` con Maven.

### Base de Datos
- MySQL local: `localhost:3306`.
- Base documentada: `recetas_familiares`.
- Usuario de aplicacion: `recetas_app`.
- No documentar passwords reales en archivos versionables. Usar variables de entorno, parametros locales o secretos no versionados.

---

## 5. Arranque Dev

### Backend

Ejemplo usando placeholders. Sustituir valores localmente sin escribir secretos reales en documentacion versionable:

```bash
java -jar backend/target/recetas-familiares-backend-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  "--spring.datasource.password=<DB_PASSWORD>" \
  "--app.security.jwt.secret=<JWT_SECRET_32_BYTES_MINIMO>" \
  "--app.dev.seed-data.enabled=true" \
  "--app.dev.seed-data.email=demo@recetas.local" \
  "--app.dev.seed-data.password=<DEMO_PASSWORD>" \
  "--app.dev.seed-data.display-name=Demo" \
  "--app.dev.seed-data.family-name=FamiliaDemo"
```

### Android

```powershell
cd android
.\gradlew assembleDebug
```

Instalacion en emulador:

```powershell
$adb = "C:\Users\GipsyDavy\AndroidSDK\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Desktop

```powershell
cd desktop
mvn javafx:run -Dapi.base.url=http://localhost:8080/
```

---

## 6. Contratos Criticos

No cambiar sin revisar Backend, Android, Desktop e iOS:

- API versionada: `/api/v1/`.
- `PageResponse<T>`: `items`, `page`, `size`, `totalItems`, `totalPages`.
- Notas: `/api/v1/families/{id}/notes`.
- Stock: `/api/v1/families/{id}/stock-items`.
- `StockItemResponse.name`, no `ingredientName`.
- `RecipeIngredientResponse`: `position`, `name`, `quantity`, `note`.
- `RecipeStepResponse`: `position`, `instruction`, `timerMinutes`.
- Entidades sincronizables: `id`, `createdAt`, `updatedAt`, `syncVersion`, `deleted`.
- Ownership familiar obligatorio en backend aunque el cliente oculte acciones.
- Chat familiar futuro: si se implementa, debe ser modulo independiente de notas, con ownership por familia, historial paginado, tiempo real, adjuntos protegidos y contrato estable antes de llevarlo a todos los clientes.

---

## 7. Estado Por Plataforma

### Backend

Implementado/documentado:
- Auth: registro, login, refresh, logout.
- Familias, miembros, roles e invitaciones.
- Recetas, ingredientes, pasos.
- Stock, menus, listas de compra.
- Favoritos, notas, fotos, valoraciones.
- Sync pull/push con tombstones y conflictos.
- `GET /api/v1/families/{id}/stats`: `totalRecipes`, `totalMembers`, `totalStockItems`, `lastActivityAt`.
- Flyway y MySQL como base principal.

Resuelto en Sprint 42 (2026-07-02):
- Fallback de secreto JWT eliminado: dev exige `JWT_SECRET` (SEC-1).
- `/uploads/**` requiere JWT; ownership fino por familia pendiente (SEC-3 parcial).
- Rate limiter proxy-aware opt-in via `app.security.rate-limit.auth.trust-proxy` (SEC-4).

Resuelto en Sprint 43 (2026-07-05):
- Paginacion opcional de `sync/pull` via `limit` + `hasMore`/`nextSince` (COD-5).
- `lastActivityAt` multi-entidad: recetas, stock, menus, listas, notas y favoritos (COD-4).
- Ownership por familia en `/uploads/**` via `UploadController` con lookup en BD (SEC-3 completo).
- Bug corregido: `/users/me`, `PUT /me` y avatar upload buscaban por email con un principal userId (404 permanente).

Endurecido tras revision Codex/Gemini post-Sprint 43 (2026-07-05):
- `/uploads/**` ya no autoriza fotos por sufijo `LIKE` ni por URL publica exacta; las fotos locales se sirven solo si la fila tiene `storage_path` interno generado por `FileStorageService`.
- `sync/pull` devuelve `serverTime` capturado antes de consultar para evitar saltos por cambios concurrentes.

Funcionalidad futura documentada:
- Chat familiar por fases: texto/emojis en tiempo real, imagenes, videos y push notifications.
- REST para historial paginado, envio inicial/fallback y operaciones de lectura.
- WebSocket/STOMP o equivalente para entrega en tiempo real con reconexion y fallback por polling.
- Storage protegido para imagenes y videos; no guardar binarios pesados directamente en MySQL.
- Requiere validar ownership familiar en cada operacion y aplicar limites de longitud, tamano, MIME, extension y rate limit.
- Produccion seria deberia contemplar miniaturas/previews, limpieza de archivos huerfanos y analisis antivirus o servicio equivalente para adjuntos.

### Android

Implementado/documentado:
- Login, recetas, detalle, formulario, favoritos y fotos.
- Stock CRUD, caducidad y notificaciones.
- Menu semanal, shopping list, notas, perfil.
- Widgets de receta y stock.
- Offline-first con Room + WorkManager.
- Tema 10 variantes x claro/oscuro/sistema.
- Modo cocina con temporizador, gestos y landscape.

Resuelto en Sprint 42 (2026-07-02):
- Timeouts en refresh authenticator (SEC-7).
- `CancellationException` re-lanzada en login (COD-7).
- `baseSyncVersion` real en push offline: dirty = syncVersion negativo conservando base (COD-3).
- Coil configurado con OkHttpClient autenticado para `/uploads/**`.

Resuelto en Sprint 43 (2026-07-05):
- Perfil consume `/stats` con fallback local offline (UX-6).
- Pull paginado con `limit=200` y tope de 50 paginas.
- Icono adaptive con `recetas.png` (mipmaps 5 dpis, fondo #F6E7D8).

Endurecido tras revision Codex/Gemini post-Sprint 43 (2026-07-05):
- Si se alcanza el tope de 50 paginas con `hasMore=true`, no avanza `lastSyncTime`; la siguiente sync reintenta sin perder filas.

Resuelto en Sprint 44 (2026-07-05):
- Fuentes Nunito/Lato empaquetadas en `res/font` (UX-1); eliminado el provider de Google Fonts por red y la dependencia `ui-text-google-fonts`.
- Primeros tests unitarios (COD-8 parcial): `SyncRepositoryTest` (pull paginado, tope de paginas, filtrado de pendientes, baseSyncVersion en push) y `StockRepositoryOfflineTest` (convencion COD-3). 11 tests con mockk + coroutines-test.
- `gradle.properties` incluye truststore Windows-ROOT (mismo fix que iOS) para resolver dependencias.

Funcionalidad futura documentada:
- Primera pantalla candidata para chat familiar tras cerrar contrato backend.
- Primera fase recomendada: historial paginado, envio de texto/emojis, indicador basico de nuevos mensajes y WebSocket con polling configurable como fallback.
- Fases posteriores: selector de imagen/video, subida segura, previews y control de progreso.
- Decidir antes de implementar si se permite envio offline con cola local.

### Desktop

Implementado/documentado:
- Login, dashboard, recetas, detalle, formulario.
- Stock, menu semanal, shopping list, notas, busqueda global.
- Modo cocina, exportaciones, notificaciones, sonidos opcionales.
- Temas, ajustes como vista central, diagnostico e instalador Windows v1.1.
- Gestion de miembros y avatar upload.

Resuelto en Sprint 42 (2026-07-02):
- Tokens cifrados con Windows DPAPI (`TokenVault`, JNA) con migracion automatica de valores legado (SEC-2).
- Carga de imagenes `/uploads/**` con Authorization en segundo plano (fotos de receta y avatar).

Resuelto en Sprint 43 (2026-07-05):
- Dashboard muestra stats familiares de `/stats` (UX-6 parcial Desktop).
- Icono de ventana e instalador regenerados desde `recetas.png` (ICO multi-res 16-256).

Endurecido tras revision Codex/Gemini post-Sprint 43 (2026-07-05):
- Si falla `/stats`, el dashboard muestra fallback local minimo con recetas cacheadas y ultima actividad local cuando existe.

Resuelto en Sprint 44 (2026-07-05):
- Instalador Windows regenerado con JDK 21 LTS (runtime 21.0.11, JNA embebido, icono nuevo): `desktop/output/RecetasFamiliares-Instalador-v1.1.exe`.
- Onboarding de primer arranque (UX-8/UX-13): `OnboardingDialog` de 4 pasos, se muestra una vez (Preferences `recetas/ui/onboardingSeen`).
- Shortcuts completos en modo cocina (UX-11): ←/→/Enter navegar, Espacio temporizador, Esc salir, tooltips y barra de pistas.
- Primeros tests unitarios (COD-8 parcial): `UpdateFromSyncTest` (filtrado de tombstones, semantica null, cache inmutable); surefire con `useModulePath=false`.

Riesgos pendientes a verificar:
- Perfil completo y stats familiares (UX-5).

Funcionalidad futura documentada:
- Chat familiar como segunda implantacion cliente despues de Android.
- Mantener comportamiento coherente con el contrato backend y evitar logica divergente.
- Soportar texto/emojis primero; imagenes/videos despues de cerrar storage protegido y limites de adjuntos.

### iOS

Implementado/documentado:
- Login, lista/detalle recetas, stock, notas, shopping, menu.
- Keychain para tokens.
- SQLDelight y Ktor.
- Pull incremental.
- Tema, ajustes, perfil, hapticos, onboarding y skeletons parciales.

Implementado en Sprint 42 (2026-07-02), SIN COMPILAR (build iOS imposible en Windows):
- Interceptor Ktor Auth bearer: refresh ante 401 + retry unico + `HttpTimeout` (SEC-6).
- Coil con HttpClient autenticado para `/uploads/**`.
- Validar compilacion y flujo real en macOS antes de dar por cerrado.

Implementado en Sprint 43 y endurecido post-revision:
- Pull paginado con tope defensivo que no avanza cursor si aun queda `hasMore=true`.
- DTOs de pull ampliados para aceptar el contrato backend completo de forma aditiva.
- Tooling actualizado a Kotlin `2.3.20` y SQLDelight `2.3.2`; `compileKotlinMetadata`, `compileKotlinIosX64`, `compileKotlinIosArm64` y `compileKotlinIosSimulatorArm64` compilan en Windows.
- Persistencia local sigue limitada al esquema SQLDelight actual: recetas, ingredientes y stock; menus, listas, favoritos, notas, fotos y pasos quedan pendientes de paridad real.

Riesgos pendientes a verificar:
- iOS ya compila los targets Kotlin/Native en Windows, pero falta validar runtime en macOS/dispositivo. `SessionStore.ios.kt` compila con warnings de casts Keychain (`NSCopyingProtocol`) que deben probarse y, si procede, reemplazarse por construccion CFDictionary mas idiomatica.
- Push sync completo (COD-1).
- Paridad con Android: busqueda, filtros, skeletons y UX de listas (UX-3).

Funcionalidad futura documentada:
- Chat familiar despues de estabilizar backend y al menos un cliente.
- Evitar abrir este frente antes de resolver refresh 401, push sync y paridad basica.
- Tiempo real y adjuntos en iOS deben llegar despues de tener contrato backend estable, previews definidos y estrategia de push notifications.

---

## 8. Bloqueantes Recomendados Para Sprint Siguiente

Sprint 44 (2026-07-05) cerro los puntos 2, 3 (parcial) y 4 de la lista anterior; el punto 1 (iOS/macOS) sigue bloqueado en Windows (ver seccion 10).

Prioridad propuesta para Sprint 45:

1. iOS: validar runtime en macOS/dispositivo (Keychain, interceptor 401, Coil autenticado, pull paginado), revisar warnings de casts Keychain y AppIcon con `recetas.png` cuando exista el proyecto Xcode (COD-1/COD-2). Bloqueado sin macOS.
2. COD-8 ampliacion: mas cobertura Android (RecipeRepository offline, favoritos, notas) y Desktop (AppSession, ApiClient con servidor fake si aporta).
3. UX Desktop: perfil completo (UX-5), ayuda contextual MVP.
4. Producto: decidir cuestiones abiertas de `docs/chat-familiar-spec.md` y, si procede, abrir sprint de chat fase 1.
5. Validacion manual de UI pendiente: onboarding y shortcuts modo cocina Desktop, fuentes empaquetadas Android en emulador.

Antes de arrancar sprint, revisar `auditoria.md` para IDs `SEC-*`, `COD-*`, `UX-*` y comprobar vigencia en codigo.

---

## 9. Validacion Esperada

Ajustar comandos al modulo tocado y al build real del repositorio.

Backend:

```bash
cd backend
mvn test
mvn -DskipTests package
```

Android:

```powershell
cd android
.\gradlew test
.\gradlew assembleDebug
```

Desktop:

```powershell
cd desktop
mvn test
mvn -DskipTests compile
```

iOS:
- Build final requiere macOS + Xcode.
- En Windows, verificar edicion Kotlin, estructura y compilacion parcial si el tooling lo permite.

No marcar `PASS`, `validado` o `cerrado` si no se ejecuto realmente en la sesion.

---

## 10. Trazabilidad Pendiente

Cuando se cierre un sprint, documentar aqui solo lo necesario:
- fecha,
- objetivo,
- archivos relevantes,
- comandos ejecutados,
- agentes IA/skills usados o justificacion de no uso,
- seguridad ejecutada,
- resultado,
- riesgos residuales.

No convertir este archivo en un historial completo de todos los sprints. Para cambios extensos usar commits, changelog o documentacion especifica.

### Sprint 42 — Hardening pre-produccion (2026-07-02)

- Objetivo: cerrar bloqueantes SEC-1/2/3/4/6/7 y COD-3/7; mover OWASP Dependency-Check a perfil Maven `security-audit`.
- Agente lider: Claude Code, en solitario (usuario no solicito Codex/Gemini para este sprint).
- Seguridad ejecutada: VibeSec y security-review invocados en la sesion.
- Backend: `mvn test` 78 tests, 0 fallos (2 tests nuevos: uploads 401 y X-Forwarded-For no falsificable).
- Android: `gradlew assembleDebug` OK; `gradlew test` sin fuentes de test (NO-SOURCE, deuda COD-8).
- Desktop: `mvn compile` OK; sin tests unitarios existentes (deuda COD-8).
- iOS: cambios aplicados sin compilar (Windows); validar en macOS. Riesgo residual.
- Convencion sync offline Android: dirty = `syncVersion` negativo (base preservada); pendiente = `syncVersion <= 0`. Documentada en `Repositories.kt`.
- Nueva dependencia Desktop: JNA (jna-jpms + jna-platform-jpms) para DPAPI.
- `mvn verify -P security-audit` ejecuta Dependency-Check (requiere `NVD_API_KEY`); los builds normales ya no lo exigen.
- Arranque dev backend ahora requiere `JWT_SECRET` definido (sin fallback).
- Riesgo residual: `/uploads/**` autenticado pero sin ownership por familia (URLs UUID no adivinables); conflicto de push devuelve error de lote completo (server gana tras pull).

### Revision Sprint 42 — Aplicacion hallazgos Codex/Gemini (2026-07-02)

- Objetivo: integrar hallazgos tecnicos/documentales reportados por Codex y Gemini tras Sprint 42.
- Agente en esta sesion: Codex, retomando cambios parciales dejados por Claude Code a solicitud del usuario.
- Seguridad ejecutada: VibeSec; no se invoco `security-review` porque no esta disponible como herramienta en esta sesion.
- Android: `pullOnce()` y refresh de recetas/stock ya no pisan filas locales `syncVersion <= 0`; entidades creadas offline y borradas antes de sincronizar se eliminan localmente para evitar tombstones perpetuos.
- Desktop/iOS: retries por 401 y refresh de token solo responden a URLs del mismo origen del API; imagenes externas no reciben Bearer.
- Desktop: si DPAPI/JNA falla en Windows, los tokens no se persisten en claro.
- Documentacion: README, `auditoria.md` y trazabilidad de `CONTINUAR.md` actualizadas con estado Sprint 42 y IDs historicos.
- Validacion ejecutada: `git diff --check` OK; Android `gradlew assembleDebug` OK; Android `gradlew test` OK sin tests (`NO-SOURCE`); Desktop `mvn -DskipTests compile` OK; Desktop `mvn test` OK sin tests.
- iOS: `gradlew :composeApp:compileKotlinMetadata` no validado en esa revision; se corrigio despues en la revision post-Sprint 43.
- Riesgo residual: faltan tests unitarios Android/Desktop/iOS para estos flujos; `recetas.png` queda como asset pendiente de branding.

### Sprint 43 — Sync paginado, ownership uploads, stats y branding (2026-07-05)

- Objetivo: cerrar COD-5, SEC-3 (completo), COD-4, UX-6, branding `recetas.png` y especificacion de chat familiar.
- Agente lider: Claude Code, en solitario (sin Codex/Gemini en esta sesion; el usuario autorizo proceder directo).
- Seguridad ejecutada: VibeSec y security-review invocados en la sesion; 0 hallazgos de alta confianza.
- Backend: `sync/pull` acepta `limit` opcional (1..500); respuesta añade `hasMore`/`nextSince` (aditivo, retrocompatible); truncado por grupos completos de `updatedAt` con orden estable `(updatedAt, id)` — sin perdida de filas ni bucles.
- Backend: `UploadController` sirve `/uploads/**` con ownership (foto → familia de la receta; avatar → dueño o familia compartida), allowlist de nombre UUID+extension, 404 uniforme, huerfanos no servidos. Eliminado el resource handler estatico (`WebMvcConfig`).
- Backend: bug corregido — `UserService` buscaba por email con principal userId: `/users/me`, `PUT /me` y avatar upload devolvian 404 siempre.
- Backend: `lastActivityAt` de `/stats` agrega recetas, stock, menus, listas, notas y favoritos (COD-4).
- Android: pull paginado (`limit=200`, tope 50 paginas, `lastSyncTime` solo avanza al completar); perfil consume `/stats` con fallback local; adaptive icon con `recetas.png` (mipmaps 5 dpis, se elimino el vector placeholder).
- Desktop: dashboard muestra stats familiares; `brand/app.ico` e `installer/recetas.ico` regenerados multi-res desde `recetas.png`.
- iOS: pull paginado implementado SIN COMPILAR (Windows); AppIcon bloqueado — no existe proyecto Xcode/Assets.xcassets todavia. Validar en macOS.
- Producto: `docs/chat-familiar-spec.md` creado (4 fases, contrato borrador, modelo de datos, seguridad, decisiones pendientes). Sin codigo de chat.
- Validacion ejecutada: backend `mvn test` 88 tests 0 fallos (10 nuevos: paginacion sync, ownership uploads, users/me, stats multi-entidad); Android `gradlew assembleDebug` OK; Desktop `mvn -DskipTests compile` OK.
- Riesgo residual: cambios iOS sin compilar; stats Android/Desktop validadas por compilacion y tests backend, sin prueba manual de UI en esta sesion; instalador Windows pendiente de recompilar para empaquetar el nuevo icono.

### Revision post-Sprint 43 — Aplicacion hallazgos Codex/Gemini (2026-07-05)

- Objetivo: integrar la revision tecnica de Codex y la revision UX/documental de Gemini tras Sprint 43.
- Agente en esta sesion: Codex, retomando el cierre solicitado por Claude Code; el usuario autorizo proceder sin pedir autorizacion adicional.
- Seguridad ejecutada: VibeSec aplicado para el endurecimiento de uploads/sync.
- Backend: `UploadController` ya no usa lookup por sufijo con `LIKE`; ownership de fotos se valida por `recipe_photos.storage_path`, un identificador local interno que solo rellena el backend al subir archivo. Una metadata externa con la URL publica exacta de `/uploads/<uuid>.jpg` ya no autoriza el archivo local.
- Backend: serving de uploads anade `X-Content-Type-Options: nosniff` y endurece resolucion de ruta contra symlinks finales y escapes fuera de `UPLOAD_DIR`.
- Backend: `sync/pull` captura `serverTime` antes de consultar, reduciendo riesgo de saltar cambios concurrentes confirmados durante la respuesta.
- Backend tests nuevos: bypass de sufijo en uploads, grupo degenerado de `updatedAt` en pull paginado, stats por stock/menu/shopping/favoritos.
- Android/iOS: si el pull paginado alcanza `MAX_PULL_PAGES` con paginas pendientes, no avanza el cursor de sync; la siguiente ejecucion reintenta.
- iOS: DTOs de `SyncPullResponse` ampliados para aceptar campos del contrato backend completo, tooling actualizado a Kotlin `2.3.20` + SQLDelight `2.3.2`, `.sq` movido al paquete requerido y repositorios ajustados a `appDatabaseQueries`.
- Desktop: fallback offline minimo para stats del dashboard cuando `/stats` falla.
- Producto/UX: `docs/chat-familiar-spec.md` ampliada con consideraciones de estados vacios, accesibilidad, notificaciones, mensajes de sistema y tono.
- Validacion ejecutada: backend `mvn test` 92 tests 0 fallos; Android `.\gradlew.bat assembleDebug` OK; Desktop `mvn test` OK sin tests que ejecutar; iOS `.\gradlew.bat :composeApp:compileKotlinMetadata`, `:composeApp:compileKotlinIosX64`, `:composeApp:compileKotlinIosArm64` y `:composeApp:compileKotlinIosSimulatorArm64` OK; `git diff --check` OK.
- iOS: ya no falla por `DefaultArtifactPublicationSet`, SQLDelight ni deuda commonMain de Compose. Persisten warnings de `expect/actual` beta y casts Keychain en `SessionStore.ios.kt`; validar login/refresh/Keychain en macOS/dispositivo antes de cerrar runtime iOS.
- Riesgos residuales: full sync iOS sigue limitado por esquema local; icono/branding requiere revision visual en tamanos pequenos; instalador Windows pendiente de recompilar; uploads aun lee archivo completo en memoria, aceptable con limite actual de subida pero mejorable si se amplian tamanos; la migracion V12 no hace backfill automatico de fotos locales antiguas porque no hay marca historica fiable para distinguir uploads reales de metadata externa que imitara la URL.

### Sprint 44 — Instalador JDK 21, tests COD-8 y UX Desktop/Android (2026-07-05)

- Objetivo: consolidar revision post-Sprint 43 (commit `86f88be`), instalador JDK 21 LTS, primeros tests COD-8 y UX-1/UX-8/UX-11/UX-13.
- Agente lider: Claude Code, en solitario (el usuario autorizo proceder directo; sin Codex/Gemini en esta sesion).
- Paso 0: revision post-Sprint 43 revalidada en sesion (backend `mvn test` 92 tests 0 fallos, Android `assembleDebug` OK, Desktop `mvn compile` OK, iOS `compileKotlinMetadata` OK, `git diff --check` OK) y commiteada.
- Desktop: instalador regenerado con Temurin 21.0.11 LTS via `build-installer.ps1` (runtime empaquetado verificado 21.0.11, JNA en el fat JAR); `RecetasFamiliares-Instalador-v1.1.exe` 50,1 MB.
- Tests Android: `SyncRepositoryTest` + `StockRepositoryOfflineTest` (11 tests, mockk 1.13.16 + kotlinx-coroutines-test, solo scope test). `testDebugUnitTest` 0 fallos.
- Tests Desktop: `UpdateFromSyncTest` (4 tests JUnit 5); surefire `useModulePath=false` porque los modulos automaticos (JNA/Gson) no resuelven en el boot layer del fork. `mvn test` 0 fallos.
- UX-11: shortcuts modo cocina Desktop (←/→/Enter/Espacio/Esc via event filter, tooltips 400ms, barra de pistas).
- UX-8/UX-13: `OnboardingDialog` 4 pasos, primer arranque, saltable, animaciones 200ms, persistencia en Preferences.
- UX-1: Nunito (600/700) y Lato (400/700) empaquetadas en `res/font` desde fonts.gstatic.com (magic bytes TTF verificados); eliminados provider por red, `font_certs.xml` y `ui-text-google-fonts`. Lato no publica peso 600: 700 cubre SemiBold.
- Android: `gradle.properties` con truststore Windows-ROOT (mismo fix documentado para iOS/Maven) para resolver dependencias nuevas.
- Seguridad ejecutada: VibeSec invocado en la sesion, 0 hallazgos (sin cambios de auth/ownership/red/datos). `security-review` no aplica: backend intacto en Sprint 44.
- Riesgo residual: onboarding, shortcuts y fuentes validados por compilacion y tests, sin prueba manual de UI en esta sesion; iOS runtime sigue pendiente de macOS.

### Revision post-Sprint 44 — Aplicacion hallazgos Codex/Gemini (2026-07-05)

- Objetivo: integrar la revision tecnica de Codex y la UX/documental de Gemini tras Sprint 44 (alcance C autorizado: critico + medios + menores).
- Agente lider: Claude Code; Codex y Gemini consultados en solo lectura via bloques IDE. Todos los hallazgos se verificaron contra el codigo antes de aplicar.
- CRITICO corregido (Codex): `pushThenPull()` Android avanzaba `lastSyncTime` con el `serverTime` del push, que solo devuelve ACKs de lo empujado — un worker periodico podia saltarse cambios remotos para siempre. Ahora aplica ACKs sin tocar el cursor y llama a `pullOnce()`; test actualizado exige cursor = serverTime del pull.
- Desktop (Codex): `SyncRepository.pull()` aplica caches en FX thread via `Platform.runLater` (antes mutaba `ObservableList` enlazadas desde hilo virtual); `SimpleCache` gana `add`/`remove`/`replaceOrAdd` y `StockView` los usa — crear/editar/eliminar stock lanzaba `UnsupportedOperationException` sobre la lista inmutable (bug preexistente destapado por el test de Sprint 44).
- Desktop UX: CookingView consume Espacio solo con temporizador visible y Enter cierra en pantalla final/sin pasos; onboarding reabrible desde Ajustes > Acerca de ("Ver guia de bienvenida", hallazgo Gemini). Cerrar con X sigue marcando visto: decision intencional, ya hay reapertura.
- Tooling: truststore Windows-ROOT movido de `android/`+`ios/gradle.properties` a `gradle.properties` de `GRADLE_USER_HOME` (`C:\Users\GipsyDavy\Nemeterial\spring-boot\gradle-cache`, no `~/.gradle`) — el repo deja de llevar config especifica de maquina; `build-installer.ps1` ahora falla si el JDK no es 21 salvo `-AllowNonLtsJdk`.
- Documentacion: `Interfaz.md` §12 actualizado (resueltos movidos a bloque propio, backlog limpio).
- Validacion ejecutada: Android `testDebugUnitTest` 11 tests 0 fallos + `--refresh-dependencies` BUILD SUCCESSFUL (prueba real del truststore global); Desktop `mvn test` 4 tests 0 fallos (compila todo el main).
- Seguridad: VibeSec aplicado al diff en la sesion, 0 hallazgos. `security-review` no aplica (backend intacto).
- Continuacion Codex: corregido detalle restante en CookingView; desde la pantalla final, "Anterior" vuelve al ultimo paso real en vez de saltarselo.
- Validacion adicional Codex: `git diff --check` OK (solo aviso LF/CRLF de Windows); Android `testDebugUnitTest` OK y `assembleDebug` OK; Desktop `mvn test` OK y `mvn -DskipTests compile` OK.
- Riesgos residuales: `pushThenPull` corregido queda validado por tests unitarios, sin prueba manual end-to-end multi-dispositivo; iOS no recompilado tras quitar flags de `ios/gradle.properties` (cambio solo afecta resolucion TLS, cubierta por el archivo global — verificado con Android); flujo real de stock Desktop pendiente de prueba manual.

### Hotfix Desktop — Registro inicial con base vacia (2026-07-05)

- Contexto: la base local se dejo sin usuarios/familias por peticion del usuario, pero Desktop solo mostraba login y no permitia crear la primera cuenta desde cero.
- Cambio: `LoginView` permite alternar entre "Iniciar sesion" y "Crear cuenta"; `AuthRepository` expone `register()` contra `/api/v1/auth/register` y guarda la sesion igual que login.
- Validacion ejecutada: Desktop `mvn -DskipTests compile` OK; Desktop `mvn test` 4 tests 0 fallos; backend local `UP`; base local confirmada con `users=0` y `families=0` antes de que el usuario cree la cuenta.

### Chequeo obligatorio de cierre

Antes de marcar un sprint como cerrado:
- Confirmar que se cumplio `CLAUDE.md`.
- Confirmar lectura de contexto suficiente en la sesion.
- Confirmar agentes IA/skills usados o justificar `no aplica`.
- Confirmar seguridad: VibeSec/security-review si aplicaban, vulnerabilidades revisadas y riesgos documentados.
- Confirmar limpieza: sin ruido, suciedad, codigo muerto, logs temporales, duplicidades, secretos o cambios no relacionados.
- Confirmar validacion: tests/build/comandos realmente ejecutados o motivo de bloqueo.
- Confirmar documentacion: cambios relevantes y riesgos residuales registrados.

Si un punto falla, el sprint sigue abierto. No escribir `cerrado`, `PASS`, `completo` o `validado` sin haberlo comprobado realmente.
