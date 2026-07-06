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

Resuelto en Sprint 45 (2026-07-05):
- COD-8 ampliado en Android con `RecipeRepositoryOfflineTest` y `FavoriteAndNoteRepositoryOfflineTest`: recetas, ingredientes, pasos, favoritos y notas cubren creado offline `syncVersion=0`, dirty negativo conservando version base, tombstones y borrado local de entidades creadas offline; tambien se cubre que `CancellationException` no haga fallback local silencioso.

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

Resuelto en Sprint 45 (2026-07-05):
- COD-8 ampliado en Desktop con `AppSessionTest` y `AuthRepositoryTest`: persistencia/limpieza de sesion, rol desconocido sin privilegios admin, register/login contra `AuthRepository` con `ApiClient` falso y logout best-effort que limpia sesion aunque falle la API.

Resuelto en Sprint 46 (2026-07-06):
- UX-5: `ProfileView` completa accesible a todos los roles (antes solo habia user card en sidebar): avatar grande con carga autenticada, cambiar foto, editar nombre, email completo, familia + badge de rol y stats familiares con fallback local. La user card del sidebar navega al perfil; la edicion de nombre/avatar se movio alli desde `MainWindow`.
- Ayuda contextual MVP: `HelpDialog` con consejos por vista activa (F1 y boton Ayuda en sidebar); la guia de bienvenida es reabrible desde el perfil y desde el propio dialogo de ayuda (antes solo admins via Ajustes).
- Fix menor preexistente: `style.css` tenia `-fx-max-width: Double.MAX_VALUE` (CSS invalido del hotfix de registro que rompia el parseo del stylesheet); movido a codigo en `LoginView`.

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

Sprint 46 (2026-07-06) cerro UX-5 y la ayuda contextual MVP en Desktop. El runtime iOS/macOS sigue bloqueado en esta maquina Windows (ver seccion 10) y COD-8 sigue parcial: no hay pruebas iOS ni pruebas de UI/manuales.

Prioridad propuesta para Sprint 47:

1. iOS: validar runtime en macOS/dispositivo (Keychain, interceptor 401, Coil autenticado, pull paginado), revisar warnings de casts Keychain y AppIcon con `recetas.png` cuando exista el proyecto Xcode (COD-1/COD-2). Bloqueado sin macOS.
2. COD-8 siguiente capa: Android `SyncWorker`/colas offline end-to-end con Room fake o DB in-memory; Desktop `ApiClient` refresh 401 y proteccion de imagenes con servidor HTTP fake si aporta valor sin fragilizar tests.
3. Producto: chat familiar fase 1. Viabilidad analizada 2026-07-06 (`docs/chat-familiar-spec.md` §9): fase 1 lista para arrancar en cuanto el usuario resuelva las 5 decisiones de §7; unico trabajo nuevo real es WebSocket/STOMP con auth. Video (fase 4) es el hueco serio; posponer.
4. Validacion manual de UI pendiente: onboarding y shortcuts modo cocina Desktop, fuentes empaquetadas Android en emulador, perfil y ayuda contextual Desktop.
5. UX-14 (nuevo, sprint posterior dedicado): ayuda TOTALMENTE completa en toda la aplicacion. El MVP de Sprint 46 (HelpDialog Desktop, 9 vistas) es solo la base. Alcance objetivo, por fases si hace falta:
   - Desktop: ayuda contextual en TODOS los modulos, dialogos y formularios (crear/editar receta, stock, menu, compra, notas, miembros, exportaciones, busqueda global, diagnostico), cada pestaña de Ajustes, modo cocina y onboarding; tooltips en todos los controles sin label visible (formato `Accion (Ctrl+X)`, delay 400ms); foco y orden de tabulacion documentados en formularios.
   - Android: sistema de ayuda equivalente (pantalla o bottom sheet de ayuda por seccion, accesible desde TopAppBar), con `contentDescription` completo y ayuda del modo cocina/manos libres.
   - iOS: mismo patron cuando el runtime este desbloqueado (COD-1/COD-2).
   - Contenido: microcopy calido y no tecnico, cubriendo cada opcion, atajo, gesto y estado (vacio/error/offline), de forma que el usuario pueda usar toda la aplicacion sin ayuda externa.
   - Criterio de cierre: inventario de pantallas/dialogos vs temas de ayuda al 100%, revision Gemini de textos, accesibilidad verificada (TalkBack/tooltips).

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

### Cierre de sesion Codex (2026-07-05 23:17)

- Ultimo commit: `930d1ca fix: permitir registro inicial en desktop`; anteriores relevantes: `0da11fc` (navegacion final modo cocina) y `f4cf44b` (hallazgos Codex/Gemini post-Sprint 44).
- Arbol Git comprobado limpio al iniciar cierre de sesion.
- Backend local sigue arrancado a peticion del usuario: `http://localhost:8080`, health `UP`, proceso Java PID `26412`.
- MySQL local `recetas_familiares` comprobado tras el hotfix: `users=1`, `families=1`, `refresh_tokens activos=1`. No documentar email ni password reales.
- Punto exacto para retomar: no hay Sprint 45 autorizado todavia. El usuario pidio estado/herramientas y se respondio esperando autorizacion.
- Sprint 45 recomendado por viabilidad en Windows: COD-8 ampliacion de tests Android/Desktop. Alternativas documentadas: iOS runtime en macOS/dispositivo (bloqueado sin macOS), UX Desktop perfil completo/ayuda contextual, o solo plan detallado.
- Herramientas confirmadas en esta sesion: Codex activo; `multi_agent_v1` disponible para subagentes si el usuario autoriza trabajo multiagente; VibeSec disponible como skill local; OWASP Dependency-Check disponible en perfiles Maven `security-audit` de backend/desktop; Browser/Documents/Presentations/Spreadsheets disponibles.
- Herramientas no confirmadas como callables directas: Gemini (preparar bloque para pegar si se necesita), `security-review` (requerido por `CLAUDE.md` cuando aplique, pero no visible como herramienta), `claude-mem` (no disponible; usar `CONTINUAR.md`/`CLAUDE.md` como memoria operativa).
- Si se retoma Sprint 45B COD-8: leer `CLAUDE.md`, `CONTINUAR.md`, `auditoria.md`; definir alcance exacto de tests; usar VibeSec si se toca auth/sync/datos; ejecutar como minimo tests afectados, build relevante y `git diff --check`.

### Sprint 45 — Ampliacion COD-8 Android/Desktop (2026-07-05)

- Objetivo: ejecutar el siguiente sprint viable en Windows: ampliar cobertura unitaria COD-8 en Android y Desktop sin abrir frentes iOS bloqueados por macOS.
- Agente: Codex, en solitario. No se usaron subagentes/multi-IA porque el usuario autorizo continuar el sprint, pero no pidio auditoria paralela ni delegacion.
- Skills/herramientas: VibeSec usado como checklist manual por tocar cobertura de auth/sesion y flujos offline; Browser/Gemini/Claude-mem no usados; `security-review` no esta disponible como herramienta callable en esta sesion.
- Android: nuevos tests `RecipeRepositoryOfflineTest` (5) y `FavoriteAndNoteRepositoryOfflineTest` (7). Cobertura de COD-3 sobre recetas, ingredientes, pasos, favoritos y notas: creado offline `syncVersion=0`, dirty negativo preservando version base, tombstones, borrado local de entidades creadas offline y propagacion de `CancellationException`.
- Desktop: nuevos tests `AppSessionTest` (2) y `AuthRepositoryTest` (3). Cobertura de persistencia/limpieza de sesion, rol desconocido sin privilegios admin, `register()`, `login()` con fallback `familyId` legacy y `logout()` best-effort.
- Cambio de produccion minimo: `AppSession` acepta `Preferences` inyectadas mediante constructor package-private para aislar tests; el constructor publico mantiene el nodo real `recetas/session`.
- Validacion ejecutada: `git diff --check` sin errores (solo aviso LF/CRLF de Windows en `AppSession.java`); Desktop `mvn test` 9 tests, 0 fallos; Android `.\gradlew.bat testDebugUnitTest` 23 tests, 0 fallos; Android `.\gradlew.bat assembleDebug` BUILD SUCCESSFUL.
- Seguridad: no se cambiaron endpoints, autorizacion backend ni almacenamiento real de secretos. Revision VibeSec manual sin hallazgos nuevos; no se introdujeron secretos ni credenciales en documentacion.
- Riesgos residuales: COD-8 sigue parcial (sin iOS, sin tests UI/manuales, sin fake HTTP para refresh de `ApiClient`); tests Android son unitarios con mocks y no sustituyen una prueba Room/WorkManager integrada; backend local seguia arrancado por peticion previa del usuario y no fue modificado.
- Punto exacto tras Sprint 45: sprint cerrado con cambios commiteados; siguiente sprint recomendado es Sprint 46 segun seccion 8.

### Sprint 46 — UX-5 perfil Desktop y ayuda contextual (2026-07-06)

- Objetivo: cerrar UX-5 (vista de perfil completa Desktop) y ayuda contextual MVP, unica opcion de la seccion 8 viable en Windows con UI nueva.
- Agente lider: Claude Code, en solitario (el usuario autorizo el plan; sin Codex/Gemini en esta sesion — se ofrecen bloques IDE para revision posterior).
- Desktop: `ProfileView` nueva (avatar 88px con carga autenticada en virtual thread, cambiar foto con allowlist y limite 8 MB, editar nombre, email completo, familia + badge de rol via `loadMyFamilies()`, stats de `/stats` con fallback local igual que Dashboard, boton guia de bienvenida). Accesible a todos los roles desde la user card del sidebar (card completa clicable); edicion de nombre/avatar retirada de `MainWindow` (movida al perfil, `refreshUserCard()` con fade).
- Desktop: `HelpDialog` nuevo — ayuda contextual por vista activa (9 vistas + fallback), F1 global y boton "Ayuda" en sidebar (tooltip 400ms), con acceso a la guia de bienvenida. Desbloquea onboarding/ayuda para no-admins.
- Fix preexistente: `-fx-max-width: Double.MAX_VALUE` en `style.css` (CSS invalido introducido con el registro Desktop; el parser lo rechazaba en runtime) sustituido por `setMaxWidth` en `LoginView`.
- Validacion ejecutada: Desktop `mvn test` 9 tests 0 fallos (compila los 48 fuentes incluidas las vistas nuevas); `git diff --check` OK (solo aviso CRLF); backend dev arrancado y app lanzada con `javafx:run` — arranque limpio, 0 errores CSS y 0 excepciones en log. Prueba manual interactiva pendiente del usuario (app dejada abierta).
- Seguridad ejecutada: VibeSec en la sesion, 0 hallazgos (solo UI cliente; ownership/validaciones reales en backend intacto; sin secretos ni datos sensibles en logs). `security-review` no aplica: backend sin cambios.
- Nota de entorno: el backend dev requiere `JWT_SECRET` (SEC-1); se arranco con secreto efimero generado en la sesion, lo que invalida sesiones previas de clientes (re-login necesario).
- Riesgo residual: sin tests de UI automatizados para las vistas nuevas (COD-8 UI sigue pendiente); prueba manual completa de perfil/ayuda pendiente de confirmacion del usuario.

### Revision post-Sprint 46 — Aplicacion hallazgos Codex/Gemini (2026-07-06)

- Objetivo: integrar la revision tecnica de Codex y la UX/textos de Gemini sobre el commit `20ba01a` (alcance completo autorizado).
- Agente lider: Claude Code; Codex y Gemini consultados en solo lectura via bloques IDE. Todos los hallazgos verificados contra el codigo antes de aplicar.
- Codex medio 1 aplicado: `ProfileView.loadFamilyInfo()` unifica familia/rol/stats — la familia elegida (la de sesion o la primera) aporta nombre, `role()` para el badge e `id` para stats; antes el badge solo leia sesion y las stats podian no cargar con `familyId` null pese a haber familias.
- Codex medio 2 aplicado: contador de generacion en la carga de avatar de `ProfileView` — una carga lenta antigua ya no pisa la imagen mas nueva.
- Codex menor 1 aplicado (confirmado como bug real): el fallback de iniciales usaba `.avatar-circle`, que fija 38px por CSS y en JavaFX la stylesheet pisa los setters de codigo; nueva clase `.profile-avatar-circle` (88px).
- Codex menor 2 aplicado: allowlist de extensiones (jpg/jpeg/png/webp) en cliente antes de subir avatar; la validacion fuerte de tipo real sigue en backend.
- Gemini: 8 retoques de redaccion en `HelpDialog` (calidez/claridad); "retirar miembros" cambiado a "expulsar miembros" para alinear con la UI real (`FamilyMembersView`). Descartado el hallazgo sobre "Sincronizar ahora": el tip es del topic dashboard y el boton del Dashboard se llama exactamente asi.
- Validacion ejecutada: Desktop `mvn test` 9 tests 0 fallos; app relanzada con `javafx:run` — 0 errores CSS y 0 excepciones en log; `git diff --check` OK.
- Seguridad: cambios revisados con criterio VibeSec en la sesion (allowlist cliente es defensa en profundidad; ownership/validacion real en backend intacto), 0 hallazgos.
- Riesgo residual: sin cambios; prueba manual interactiva de perfil/ayuda sigue pendiente del usuario.

### Cierre de sesion Claude Code (2026-07-06)

- Ultimo commit: `0dcb5cd fix: aplicar hallazgos revision Codex/Gemini post-Sprint 46`; anterior: `20ba01a feat: perfil completo y ayuda contextual en desktop (UX-5)`. Arbol Git limpio al cerrar.
- Sprint 46 CERRADO (UX-5 + ayuda contextual Desktop) y revision post-Sprint 46 aplicada en la misma sesion (Codex 4 hallazgos aplicados, Gemini 8 textos aplicados + 1 descartado con motivo documentado arriba).
- Procesos dejados en marcha al cerrar: backend dev en `http://localhost:8080` (arrancado con `JWT_SECRET` efimero generado en la sesion — si se reinicia, regenerarlo; las sesiones de clientes previas quedaron invalidadas y requieren re-login) y app Desktop via `javafx:run`. Si la maquina se reinicia, arrancar backend segun seccion 5 recordando exportar `JWT_SECRET`.
- Punto exacto para retomar: NO hay Sprint 47 autorizado. Queda pendiente de esta sesion solo la PRUEBA MANUAL interactiva del usuario en Desktop: abrir perfil desde la user card del sidebar, editar nombre, cambiar foto (allowlist jpg/jpeg/png/webp, max 8 MB), ver familia/rol/stats, F1 y boton Ayuda en varias vistas, reabrir guia de bienvenida. Si la prueba manual revela fallos, tratarlos como hotfix antes de abrir Sprint 47.
- Sprint 47 recomendado por viabilidad en Windows (seccion 8): COD-8 siguiente capa (Android `SyncWorker`/colas offline e2e; Desktop `ApiClient` refresh 401 con HTTP fake) o chat familiar fase 1 (decidir antes cuestiones abiertas de `docs/chat-familiar-spec.md`). iOS sigue bloqueado sin macOS.
- Herramientas confirmadas en esta sesion: VibeSec (skill, ejecutada 2 veces con 0 hallazgos), security-review (skill disponible, no aplico: backend intacto), OWASP Dependency-Check (perfil Maven `security-audit`, no aplico: sin dependencias nuevas), bloques IDE Codex/Gemini (usados en solo lectura, hallazgos verificados contra codigo antes de aplicar). `claude-mem` no existe: la memoria operativa es este archivo + memoria persistente del agente.
- Al retomar: leer `CLAUDE.md`, este `CONTINUAR.md` (secciones 8 y 10) y, si el sprint toca deuda, `auditoria.md`. Ejecutar validaciones reales en la sesion antes de afirmar estado.

### Hotfix admin inicial y gestion de miembros (2026-07-06)

- Objetivo: comprobar que el primer usuario de una instalacion nueva queda con rol administrador suficiente y, si faltaba funcionalidad, permitir crear/anadir usuarios y asignar roles desde Desktop.
- Resultado: el registro backend ya creaba el primer usuario como `OWNER`; se anadio cobertura explicita y migracion `V13__ensure_family_owner_members.sql` para promover defensivamente a `OWNER` al miembro activo mas antiguo de familias existentes que no tuvieran propietario.
- Backend: `InviteMemberRequest` acepta opcionalmente `displayName` y `password`; `FamilyService.inviteMember()` mantiene invitacion silenciosa de usuarios existentes/no existentes sin datos de creacion, y permite a `OWNER`/`ADMIN` crear nuevos usuarios con password hasheada y rol `MEMBER` o `ADMIN`. `OWNER` sigue bloqueado como rol asignable por invitacion.
- Desktop: `FamilyMembersView` incorpora boton "Anadir miembro" visible solo para admins/owner, valida email/nombre/password/rol, ejecuta IO en `Thread.ofVirtual()` y actualiza UI con `Platform.runLater`; `MainWindow` refresca el rol persistido al arrancar para recoger promociones por migracion sin exigir re-login si la API responde.
- Seguridad: VibeSec usado como checklist manual por tocar auth/roles. La autorizacion real sigue en backend mediante `requireAdminOrAbove`; no se introdujeron secretos ni logs sensibles. Colisiones de email al crear usuarios se convierten en `409 CONFLICT`.
- Base local dev comprobada sin exponer PII: `active_users=1`, `active_families=1`, `role_OWNER=1`, `families_without_owner=0`.
- Validacion ejecutada: backend `mvn test` 93 tests 0 fallos; Desktop `mvn test` 9 tests 0 fallos; `git diff --check` sin errores (solo avisos LF/CRLF de Windows).
- Riesgo residual: no se hizo prueba manual interactiva de Desktop; queda pendiente verificar visualmente el dialogo de anadir miembro contra backend real antes de empaquetar.

### Punto de retoma para proximo sprint — tras hotfix admin (2026-07-06)

- Estado Git al documentar: hay cambios sin commit del hotfix admin. No abrir Sprint 47 ni mezclar trabajo nuevo hasta decidir si se prueba manualmente y se commitea este hotfix.
- Ultimo commit confirmado antes del hotfix: `0dcb5cd fix: aplicar hallazgos revision Codex/Gemini post-Sprint 46`.
- Archivos de produccion modificados por el hotfix: `backend/src/main/java/org/gipsybuho/recetasfamiliares/families/FamilyService.java`, `InviteMemberRequest.java`, `users/UserRepository.java`, migracion `V13__ensure_family_owner_members.sql`, `desktop/api/dto/FamilyDtos.java`, `desktop/data/repository/FamilyRepository.java`, `desktop/ui/FamilyMembersView.java`, `desktop/ui/MainWindow.java`.
- Tests modificados/anadidos: `AuthControllerTest` verifica que registro/login exponen rol `OWNER`; `FamilyMemberControllerTest` cubre owner inicial creando admin, admin creando miembro, cambio de rol y expulsion.
- Contrato API cambiado de forma compatible hacia atras: `POST /api/v1/families/{familyId}/members` mantiene `email` y `role`, y acepta opcionalmente `displayName` y `password` para crear usuario nuevo. Clientes antiguos que solo invitan emails existentes siguen funcionando.
- Impacto multiplataforma revisado: backend y Desktop actualizados; Android/iOS/shared no se tocaron porque no tienen UI de gestion de miembros en este hotfix. Si se implementa la gestion de miembros en Android/iOS, reutilizar este contrato y no duplicar reglas de permisos en cliente.
- Primera accion al retomar: ejecutar prueba manual Desktop con backend dev real: abrir Miembros, comprobar boton "Anadir miembro" como OWNER, crear un ADMIN con password temporal, iniciar sesion con ese admin, crear MEMBER, cambiar rol y expulsar MEMBER. Confirmar que un usuario MEMBER no ve/usa acciones admin.
- Segunda accion al retomar: si la prueba manual pasa, ejecutar `git diff --check`, backend `mvn test`, Desktop `mvn test`, revisar `git diff --stat` y hacer commit del hotfix. Si falla, tratarlo como hotfix antes de cualquier sprint nuevo.
- Seguridad pendiente antes de cerrar el hotfix: repetir checklist VibeSec/security-review si se cambian endpoints, permisos o UI sensible adicional; no loggear emails/passwords reales durante pruebas; usar cuentas temporales sin datos personales.
- Siguiente sprint recomendado tras cerrar este hotfix: COD-8 capa siguiente (Desktop `ApiClient` refresh 401 con HTTP fake y/o Android `SyncWorker`/colas offline e2e). Alternativa de producto: chat familiar fase 1 solo si el usuario decide las 5 cuestiones abiertas de `docs/chat-familiar-spec.md` §7.
- Riesgo residual documentado para el siguiente sprint: falta automatizacion UI para dialogos Desktop y falta paridad de gestion de miembros en Android/iOS; no bloquear el hotfix por esto, pero registrarlo como deuda funcional multiplataforma.

### Cierre hotfix admin y gestion de miembros (2026-07-06)

- Cerrado por Claude Code tras el plan de retoma de la entrada anterior.
- Revalidacion en sesion: revision de seguridad del diff con criterio VibeSec/security-review (auth/roles/migracion) sin hallazgos; backend `mvn test` 93 tests 0 fallos; Desktop `mvn test` 9 tests 0 fallos; `git diff --check` OK.
- Jar backend reempaquetado (el jar en ejecucion no incluia el hotfix), backend rearrancado y migracion `V13` aplicada con exito (BD en v13); app Desktop relanzada sin errores.
- Prueba manual del usuario: PASS (añadir miembro como OWNER, crear ADMIN, ADMIN crea MEMBER, cambio de rol, expulsion, MEMBER sin acciones admin; perfil y F1 de Sprint 46 tambien verificados).
- Nota de contrato: `POST /families/{id}/members` acepta opcionalmente `displayName`+`password` (retrocompatible). Android/iOS sin UI de miembros: deuda de paridad documentada.
- Riesgo residual: 409 al crear usuario confirma a un admin autenticado que un email ya existe (necesario para UX de creacion; superficie limitada a admins).

### Analisis de viabilidad chat familiar (2026-07-06, solo lectura)

- Objetivo: evaluar viabilidad de chat tipo WhatsApp (texto en tiempo real + fotos + videos) a peticion del usuario. Sin cambios de codigo.
- Agente: Claude Code en solitario (analisis de solo lectura; no aplicaban Codex/Gemini ni skills de seguridad — sin diff que revisar).
- Resultado completo en `docs/chat-familiar-spec.md` §9. Resumen: fase 1 (texto tiempo real backend+Android) viable ya — solo falta WebSocket/STOMP con auth por membership; fase 3 (imagenes) barata reutilizando `FileStorageService`/`UploadController`; fase 4 (video+push) es el hueco serio (lectura completa en memoria, sin Range serving, ffmpeg pesado, FCM sin configurar, APNs sin macOS).
- Bloqueo formal para fase 1: las 5 decisiones de §7 de la spec (retencion, read-receipts, reacciones, broker, tests) — decision de producto del usuario, no trabajo tecnico.
- Preparado para proximo sprint: si el usuario resuelve §7, abrir "Sprint chat fase 1" con alcance backend (modulo chat + WS + tests de ownership/rate limit) y Android (pantalla chat + WS con fallback polling).

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
