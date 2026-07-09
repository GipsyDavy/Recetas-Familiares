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

Recetas Familiares es una aplicacion premium multiplataforma para familias: recetas, stock, menus, lista de compra, notas, fotos, miembros, sincronizacion y chat familiar con texto, adjuntos de imagen basicos y edicion/borrado propio en tiempo real. Las fases futuras del chat son cerrar UX de imagenes, video/push e iOS.

Plataformas:
- `backend/`: Spring Boot + MySQL + Flyway + JWT.
- `android/`: Kotlin + Compose + Room + WorkManager.
- `desktop/`: JavaFX + Maven + HTTP API client.
- `ios/`: KMP + Compose Multiplatform + Ktor + SQLDelight.
- `shared/`: objetivo para logica compartida Android/iOS cuando aplique. Aun no existe como modulo en el repo; iOS mantiene su propia copia de DTOs y logica bajo `ios/composeApp/`.

Estado conocido a partir de la documentacion previa:
- Backend: 116 tests, 0 fallos en la ultima validacion documentada de chat con edicion/borrado propio.
- Android: funcional, con offline-first, UI avanzada y chat texto validado en AVD.
- Desktop: funcional, instalador Windows v1.1 generado, ajustes como vista central y chat texto validado a nivel de protocolo.
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

Desarrollo con PostgreSQL en Hetzner via WireGuard. El trafico DB viaja por el tunel cifrado (`10.10.0.1:5432`); no se fuerza `sslmode=require` dentro del tunel. Si en el futuro se usa un Postgres gestionado o una ruta fuera de WireGuard, exigir TLS en `DB_URL`.

PowerShell:

```powershell
$env:DB_URL="jdbc:postgresql://10.10.0.1:5432/recetas_familiares"
$env:DB_USERNAME="recetas_app"
$env:DB_PASSWORD="<DB_PASSWORD>"
$env:JWT_SECRET="<JWT_SECRET_32_BYTES_MINIMO>"
$env:DEV_SEED_DATA_ENABLED="true"
$env:DEV_SEED_EMAIL="demo@recetas.local"
$env:DEV_SEED_PASSWORD="<DEMO_PASSWORD>"
$env:DEV_SEED_DISPLAY_NAME="Demo"
$env:DEV_SEED_FAMILY_NAME="FamiliaDemo"
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Ejemplo `java -jar` usando placeholders. Sustituir valores localmente sin escribir secretos reales en documentacion versionable:

```bash
java -jar backend/target/recetas-familiares-backend-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  "--spring.datasource.url=jdbc:postgresql://10.10.0.1:5432/recetas_familiares" \
  "--spring.datasource.username=recetas_app" \
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
- Chat familiar texto (2026-07-07), imagenes (Fase 3, 2026-07-08) y edicion/borrado propio (2026-07-08) integrados y publicados en `main` (backend + Android + Desktop). Modulo independiente bajo `/api/v1/families/{familyId}/chat`.
  - `GET /messages?before=<id>&limit=<=50` → `{ items[], hasMore, nextBefore }` (desc por cursor, filtrado por limpieza del usuario).
  - `POST /messages` `{ id?, body }` (id de cliente idempotente, `body` <=2000) → `ChatMessageResponse` + broadcast WS.
  - `POST /messages/images` multipart `id?`, `body?`, `files[]` (JPEG/PNG/WebP, max 5, 8 MB por archivo, validacion fuerte) → `ChatMessageResponse.attachments[]` + broadcast WS.
  - `PUT /messages/{messageId}` `{ body }` → edita solo mensajes propios no borrados, `body` trim no vacio <=2000, ventana 15 minutos, broadcast WS afterCommit.
  - `DELETE /messages/{messageId}` → soft delete solo de mensaje propio, devuelve tombstone (`deleted=true`, `body=null`, `attachments=[]`) y broadcast WS afterCommit.
  - Adjuntos servidos por `/uploads/chat/{filename}` y `/uploads/chat_thumbnails/{filename}` con ownership familiar y 404 fail-closed.
  - `POST /clear` → 204 (limpieza por usuario, marca `cleared_before`, no borra para otros).
  - `GET /export` → copia del usuario en orden ascendente.
  - WS `/ws` (STOMP, broker simple), topic `/topic/families/{familyId}/chat` (solo entrega); JWT en el CONNECT, ownership en el SUBSCRIBE, rate limit de envio por usuario.
  - No entra en `sync/pull`: cursor propio. Borrado/export es POR USUARIO, no global. Detalle completo en `docs/chat-familiar-spec.md` §10-§12.

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

Chat familiar:
- Texto/emojis y edicion/borrado propio ya integrados en backend, Android y Desktop.
- REST para historial paginado, envio inicial/fallback, envio de imagenes multipart, editar/borrar mensajes propios, limpiar/exportar por usuario y WebSocket/STOMP operativo.
- Imagenes Fase 3 tiene backend/contrato/storage y miniaturas, pero queda abierto un sprint funcional de UX: en Desktop las imagenes quedan en el chat sin abrirse ni descargarse; en Android se ha observado mensaje con globo/adjunto sin thumbnail visible y tampoco hay abrir/descargar.
- Fases pendientes: cerrar UX de imagenes, videos, push notifications e iOS.
- Storage protegido para imagenes y videos; no guardar binarios pesados directamente en MySQL.
- Imagenes fase 3: JPEG/PNG/WebP, max 5 por mensaje, max 8 MB, extension + `Content-Type` + magic bytes + parseo real, stripping de metadata por re-encode, thumbnails backend y cleanup best-effort de fallos parciales.
- Produccion seria deberia contemplar mover miniaturas a worker si sube volumen y analisis antivirus o servicio equivalente para adjuntos.

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

Chat familiar:
- Fase texto/emojis implementada y validada en AVD con backend real: historial, envio/recepcion en vivo, fallback/reconexion, limpiar/exportar por usuario y bloqueo offline sin `POST`.
- Fase imagenes implementada a nivel de contrato/envio: selector del sistema, caption opcional, compresion JPEG fuera del hilo principal, envio multipart y render previsto de thumbnails con Coil + OkHttp autenticado.
- Pendiente vivo de imagenes: la validacion visual reporto que en Android no aparece el thumbnail, solo el globo del mensaje con adjunto; falta abrir a tamano completo y guardar/descargar.
- Edicion/borrado propio implementado: menu en mensaje propio, dialogo de edicion, confirmacion de borrado y merge por id de respuestas REST/WS.
- Envio offline no permitido en fase texto; sin cola local.
- Pendiente menor: export Android usa fechas ISO/UTC en vez de formato local legible.
- Fases posteriores: cerrar imagenes UX, video/push y progreso explicito de subida si se detecta necesidad UX.

### Desktop

Implementado/documentado:
- Login, dashboard, recetas, detalle, formulario.
- Stock, menu semanal, shopping list, notas, busqueda global.
- Modo cocina, exportaciones, notificaciones, sonidos opcionales.
- Temas, ajustes como vista central, diagnostico e instalador Windows v1.1.
- Gestion de miembros y avatar upload.
- Chat familiar texto e imagenes: historial paginado, envio/recepcion en tiempo real (WebSocket/STOMP), limpiar/exportar por usuario, selector JPG/PNG/WebP y render de thumbnails autenticados. Texto validado a nivel de protocolo contra backend real; pendiente funcional detectado en imagenes: no hay click para abrir original ni accion de guardar/descargar desde JavaFX.

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

Chat familiar:
- Texto, imagenes y edicion/borrado propio ya implementados (ver trazabilidad). Desktop<->Android texto validado a nivel de protocolo; queda prueba manual GUI si se quiere cerrar UX visual de imagenes y menus de edicion/borrado.
- Videos/push quedan como fase 4.

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
- Chat familiar iOS despues de estabilizar backend y clientes Android/Desktop.
- Evitar abrir este frente antes de resolver refresh 401, push sync y paridad basica.
- Tiempo real y adjuntos en iOS deben llegar despues de tener runtime macOS/dispositivo, contrato backend estable y estrategia de push notifications.

---

## 8. Bloqueantes Recomendados Para Sprint Siguiente

Tras Chat Fase 1, Chat Desktop Fase 2, remediacion OWASP, Chat Fase 3 imagenes y edicion/borrado propio (publicado en `main` el 2026-07-08), el chat de texto/edicion queda implementado en backend/Android/Desktop con build/tests verdes en sesion. Imagenes tienen contrato/backend/storage y envio basico, pero la validacion visual dejo una brecha funcional en clientes: falta visor/descarga y Android no renderiza de forma fiable el adjunto. Backend/desktop pasan Dependency-Check con umbral CVSS >= 7 en la ultima auditoria documentada. El runtime iOS/macOS sigue bloqueado en esta maquina Windows y COD-8 sigue parcial: no hay pruebas iOS ni pruebas UI automatizadas.

Prioridad propuesta para el siguiente sprint no autorizado:

1. Chat imagenes UX (siguiente sprint recomendado): render fiable de imagenes en Android, abrir imagen original a tamano completo en Desktop/Android, guardar/descargar con flujo autenticado, placeholders/error states y validacion cruzada Desktop<->Android con imagen real.
2. Validacion manual de UI pendiente: menus de edicion/borrado del chat, onboarding y shortcuts modo cocina Desktop, fuentes empaquetadas Android en emulador, perfil y ayuda contextual Desktop.
3. Chat siguiente capa: fase 4 video/push si el usuario prioriza multimedia/notificaciones. Video requiere redisenar storage a streaming a disco/Range y antivirus o servicio equivalente.
4. Vigilancia dependencias: revisar `desktop/owasp-suppressions.xml` antes de 2026-10-01 y sustituir Kotlin 2.4.0 por Kotlin >= 2.4.20 estable cuando exista; monitorizar PDFBox porque 3.0.7 sigue con CVEs medias y no hay 3.0.x posterior en Maven Central a 2026-07-08.
5. PostgreSQL en Hetzner: migracion MySQL -> PostgreSQL con backend Spring intacto. Plan completo en `docs/migracion-mysql-a-postgresql-plan.md`; requiere resolver las 5 decisiones de §4 del plan antes de arrancar.
6. COD-8 siguiente capa: Android `SyncWorker`/colas offline end-to-end con Room fake o DB in-memory; Desktop tests adicionales si aportan valor sin fragilizar.
7. iOS: validar runtime en macOS/dispositivo (Keychain, interceptor 401, Coil autenticado, pull paginado), revisar warnings de casts Keychain y AppIcon con `recetas.png` cuando exista el proyecto Xcode (COD-1/COD-2). Bloqueado sin macOS.
8. UX-14 (sprint posterior dedicado): ayuda TOTALMENTE completa en toda la aplicacion. El MVP de Sprint 46 (HelpDialog Desktop, 9 vistas) es solo la base. Alcance objetivo, por fases si hace falta:
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

### Sprint 47 — COD-8 ApiClient Desktop y SyncWorker Android (2026-07-06)

- Objetivo: ampliar COD-8 en la siguiente capa viable en Windows, sin abrir iOS ni chat: Desktop `ApiClient` con HTTP fake y Android `SyncWorker`/colas offline.
- Agente: Codex en solitario. No se usaron subagentes ni Gemini porque el alcance fue acotado, tecnico y con validacion local directa; no habia revision UX/producto.
- Android: `SyncWorker` delega en `SyncWorkerRunner`, que devuelve `success`/`retry` y ya no convierte `CancellationException` en retry. Nuevos tests `SyncWorkerRunnerTest` cubren exito, fallo recuperable y cancelacion. `SyncRepositoryTest` amplia la cola offline de `pushThenPull` para stock, notas, favoritos y shopping items, validando `baseSyncVersion`, tombstones y que el cursor avance solo con el pull posterior.
- Desktop: `ApiClient` acepta `baseUrl` inyectable manteniendo el constructor publico existente con `api.base.url`; se anadio `mockwebserver` solo en scope test. Nuevo `ApiClientHttpTest` cubre refresh 401 con reintento y persistencia de tokens nuevos, limpieza de sesion si falla refresh, y que `fetchImage()` solo envie `Authorization` al origen del API y no a URLs externas.
- Archivos modificados: `android/.../sync/SyncWorker.kt`, `android/.../SyncWorkerRunnerTest.kt`, `android/.../SyncRepositoryTest.kt`, `desktop/pom.xml`, `desktop/.../api/ApiClient.java`, `desktop/.../core/ApiClientHttpTest.java`.
- Validacion ejecutada: Desktop `mvn -Dtest=ApiClientHttpTest test` 3 tests 0 fallos; Desktop `mvn test` 12 tests 0 fallos; Desktop `mvn -DskipTests compile` BUILD SUCCESS; Android `.\gradlew.bat testDebugUnitTest` 27 tests 0 fallos; Android `.\gradlew.bat assembleDebug` BUILD SUCCESS; `git diff --check` OK (solo avisos LF/CRLF de Windows).
- Seguridad: VibeSec usado como checklist por tocar auth/refresh, token forwarding de imagenes y sync offline. Verificado que los tokens de prueba son ficticios, que `Authorization` no se filtra a hosts externos, que el refresh no adjunta bearer y que no se introducen secretos reales en archivos versionables. `security-review` no esta disponible como herramienta callable directa en esta sesion; se aplico revision manual equivalente sobre diff sensible.
- OWASP Dependency-Check: `NVD_API_KEY` estaba presente y se intento `mvn -DskipTests verify -P security-audit` en Desktop por la nueva dependencia de test, pero no termino tras 15 minutos; el proceso Maven residual fue identificado y detenido. No hay reporte `dependency-check-report.*`, por tanto no cuenta como validacion PASS.
- Riesgos residuales: no hay prueba Room/WorkManager real con scheduler Android; la cobertura nueva del worker es por runner puro y la cola offline sigue con DAOs mockeados. OWASP queda pendiente por timeout. iOS sigue bloqueado sin macOS y no se toco.
- Estado de Git al cierre: Sprint 47 commiteado en `f34d132 test: cubrir sync worker y refresh desktop`.

### Cierre de sesion Codex (2026-07-06)

- Ultimo commit funcional del sprint: `f34d132 test: cubrir sync worker y refresh desktop`.
- Esta entrada se commitea como cierre documental de sesion; tras ese commit, el arbol Git debe quedar limpio.
- Sprint 47 cerrado y documentado: cobertura COD-8 ampliada en Android (`SyncWorkerRunnerTest`, cola offline en `SyncRepositoryTest`) y Desktop (`ApiClientHttpTest` con `MockWebServer`).
- Validaciones de la sesion ya documentadas en Sprint 47: Desktop `mvn test` 12 tests 0 fallos, Desktop `mvn -DskipTests compile` OK, Android `testDebugUnitTest` 27 tests 0 fallos, Android `assembleDebug` OK, `git diff --check` OK.
- OWASP Dependency-Check quedo pendiente por timeout de 15 minutos pese a `NVD_API_KEY` presente; no hay reporte utilizable.
- Punto exacto para retomar: NO hay Sprint 48 autorizado. Opciones recomendadas: resolver OWASP Dependency-Check timeout si se quiere cierre de seguridad de dependencias, avanzar COD-8 con pruebas Room/WorkManager reales, o abrir chat familiar fase 1 solo tras decidir las 5 cuestiones de `docs/chat-familiar-spec.md` §7. iOS sigue bloqueado sin macOS/dispositivo.

### Sprint Chat Fase 1 — Chat familiar texto tiempo real backend + Android (2026-07-07)

- Objetivo: implementar la fase 1 del chat familiar (texto/emojis en tiempo real) tras resolver el usuario las 5 decisiones de `docs/chat-familiar-spec.md` §7. Alcance elegido por el usuario: texto en tiempo real + borrado/exportacion por usuario; reacciones, fotos y video quedan para fases posteriores.
- Agente lider: Claude Code, en solitario. No se usaron Codex/Gemini: el usuario pidio implementar directamente; el trabajo fue tecnico y con validacion local (tests + build). Se ofrecen bloques IDE si se quiere revision paralela antes de fusionar la rama.
- Rama: `feat/chat-fase-1` (creada desde `main` `792d603`). Commits: `5b92135` (backend), `88f101b` (Android). Docs en este mismo cierre.
- Decisiones de producto registradas: retencion ilimitada; borrar/limpiar y exportar POR USUARIO (marca `cleared_before`, no afecta a otros); sin read-receipts; reacciones a fase 2; broker STOMP embebido; tests REST + autorizacion WS.
- Backend: modulo `chat/` independiente, migracion `V14` (`chat_messages`, `chat_message_clears`), REST (`GET/POST /chat/messages`, `POST /chat/clear`, `GET /chat/export`), WebSocket/STOMP `/ws` con JWT en el CONNECT y ownership en el SUBSCRIBE, rate limit de envio por usuario, idempotencia por id de cliente. `spring-boot-starter-websocket` anadido; `SecurityConfig` permite el handshake `/ws/**` (auth en STOMP).
- Android: `ChatScreen` (overlay desde TopAppBar), `ChatSocket` (STOMP minimo sobre WebSocket OkHttp, sin dependencias nuevas), `ChatRepository`, DTOs y endpoints, estado/logica en `RecetasViewModel` (merge sin duplicados, autoscroll, polling de respaldo 15 s, cierre en `onCleared`). Envio offline no permitido (sin cola local); degrada a polling.
- Seguridad ejecutada: `security-review` (skill) sobre el backend en la sesion → 0 hallazgos de alta confianza (notas defense-in-depth: origen WS `*` por defecto configurable, 409 por reuso de id mitigado por UUID, export sin limite de tamano). VibeSec aplicado como checklist manual sobre el diff Android (token en frame no en URL, Bearer limitado al host, sin logs sensibles) → 0 hallazgos.
- Validacion ejecutada: backend `mvn test` 107 tests 0 fallos (antes 93; +14 chat: 7 REST + 7 interceptor STOMP); Android `:app:compileDebugKotlin` OK, `:app:assembleDebug` OK, `:app:testDebugUnitTest` OK (sin regresion, 27 tests previos). `git diff --check` no revisado en este cierre (solo avisos LF/CRLF esperados de Windows).
- Riesgos residuales: sin prueba manual end-to-end en dispositivo/emulador con backend arrancado (tiempo real y fallback validados por compilacion y tests de contrato, no en runtime); STOMP sin heartbeats (caida silenciosa se detecta al fallar el socket, polling cubre mientras); iOS bloqueado sin macOS; Desktop es la siguiente implantacion (fase 2); reacciones/edicion/fotos/video+push en fases 2-4. Rama sin fusionar a `main`: pendiente de decision del usuario (prueba manual y/o revision Codex/Gemini antes de merge).
- Punto exacto para retomar: probar manualmente Android contra backend dev real (abrir chat, enviar/recibir en vivo, verificar fallback a polling deteniendo el WS, borrar para mi, exportar). Si pasa, decidir merge de `feat/chat-fase-1`. Siguiente incremento: fase 2 Desktop o fase 3 fotos.

### Hotfix y validacion manual post-Gemini — Chat Android (2026-07-07)

- Objetivo: aplicar hallazgos de la revision Gemini antes de cerrar la fase 1 y ejecutar prueba manual Android real contra backend dev.
- Alcance aplicado solo Android: `ChatScreen.kt`, `RecetasViewModel.kt`, `ChatRepository.kt`, `ChatSocket.kt`.
- Hallazgo critico cerrado: el envio queda bloqueado si `chatConnected=false`; `sendChat()` tambien rechaza llamadas sin conexion en tiempo real y emite mensaje de usuario.
- Hallazgos medios cerrados: limite UI/backend de 2000 caracteres compartido (`CHAT_MAX_BODY_LENGTH`), borrador se limpia solo tras `POST` correcto, autoscroll no interrumpe lectura salvo carga inicial/cerca del final/mensaje propio, parseo defensivo de `MESSAGE` STOMP, `toWebSocketUrl()` soporta `ws/wss` y baseUrl sin protocolo, reconexion WebSocket con backoff corto tras caidas no iniciadas por el cliente.
- Validacion automatizada ejecutada tras los cambios finales: Android `:app:compileDebugKotlin` OK; `:app:testDebugUnitTest` OK; `:app:assembleDebug` OK; `git diff --check` OK (solo avisos LF/CRLF de Windows).
- Validacion runtime Android ejecutada en AVD `Pixel_9_Pro`: APK instalada; backend dev `GET /api/v1/health` OK; migracion `V14` aplicada por jar actual; historial REST `200`; handshake WebSocket `/ws` `101`; envio desde Android `POST /chat/messages` `201`; recepcion en vivo de mensaje enviado por segundo usuario via API; limite de 2050 caracteres queda capado en `2000/2000` sin `POST`; exportacion abre sharesheet con preview; borrar para mi muestra dialogo, cancelar conserva mensajes, confirmar devuelve `204`, owner queda vacio y otro miembro conserva historial por API; red del emulador cortada bloquea boton y no emite `POST`; red restaurada reconecta a `En linea`; recepcion post-reconexion verificada.
- Seguridad/entorno: se usaron cuentas temporales locales `@recetas.local`, sin datos personales. VibeSec usado como checklist: JWT en frame STOMP, no en URL; sin logs de tokens; validaciones de ownership siguen en backend. Backend dev quedo arrancado localmente con `JWT_SECRET` efimero y `DB_USERNAME=root` por desajuste local de credenciales de `recetas_app`; no usar esa desviacion fuera de pruebas locales.
- Riesgos residuales: export Android sigue mostrando fechas ISO/UTC, no formato local legible (hallazgo menor pendiente); STOMP sigue sin heartbeats reales, aunque ahora hay reconexion por cierre/fallo; no se ejecuto TalkBack ni prueba de historial largo con lectura manual prolongada; Desktop/iOS chat siguen pendientes por fases posteriores. Estado al documentar esta validacion: rama `feat/chat-fase-1` pendiente de commit hotfix y merge local.

### Cierre local Chat Fase 1 (2026-07-07)

- Commit hotfix en rama: `43877f6 fix: endurecer chat Android tras validacion manual`.
- Merge local a `main`: `merge: integrar chat familiar fase 1`.
- Estado: Chat Fase 1 queda integrado localmente en `main`; push remoto pendiente hasta autorizacion explicita.
- Punto de retoma: revisar `git status`, decidir si subir `main` a remoto y planificar siguiente fase. Pendientes funcionales recomendados: formato local de fechas en export Android, TalkBack/historial largo, Desktop chat fase 2, fotos fase 3.

### Publicacion remota Chat Fase 1 (2026-07-07)

- Usuario autorizo proceder tras el cierre local.
- Accion realizada: publicado `main` en `origin/main` con Chat Fase 1 y esta trazabilidad documental.
- No se publica la rama temporal `feat/chat-fase-1` salvo orden posterior.

### Cierre de sesion Codex — Chat Fase 1 publicado (2026-07-07)

- Estado Git al cerrar: `main` limpio y alineado con `origin/main` en `0ea0e14 docs: confirmar publicacion remota chat fase 1`; rama local temporal `feat/chat-fase-1` conserva el ultimo commit de rama `43877f6` y no se publico.
- Trabajo cerrado en esta sesion: revision Gemini incorporada, hotfix Android aplicado, pruebas automatizadas Android ejecutadas, prueba manual completa de chat en AVD, merge local de `feat/chat-fase-1` a `main`, publicacion de `main` a GitHub y trazabilidad documental actualizada.
- Validaciones relevantes ya ejecutadas en la sesion: Android `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `git diff --check`, backend health `UP`, WebSocket Android `101`, envio REST `201`, recepcion en vivo, bloqueo offline sin `POST`, reconexion tras restaurar red, exportacion sharesheet y borrado por usuario `204`.
- Entorno al cerrar: backend dev sigue arrancado en `http://localhost:8080` con proceso Java `PID 16636`, `JWT_SECRET` efimero y `DB_USERNAME=root` por desajuste local de credenciales de `recetas_app`; no usar esa desviacion fuera de pruebas locales. El emulador/AVD ya no aparece conectado por `adb devices`.
- Punto exacto de retoma: empezar en `main`, ejecutar `git status --short --branch` y confirmar que sigue alineado con `origin/main`. No queda trabajo sin commit de Chat Fase 1. Antes de abrir una fase nueva, decidir si se borra la rama local `feat/chat-fase-1` o se conserva como referencia.
- Siguiente sprint recomendado: Chat Desktop fase 2 si se quiere paridad cliente; alternativa corta: corregir export Android a zona horaria/formato local y ampliar prueba manual TalkBack + historial largo. Fase fotos queda como fase 3; iOS sigue bloqueado sin macOS/dispositivo.
- Riesgos residuales vivos: export Android usa ISO/UTC, STOMP no implementa heartbeats reales aunque reconecta tras cierre/fallo, no hay pruebas UI automatizadas del chat, no se ejecuto TalkBack, y la base local contiene cuentas temporales `@recetas.local` creadas para la prueba.

### Chat Fase 2 — Chat familiar Desktop (2026-07-07)

- Objetivo: implantar el chat familiar (fase 1, solo texto) en Desktop JavaFX conectado al mismo backend que Android, para mensajeria en tiempo real dentro de la familia.
- Agente lider: Claude Code en solitario para implementar; revision UX/producto de Gemini via bloque IDE (solo lectura). Hallazgos verificados contra el codigo antes de aplicar.
- Backend: sin cambios (contrato chat fase 1 ya existente y auditado). Desktop solo consume REST + WebSocket/STOMP.
- Archivos nuevos Desktop: `api/dto/ChatDtos.java`, `api/ChatSocket.java` (STOMP minimo sobre `okhttp3.WebSocket`, sin dependencias nuevas), `data/repository/ChatRepository.java`, `ui/ChatView.java`.
- Archivos modificados Desktop: `core/AppSession.java` (+`userId` persistido), `data/repository/AuthRepository.java` (guarda userId en login), `data/repository/UserRepository.java` (`fetchMe()` backfill), `api/ApiClient.java` (`getBaseUrl()` + `newWebSocket()`), `core/AppContext.java` (registro `ChatRepository`), `ui/MainWindow.java` (boton sidebar "Chat familiar", navegacion, apertura/cierre de socket en navegacion y logout), `resources/style.css` (clases `chat-*` tematizadas).
- Paridad con Android: mismo topic `/topic/families/{id}/chat`; JWT en el frame CONNECT (no en URL); ownership validado en backend; envio por REST, recepcion por WS; borrar/exportar por usuario.
- Revision Gemini incorporada (alcance C autorizado): (1) burbujas muestran fecha cuando el mensaje no es de hoy (Hoy/Ayer/DD/MM) — mejora sobre Android, que solo muestra HH:mm; (2) `TextArea` de entrada con tope de altura (120px); (3) esta documentacion; (4) re-render tras backfill de `userId`. Descartados por paridad/ruido: contador siempre visible, boton "cargar anteriores" persistente, separar statusLabel. Hallazgo "alto" de race de `userId` verificado como no real (el flujo resuelve el id antes de renderizar historial/envios).
- Seguridad: `/VibeSec` (skill) ejecutado sobre el diff en la sesion → PASS, 0 hallazgos de alta confianza (token en frame STOMP no en URL, Bearer solo al origen del API, ownership en backend, `Label` JavaFX renderiza texto plano sin XSS, sin secretos en logs; userId en prefs es UUID opaco no secreto). `/security-review` no aplica: backend sin cambios.
- Validacion ejecutada: Desktop `mvn -DskipTests compile` BUILD SUCCESS; Desktop `mvn test` 12 tests 0 fallos (incluye `AppSessionTest` tras anadir `userId`); backend contrato chat `ChatControllerTest`+`ChatStompAuthChannelInterceptorTest` 14/14 0 fallos.
- Riesgos residuales: STOMP sin heartbeats (igual que Android; reconexion por cierre/fallo); si el access token expira con el chat abierto el WS no refresca solo hasta reentrar (paridad Android); sin tests UI automatizados; prueba de renderizado/clics de la GUI JavaFX y del emulador Android sigue pendiente (no automatizable por el agente).
- Commit: rama `feat/chat-desktop` `6ee4697` (backend intacto).

### E2E de protocolo Chat Desktop y cierre (2026-07-07)

- Objetivo: validar en runtime el camino de datos del chat Desktop (REST + WebSocket/STOMP) contra backend real, y cerrar el sprint con merge a `main`.
- Agente lider: Claude Code en solitario. El usuario facilito credencial MySQL de sesion (`root`) usada solo como variable de entorno; NO escrita en ningun archivo versionado ni scratchpad.
- Entorno: backend dev arrancado con `DB_USERNAME=root`, `JWT_SECRET` efimero de sesion; MySQL local. Se creo familia de prueba `TestChat` con cuentas `@recetas.local` (userA OWNER, userB MEMBER).
- Metodo: cliente Node que replica exactamente los frames STOMP del `ChatSocket` Desktop/Android (CONNECT con JWT en el frame, SUBSCRIBE al topic, parseo de MESSAGE) + `curl` para REST. Sustituto fiel de la capa de red del cliente Desktop.
- Resultado tiempo real (WS): handshake+SUBSCRIBE de A y B OK; A envia REST -> B recibe en vivo por WS (autor correcto); B envia REST -> A recibe en vivo por WS; idempotencia por client id (misma id de servidor, sin duplicar). 4/4 PASS.
- Resultado REST: B ve historial compartido de la familia; rechaza body >2000 (400) y acepta exactamente 2000 (201); export A devuelve mensajes; borrar para A devuelve 204 y deja A vacio mientras B conserva su historial (borrado por-usuario, no global). Todo correcto.
- Conclusion: interoperabilidad Desktop<->Android en tiempo real a traves del backend CONFIRMADA a nivel de protocolo (mismos endpoints y frames STOMP). Ownership, idempotencia, limites, export y borrado por-usuario verificados en runtime.
- No validado (requiere interaccion humana): renderizado/clics de la GUI JavaFX y UI del emulador Android. Runbook de prueba manual entregado al usuario.
- Cierre Git: `feat/chat-desktop` (`6ee4697`) fusionado a `main`. Push remoto pendiente de autorizacion explicita del usuario.
- Punto de retoma: opcional prueba manual GUI Desktop/Android con el runbook; decidir push de `main` a `origin`; siguiente incremento chat: fase 3 (fotos) o iOS (bloqueado sin macOS). Limpieza pendiente: base local con cuentas de prueba `@recetas.local` y familia `TestChat`.

### Analisis migracion MySQL -> PostgreSQL (2026-07-08, solo lectura)

- Objetivo: evaluar a peticion del usuario si migrar la DB a Supabase o a Postgres en Hetzner. Sin cambios de codigo de migracion.
- Agente: Claude Code en solitario (analisis de solo lectura; no aplicaban skills de seguridad ni otros agentes: no habia diff).
- Aclaracion aportada al usuario: Supabase no es una DB (su motor es Postgres); Hetzner es hosting ortogonal; Supabase Cloud no corre en Hetzner (seria self-hosted). "Full Supabase" = reescritura de backend + 3 clientes + operar stack self-hosted; descartado.
- Decision acordada: Camino 1 = mantener Spring Boot y migrar solo el motor **MySQL 8.0 -> PostgreSQL**, con la DB en Hetzner. Clientes sin cambios.
- Evidencia de viabilidad recogida (inspeccion 2026-07-08): todas las `@Query` son JPQL (0 nativeQuery); timestamps por `@PrePersist`/`@PreUpdate` (el `ON UPDATE CURRENT_TIMESTAMP` MySQL es redundante); esquema portable (CHAR(36) UUID, VARCHAR, BIGINT, BOOLEAN, TIMESTAMP(6); sin AUTO_INCREMENT/ENGINE/ENUM/JSON); 14 migraciones Flyway; tests ya en H2; `DB_URL/USERNAME/PASSWORD` externalizados; dialecto Hibernate autodetectado.
- Entregable: `docs/migracion-mysql-a-postgresql-plan.md` con decision, evidencia, alcance, 5 decisiones pendientes del usuario, plan paso a paso (deps, traduccion de las 14 migraciones, entidades/validate, config, tests Testcontainers, migracion de datos, infra Hetzner), validacion esperada, riesgos y rollback.
- Gotcha principal a decidir: Postgres autogestionado en Hetzner implica backups/PITR/hardening propios (Hetzner no da Postgres gestionado nativo).
- Estado: sprint de migracion NO autorizado. Documentado para arrancar en frio cuando el usuario lo autorice. Sin cambios en el codigo del backend ni en la DB.

### Limpieza documental + OWASP Dependency-Check (2026-07-08)

- Objetivo autorizado por el usuario: `LIMPIEZA/DOCS+OWASP`.
- Agente de esta sesion: Codex. No se usaron subagentes/multi-IA ni Gemini porque el alcance fue acotado a documentacion y ejecucion local de auditoria. VibeSec usado como checklist de seguridad por tratarse de auditoria de dependencias y cierre documental. `security-review` no aplica: no se modificaron endpoints, auth, Spring Security, JWT, CORS, ownership ni codigo funcional backend.
- Limpieza documental aplicada: `docs/chat-familiar-spec.md` deja de decir que Desktop esta pendiente; ahora refleja chat texto integrado en `main` para backend + Android + Desktop, con fase 3 imagenes, fase 4 video/push e iOS pendientes. `CONTINUAR.md` actualiza estado consolidado, contrato critico de chat, secciones Backend/Android/Desktop y prioridades de siguiente sprint.
- OWASP Backend ejecutado: `mvn -f backend\pom.xml -DskipTests verify -P security-audit`. Resultado: BUILD FAILURE por vulnerabilidades con CVSS >= 7. Reportes generados en `backend/target/dependency-check-report.html` y `.json`.
  - Criticos/altos principales: `spring-core-6.2.18` (incluye CVE-2026-41855, 9.8), `tomcat-embed-core-10.1.54` (incluye CVE-2026-41293/CVE-2026-43512, 9.8), `spring-security-core-6.5.10` (incluye CVE-2026-47838, 8.1), `jackson-databind-2.21.2` (CVE-2026-54512/CVE-2026-54513, 8.1). Tambien aparecen medios en `commons-lang3`, `log4j-api` y `swagger-ui`/DOMPurify.
- OWASP Desktop ejecutado: `mvn -f desktop\pom.xml -DskipTests verify -P security-audit`. Resultado: BUILD FAILURE por vulnerabilidades con CVSS >= 7. Reportes generados en `desktop/target/dependency-check-report.html` y `.json`.
  - Criticos/altos principales: `kotlin-stdlib-1.8.21` y `kotlin-stdlib-common-1.9.10` por CVE-2026-53914 (9.8). `pdfbox-3.0.3` aparece con CVEs medias.
- Observacion de auditoria: Sonatype OSS Index quedo deshabilitado por falta de credenciales/token; la auditoria se basa en NVD/CISA y analizadores locales de Dependency-Check.
- No se actualizaron dependencias ni se tocaron POMs funcionales en este sprint. Siguiente accion recomendada: autorizar un sprint separado de remediacion de dependencias para backend y desktop, con upgrades controlados, `mvn test`, `mvn -DskipTests package/compile`, smoke de backend y nueva ejecucion OWASP hasta PASS o supresion justificada de falsos positivos.
- Riesgo residual vivo: hasta remediar, la auditoria OWASP queda en rojo para backend y desktop. Los reportes estan en `target/` y no se versionan.

### Remediacion OWASP dependencias backend/desktop (2026-07-08)

- Objetivo autorizado por el usuario: continuar tras `LIMPIEZA/DOCS+OWASP` y dejar backend/desktop en verde con OWASP Dependency-Check.
- Agente de esta sesion: Codex. No se usaron subagentes/multi-IA ni Gemini porque el alcance fue acotado a dependencias Maven y verificacion local. VibeSec usado como checklist. `security-review` no disponible como herramienta callable en esta sesion y no era critico porque no se modificaron endpoints, auth, ownership ni flujo funcional.
- Fuentes/criterio: Maven Central metadata para versiones disponibles; NVD y documentacion oficial Kotlin para CVE-2026-53914. Se evito saltar a Spring Boot 4 y se mantuvo Spring Boot 3.5.x.
- Backend actualizado:
  - Spring Boot parent `3.5.14` -> `3.5.15`.
  - Overrides de BOM/propiedades: Tomcat `10.1.57`, Log4j `2.26.1`, Jackson BOM `2.22.1`, Commons Lang `3.20.0`.
  - Override de `org.webjars:swagger-ui` a `5.32.8` via `dependencyManagement`.
- Desktop actualizado:
  - OkHttp `4.12.0` -> `5.4.0`, usando artefacto JVM explicito `okhttp-jvm` para mantener JPMS `requires okhttp3`.
  - `kotlin-stdlib` fijado directo a `2.4.0`.
  - PDFBox `3.0.3` -> `3.0.7`.
  - Nueva supresion acotada `desktop/owasp-suppressions.xml` para `org.jetbrains.kotlin:kotlin-stdlib:2.4.0` + `CVE-2026-53914`, con caducidad `2026-10-01Z`. Justificacion: la CVE afecta metadata de build cache Kotlin; Desktop solo consume el runtime por OkHttp. A 2026-07-08 Kotlin `2.4.0` es la ultima estable y Kotlin `2.4.20` estable esta planificado para septiembre de 2026.
- Validacion ejecutada:
  - Backend `mvn -f backend\pom.xml test` -> 107 tests, 0 fallos.
  - Desktop `mvn -f desktop\pom.xml test` -> 12 tests, 0 fallos.
  - Backend `mvn -f backend\pom.xml -DskipTests verify -P security-audit` -> BUILD SUCCESS; reportes en `backend/target/dependency-check-report.html` y `.json`.
  - Desktop `mvn -f desktop\pom.xml -DskipTests verify -P security-audit` -> BUILD SUCCESS; reportes en `desktop/target/dependency-check-report.html` y `.json`.
- Observaciones de auditoria: Sonatype OSS Index sigue deshabilitado por falta de credenciales/token; Dependency-Check uso NVD/CISA y analizadores locales. Desktop conserva aviso no bloqueante en `pdfbox-3.0.7` (CVE-2026-23907, CVE-2026-33929) porque no existe version 3.0.x posterior en Maven Central a 2026-07-08. Backend conserva warnings de tests sobre Mockito dynamic agent y SpringDoc endpoints habilitados por defecto en perfil test.
- Punto de retoma historico: revisar `git status`, decidir commit/push. Ese punto fue continuado en el sprint Chat Fase 3 imagenes. Dependencias: revisar Kotlin >= 2.4.20 estable antes de que caduque la supresion.

### Chat Fase 3 — Imagenes con validacion fuerte de adjuntos (2026-07-08)

- Objetivo autorizado por el usuario: `Chat fase 3 imagenes con validacion fuerte de adjuntos`.
- Agente de esta sesion: Codex. VibeSec usado como checklist por tocar uploads, ownership y clientes. `security-review` requerido por criterio de seguridad, pero no disponible como herramienta callable en esta sesion (`tool_search` no encontro herramienta equivalente); se documento la limitacion y se aplico revision manual VibeSec/OWASP sobre el diff.
- Backend implementado:
  - Dependencia `com.twelvemonkeys.imageio:imageio-webp:3.13.1` para lectura WebP.
  - Migracion `V15__create_chat_attachments.sql`.
  - Nuevos `ChatAttachmentEntity`, `ChatAttachmentRepository`, `ChatAttachmentResponse`; `ChatMessageResponse.attachments[]`.
  - Endpoint `POST /api/v1/families/{familyId}/chat/messages/images` multipart (`id?`, `body?`, `files[]`).
  - Rutas protegidas `GET /uploads/chat/{filename}` y `GET /uploads/chat_thumbnails/{filename}` con ownership familiar y 404 fail-closed.
  - `FileStorageService` endurecido: allowlist JPEG/PNG/WebP, extension + `Content-Type` + magic bytes, parseo real por ImageIO, limite 8 MB, limites de dimensiones/pixeles, re-encode para stripping de metadata, WebP normalizado a JPEG, thumbnails JPEG 512px, nombres UUID y subdirectorios controlados.
  - Limpieza best-effort de archivos ya escritos si falla thumbnail, validacion posterior, persistencia o publicacion antes del commit.
- Android implementado:
  - DTO `ChatAttachmentDto`, endpoint multipart Retrofit, `ChatRepository.sendImages()`.
  - `RecetasViewModel.sendChatImage()` comprime a JPEG en `Dispatchers.IO`, conserva caption opcional y mergea sin duplicados.
  - `ChatScreen` incluye selector de imagen del sistema, boton de adjunto, render de thumbnails con Coil/OkHttp autenticado y export con contador de imagenes.
- Desktop implementado:
  - DTO `ChatAttachment`, multipart con campos y archivos repetidos en `ApiClient`, `ChatRepository.sendImage()`.
  - `ChatView` incluye selector JPG/PNG/WebP, caption opcional, render async de thumbnails con `fetchImage()` autenticado y export con contador.
- Validacion ejecutada:
  - Backend enfocado: `mvn -f backend\pom.xml "-Dtest=ChatControllerTest,FileStorageServiceTest,UploadControllerTest" test` -> 18 tests, 0 fallos.
  - Backend completo final: `mvn -f backend\pom.xml test` -> 111 tests, 0 fallos.
  - Android `.\gradlew.bat :app:compileDebugKotlin` -> BUILD SUCCESS.
  - Android `.\gradlew.bat testDebugUnitTest assembleDebug` -> BUILD SUCCESS.
  - Desktop `mvn -f desktop\pom.xml test` -> 12 tests, 0 fallos.
  - OWASP backend `mvn -f backend\pom.xml -DskipTests verify -P security-audit` -> BUILD SUCCESS.
  - OWASP desktop `mvn -f desktop\pom.xml -DskipTests verify -P security-audit` -> BUILD SUCCESS; conserva warning no bloqueante de PDFBox 3.0.7 (CVE-2026-23907, CVE-2026-33929).
- Seguridad revisada manualmente: ownership por familia en DB, no path traversal (`SAFE_FILENAME`, `toRealPath`, subdirs controlados), no MIME sniffing en serving (`nosniff`), bearer Desktop solo al origen API, Compose/JavaFX renderizan captions/body como texto plano, archivos huerfanos inaccesibles y limpieza best-effort en fallos parciales.
- Riesgos residuales: thumbnails se generan sincronicamente en la request; no hay antivirus/sandbox de adjuntos; no se hizo prueba manual GUI enviando imagenes reales en Android/Desktop; WebP se acepta como entrada pero se almacena como JPEG. Pendiente commit/push.
- Punto de retoma: revisar `git status`, ejecutar `git diff --check`, decidir commit/push. Siguiente sprint recomendado: prueba manual GUI de imagenes, edicion/borrado individual de mensajes, o fase 4 video/push con redisenio de storage.

### Cierre Chat Fase 3 — commit del trabajo sin publicar (2026-07-08)

- Objetivo: sacar de riesgo el trabajo de Chat Fase 3 (imagenes) que una sesion previa dejo en el arbol sin commitear; revision de seguridad + validacion real + commit de cierre. Sin features nuevas.
- Agente lider: Claude Code en solitario para el codigo; apoyo multi-IA (Codex tecnico, Gemini UX) autorizado y entregado como bloques copy-paste para el chat IDE (no CLI, regla del usuario). Hallazgos externos pendientes de integrar solo si se verifican.
- Seguridad ejecutada en sesion: skill VibeSec + revision manual del diff. Superficie solida: ownership de adjuntos entre familias (`chat_attachments.storage_path` + membership), path traversal (`SAFE_FILENAME` UUID+ext, `toRealPath`, symlinks bloqueados), magic bytes vs extension vs Content-Type, EXIF stripping por re-encode, limites 8MB/8000px/16MP, 404 fail-closed, WS sin fuga entre familias (SUBSCRIBE valida membership), JPQL parametrizado, rate limit por usuario. Sin hallazgos de severidad alta.
- Hallazgo MEDIO (correctness, no seguridad): `spring.servlet.multipart.max-request-size=12MB` es menor que la capacidad anunciada (5 imagenes x 8MB). Enviar >12MB por mensaje lo rechaza Tomcat antes de la validacion de la app. Decision pendiente del usuario: subir el limite (aumenta DoS) o documentar/reducir el maximo real. No se toco en este cierre.
- Validacion ejecutada en sesion: backend `mvn test` 111 tests 0 fallos + `mvn -DskipTests package` OK (jar generado, V15 validada); Desktop `mvn test` 12 tests 0 fallos; Android `gradlew test assembleDebug` BUILD SUCCESSFUL. iOS no aplica (chat iOS es fase futura, sin runtime macOS en Windows).
- Archivos nuevos committeados: `chat/ChatAttachmentEntity/Repository/Response.java`, migracion `V15__create_chat_attachments.sql`, `desktop/owasp-suppressions.xml`.
- Riesgos residuales: sin prueba manual GUI de envio real de imagenes en Android/Desktop; decodificacion de imagen en request thread (deuda diferida documentada); Desktop `sendImage` solo envia 1 archivo; push remoto pendiente de autorizacion explicita.

### Endurecimiento Chat Fase 3 — hallazgos Codex/Gemini (2026-07-08)

- Objetivo: integrar la revision multi-IA (Codex tecnico, Gemini UX) del chat con imagenes. Todos los hallazgos verificados contra codigo antes de aplicar; solo se integro lo confirmado.
- Agente lider: Claude Code. Bloques Codex/Gemini entregados al usuario para el chat IDE (no CLI). Alcance aprobado por el usuario: seguridad + robustez + doc barato, sin features nuevas.
- SEGURIDAD (ALTO, corregido): `ChatStompAuthChannelInterceptor` ahora rechaza frames STOMP `SEND`. Antes, un cliente autenticado podia publicar directo al broker simple (`/topic/families/{id}/chat`) saltandose REST, ownership, persistencia y rate limit, permitiendo spoofing de autor e inyeccion cross-family en vivo. Los clientes solo usan CONNECT/SUBSCRIBE; el broadcast legitimo lo emite el servidor (`ChatRealtimePublisher`), fuera del canal entrante. Test `rejectsClientSendToBroker` anadido.
- ROBUSTEZ backend: (1) publish WS movido a `afterCommit` de la transaccion y limpieza de archivos en `afterCompletion(ROLLED_BACK)` — evita broadcast fantasma y binarios huerfanos si el commit falla tras escribir en disco; (2) N+1 en historial/export resuelto con `@BatchSize(50)` en `ChatMessageEntity.attachments` (compatible con la paginacion por cursor, a diferencia de un fetch join); (3) `max-request-size` multipart 12MB -> 50MB (cubre 5x8MB) + handler `MaxUploadSizeExceededException` -> 413 en `GlobalExceptionHandler`.
- CLIENTE: Android `compressImage` hace downsample durante la decodificacion (`setTargetSampleSize`) para no cargar el bitmap a resolucion completa (evita OOM con fotos grandes; afecta tambien a subida de fotos de receta). Desktop deja de exponer `ex.getMessage()` al enviar imagen (microcopy calido). Android `contentDescription` de adjunto incluye el caption si existe.
- DOC: `docs/chat-familiar-spec.md` aclara que el backend acepta hasta 5 imagenes pero Android/Desktop envian 1 por mensaje en esta fase.
- Validacion en sesion: backend `mvn test` **112 tests 0 fallos** (+1 SEND denegado); Desktop `mvn test` 12 tests 0 fallos; Android `gradlew test assembleDebug` BUILD SUCCESSFUL.
- Hallazgos diferidos (backlog, no en este alcance): indicador de progreso de subida y visor de imagen a tamano completo (Gemini, features nuevas); cobertura de tests de casos limite backend (rollback, mismatch magic byte, exceso dimensiones); tope de tamano en export; pulido visual de placeholders de carga.
- Riesgo residual: sin prueba manual GUI de envio real de imagenes; push remoto pendiente de autorizacion.

### Chat edicion/borrado individual propio (2026-07-08)

- Objetivo autorizado por el usuario: continuar con el siguiente sprint recomendado tras Chat Fase 3, implementando edicion y borrado individual de mensajes propios en backend, Android y Desktop.
- Agente de esta sesion: Codex. VibeSec usado como checklist por tocar endpoints autenticados, ownership, soft delete y clientes. Bloque Gemini preparado para revision copy-paste segun regla del usuario; integrar hallazgos externos solo si el usuario pega respuesta y se verifican contra codigo.
- Backend implementado:
  - Nuevo `EditChatMessageRequest`.
  - `PUT /api/v1/families/{familyId}/chat/messages/{messageId}`: solo autor, mensaje no borrado, `body` trim no vacio <=2000, ventana de edicion de 15 minutos desde `createdAt`, respuesta `ChatMessageResponse`.
  - `DELETE /api/v1/families/{familyId}/chat/messages/{messageId}`: solo autor, soft delete de mensaje y adjuntos, respuesta tombstone (`deleted=true`, `body=null`, `attachments=[]`).
  - Ambas mutaciones publican por WS mediante `publishAfterCommit`, coherente con el endurecimiento anterior.
  - Historial/export ya no filtran `deleted=false`; asi los tombstones sobreviven a recarga/export hasta que la limpieza por usuario los oculte por `cleared_before`.
- Android implementado:
  - Retrofit/DTO/repositorio con `editChatMessage` y `deleteChatMessage`.
  - `RecetasViewModel` valida cliente, llama backend y mergea por id.
  - `ChatScreen` incorpora menu en mensajes propios no borrados, dialogo de edicion y confirmacion de borrado.
- Desktop implementado:
  - `ApiClient.delete(path, responseType)`, DTO de edicion, `ChatRepository.edit/delete`.
  - `ChatView` cambia dedupe por upsert ordenado para que REST/WS reemplacen mensajes existentes.
  - Boton visible de opciones y menu contextual en burbujas propias no borradas para editar/eliminar.
- Tests nuevos backend: editar propio reciente, bloqueo 404 a otro miembro de la misma familia, rechazo de edicion tras 15 minutos y tombstone persistente en historial/export.
- Validacion ejecutada:
  - Backend `mvn -f backend\pom.xml test` -> 116 tests, 0 fallos.
  - Desktop `mvn -f desktop\pom.xml test` -> 12 tests, 0 fallos.
  - Android `.\gradlew.bat test assembleDebug` -> BUILD SUCCESS.
- Riesgos residuales: sin prueba manual GUI de los menus/dialogos en Android/Desktop; sin tests UI automatizados Compose/JavaFX; borrado admin/owner de mensajes ajenos queda fuera de alcance por decision de "individual propio".
- Commit publicado: `2c18a9f feat: editar y borrar mensajes propios en chat`.
- Punto de retoma: revisar `git status`. Queda una carpeta no versionada `herztner/` ajena a este sprint, no tocada. Siguiente sprint recomendado: validacion manual GUI del chat completo (imagenes + edicion/borrado) o fase 4 video/push con redisenio de storage.

### Sprint pendiente - Chat imagenes UX: visor, descarga y render fiable (2026-07-08)

- Origen: durante la prueba visual manual del chat, el usuario confirma que las imagenes enviadas/recibidas en Desktop se quedan dentro del chat sin posibilidad de abrirlas ni descargarlas. En Android no se ve la imagen en el chat; solo aparece el globo de mensaje con adjunto, sin abrir ni descargar.
- Severidad producto: funcional. El backend y el multipart existen, pero el adjunto no es consumible de forma suficiente por los usuarios.
- Alcance recomendado:
  - Backend: verificar contrato `ChatMessageResponse.attachments[]`, `url`, `thumbnailUrl`, ownership 404 fail-closed y headers de descarga. Si hace falta accion de descarga dedicada, mantenerla autenticada y sin exponer bearer en URLs externas.
  - Android: corregir render de thumbnail autenticado en `ChatScreen`, revisar base URL/URLs relativas, estados loading/error, tap para visor a tamano completo y accion guardar/compartir/descargar.
  - Desktop: hacer click en thumbnail para abrir original en dialogo/ventana, accion guardar con `FileChooser`, placeholder/error/retry y fetch autenticado del original.
  - Tiempo real: validar que un envio con imagen desde Desktop aparece con thumbnail util en Android y viceversa, sin depender de recargar historial.
  - Seguridad: no filtrar bearer a terceros, no abrir directamente URLs con token en navegador externo, conservar ownership por familia y path traversal fail-closed.
- Criterios de cierre:
  - Backend tests si se toca serving/headers/contrato.
  - Android `test assembleDebug`.
  - Desktop `mvn test`.
  - Prueba visual real con una imagen enviada Desktop -> Android y otra Android -> Desktop: thumbnail visible, abrir original, guardar/descargar y error state si el adjunto devuelve 404.
- Punto de retoma operativo: en esta sesion se habian arrancado backend dev/H2, emulador Android y Desktop para pruebas visuales. En una nueva sesion, comprobar procesos vivos y reiniciar limpio si hace falta. No versionar credenciales de prueba; recrear usuarios locales en la misma familia si el backend H2 se reinicia.
- Recomendacion de orden: hacer este sprint antes de video/push, porque cierre UX de imagenes es deuda de la Fase 3 ya integrada.

### PUNTO DE RETOMA EXACTO — Migracion PostgreSQL en curso (2026-07-09)

Documento redactado como ingeniero senior experto en programacion para retomar sin ambiguedad en la proxima sesion. Hay DOS hilos abiertos en paralelo, en ramas distintas. Leer entero antes de tocar nada.

#### Estado del repositorio
- Rama activa al cerrar la sesion: `feat/migracion-postgresql` (creada desde `main`).
- `main` sigue SIN los cambios del chat: viven solo en `feat/chat-imagenes-ux` (pusheada a origin) y en esta rama de migracion NO estan (salio de main limpio). No mezclar.
- Carpeta `herztner/` sin versionar (IP VPS + clave publica + comando ssh). No commitear; considerar `.gitignore` mas adelante.

#### Hilo A — Chat imagenes UX (COMPLETO, pendiente de merge y prueba manual)
- Rama `feat/chat-imagenes-ux`, commit `ca75ebf`, PUSHEADA a origin. Builds verdes (Android assembleDebug OK, Desktop 12 tests). VibeSec + security-review 0 hallazgos. Hallazgos Codex/Gemini integrados.
- PR NO creado (no habia `gh`; se instalo, no se autentico y se DESINSTALO por decision del usuario). Crear PR a mano: https://github.com/GipsyDavy/Recetas-Familiares/pull/new/feat/chat-imagenes-ux (base `main`).
- Residual: falta prueba visual cross-device real (thumbnail, abrir original, guardar Desktop<->Android, error 404). Desktop no normaliza el origen de la URL (riesgo al exponer en Hetzner; Android ya cubierto por `rewriteUploadUrl`).

#### Hilo B — Migracion MySQL -> PostgreSQL (EN CURSO, Fase 1 hecha)
- Tipo de migracion confirmado: **MySQL 8 -> PostgreSQL**, backend Spring Boot INTACTO (misma seguridad). NO es Supabase. Plan completo: `docs/migracion-mysql-a-postgresql-plan.md`.
- Decisiones cerradas (usuario eligio "defaults" + matices):
  - Hosting: Postgres autogestionado en VPS Hetzner (`167.233.213.242`).
  - UUID: se mantiene `CHAR(36)` (minimo cambio).
  - Datos: migrar `FamilyDemo` con `pgloader` (base no limpia).
  - Tests: subir a Testcontainers-Postgres.
  - Pooler: conexion directa para migraciones.
  - **Backend LOCAL por ahora** (no en el VPS todavia).
  - **Red: WireGuard** (no Tailscale, no Cloudflare). Justificacion: con backend local, el trafico critico es backend local -> Postgres Hetzner (TCP crudo), no HTTP; WireGuard es la alternativa directa a Tailscale y no necesita dominio. Postgres escuchara SOLO en la interfaz WireGuard, jamas publico. Cloudflare Tunnel + Zero Trust queda APLAZADO para cuando se exponga la API publica (backend en VPS); ademas requiere un dominio en Cloudflare que el usuario NO tiene (la cuenta/plan es gratis, el nombre de dominio no). No se creo cuenta Cloudflare (accion interactiva del usuario, no automatizable).

- FASE 1 — HECHA y commiteada: commit `2514c29` en `feat/migracion-postgresql`.
  - `backend/pom.xml`: `com.mysql:mysql-connector-j` -> `org.postgresql:postgresql` (runtime); `org.flywaydb:flyway-mysql` -> `org.flywaydb:flyway-database-postgresql`. Versiones gestionadas por el BOM de Spring Boot (sin `<version>`).
  - Validado: `mvn test` -> 116 tests, 0 fallos (aun sobre H2 en MODE=MySQL).

- FASE 2 — SIGUIENTE, NO empezada. Alcance exacto:
  - CORRECCION al plan: hay **15 migraciones (V1..V15)**, no 14. El plan `docs/migracion-*.md` §2 dice 14 porque es anterior al chat; el chat anadio `V14__create_chat_schema.sql` y `V15__create_chat_attachments.sql`. Traducir las 15.
  - Traducir `backend/src/main/resources/db/migration/V1..V15` a sintaxis PostgreSQL: eliminar `ON UPDATE CURRENT_TIMESTAMP(6)` (la app fija `updated_at` via `@PreUpdate`); `DEFAULT CURRENT_TIMESTAMP(6)` -> `now()` o quitar default; `TIMESTAMP(6)` -> `timestamptz` (UTC; ya hay `hibernate.jdbc.time_zone: UTC`); mantener `CHAR(36)`; indices/PK/FK/UNIQUE/CHECK sin cambios.
  - ACOPLAMIENTO CRITICO: los tests usan H2 en `MODE=MySQL` con `ddl-auto=validate` (`backend/src/test/resources/application-test.yml`, y `DevDataSeederTest` con url H2 inline). Al pasar las migraciones a sintaxis Postgres, H2 deja de servir. Por eso Fase 2 DEBE incluir el cambio de tests a Testcontainers-Postgres EN EL MISMO COMMIT para no dejar un commit rojo. Anadir dependencia Testcontainers-postgresql (scope test), base class con `@Container PostgreSQLContainer` + `@DynamicPropertySource`, y quitar la config H2.
  - REQUISITO DE ENTORNO: Fase 2 necesita **Docker** en la maquina (Testcontainers arranca `postgres:16`). PREGUNTA ABIERTA al usuario sin responder: si hay Docker Desktop instalado/corriendo. Alternativas si no: Postgres local levantado por el usuario, o validar solo compilacion y aplazar la ejecucion de tests.
  - Criterio de cierre Fase 2: `mvn test` verde contra Postgres real + arranque con `ddl-auto=validate` sin desajustes entidad/columna.

- FASES POSTERIORES (no empezadas): Fase 3 ya absorbida en Fase 2 (tests). Fase 4 config despliegue (`application*.yml`/env, `sslmode=require`), sin secretos. Fase 5 infra Hetzner (Postgres Docker, usuario minimo privilegio, backups pg_dump/PITR, firewall) + Fase 5b WireGuard (VPS<->maquina local, Postgres bind a wg0, drop publico 5432). Fase 6 datos (`pgloader` FamilyDemo, verificar recuentos). Fase 7 smoke E2E (health UP, Flyway V1..V14, registro/login, CRUD, sync, chat REST+WS) + VibeSec sobre config de conexion + cierre docs.

- Metodo de trabajo pactado: commitear POR FASE (por si se agota cuota de IA), verificar build/tests reales en cada fase, no marcar nada como validado sin ejecutarlo. Rollback: MySQL actual intacto hasta Fase 7; todo en rama.

#### Primer paso de la proxima sesion
1. `git checkout feat/migracion-postgresql` y confirmar `git log -1` = `fc3fc22` (Fase 1 en `2514c29`).
2. Confirmar disponibilidad de Docker (Fase 2). Si no hay, decidir alternativa.
3. Ejecutar Fase 2 (traducir V1..V15 + Testcontainers) y commitear al quedar verde.

#### Guia ejecutable Fase 2 (autosuficiente para cualquier agente IA)

NOTA PARA AGENTES IA QUE RETOMEN: la memoria de Claude en `~/.claude` es privada y NO esta en el repo. Las fuentes de verdad en el repositorio son `CLAUDE.md` (reglas), este `CONTINUAR.md` (estado) y `docs/migracion-mysql-a-postgresql-plan.md` (plan). No asumir contexto de sesiones anteriores fuera de estos archivos y del historial de git.

Inventario real de idioms MySQL a traducir (verificado por grep el 2026-07-09):
- `ON UPDATE CURRENT_TIMESTAMP(6)` y `DEFAULT CURRENT_TIMESTAMP(6)`: en V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V14, V15. (V11, V12, V13 no los tienen.)
- `TINYINT`: solo en `V10__create_ratings_schema.sql` (`stars TINYINT NOT NULL`) -> Postgres `smallint`. Su `CHECK (stars BETWEEN 1 AND 5)` es valido en Postgres, sin cambio.
- No hay `ENGINE=`, `AUTO_INCREMENT`, `UNSIGNED`, `ENUM(` en ninguna migracion (los UUID son `CHAR(36)` generados por la app).
- V11 y V12 son ALTER TABLE (avatar_url, storage_path). V13 (`ensure_family_owner_members`) tiene logica INSERT/UPDATE con subconsultas: revisar que la sintaxis sea ANSI/Postgres (evitar extensiones MySQL); traducir si hace falta.

Reglas de traduccion (aplicar a cada archivo):
1. Quitar el fragmento ` ON UPDATE CURRENT_TIMESTAMP(6)` (dejar la columna como `... NOT NULL`). La app fija `updated_at` en `@PreUpdate`.
2. `DEFAULT CURRENT_TIMESTAMP(6)` -> `DEFAULT now()`.
3. `TIMESTAMP(6)` -> `timestamptz` (la app ya opera en UTC con `hibernate.jdbc.time_zone: UTC`).
4. `CHAR(36)` se mantiene (decision: minimo cambio).
5. En V10: `TINYINT` -> `smallint`.
6. No tocar indices, PK, FK, UNIQUE ni CHECK salvo sintaxis incompatible.
7. Editar V1..V15 IN SITU (no crear V16 de conversion): el esquema Postgres se crea desde cero, aun no hay Postgres productivo con estas migraciones aplicadas.

Cambio de tests a Testcontainers-Postgres (mismo commit, obligatorio):
- `backend/pom.xml` (dependencyManagement o version directa): anadir `org.testcontainers:junit-jupiter` y `org.testcontainers:postgresql` (scope test). Testcontainers publica BOM `org.testcontainers:testcontainers-bom`; fijar version (p.ej. la vigente estable) en `<properties>`.
- Sustituir la config H2 de `backend/src/test/resources/application-test.yml` por Postgres via `@DynamicPropertySource`. Crear una base class abstracta anotada con `@Testcontainers` y un `@Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16")`, e inyectar `spring.datasource.url/username/password` con `@DynamicPropertySource`. Mantener `ddl-auto: validate` y Flyway activo (que aplique V1..V15 sobre el contenedor).
- Revisar `DevDataSeederTest` (tiene URL H2 `MODE=MySQL` inline en `@TestPropertySource`/`@DynamicPropertySource`): migrar tambien a Postgres o marcar su estrategia; no debe quedar apuntando a H2 si el resto va a Postgres.
- Comprobar Docker antes: `docker version` (o `docker info`). Sin Docker, Testcontainers falla al arrancar el contenedor.

Validacion de cierre Fase 2:
- `cd backend && mvn test` -> verde contra Postgres real (Testcontainers levanta `postgres:16`; Flyway aplica V1..V15; Hibernate `validate` sin desajustes entidad/columna).
- Si `validate` reporta mismatch, iterar en `columnDefinition` de entidades o en el tipo de la migracion hasta 0 errores (el punto delicado es `CHAR(36)` vs el tipo de la columna, y `timestamptz` vs `TIMESTAMP(6)` de las entidades).
- Commit por fase: mensaje `chore(db): fase 2 migracion postgres - traduccion V1..V15 + testcontainers`. No marcar validado sin ejecutar `mvn test` realmente.

### Sprint Fase 2 Postgres + infra Hetzner/WireGuard — SESION 2026-07-09 (EN CURSO, NO cerrado)

Agente lider: Claude Code (Opus 4.8), en solitario. Sprint pausado por cuota; continua otro agente IA.
Plan completo de la sesion (fuera del repo): `C:\Users\Gipsy Davy\.claude\plans\replicated-scribbling-petal.md`.

#### Commits de esta sesion (rama `feat/migracion-postgresql`)
- `c1f2680` chore(db): fase 2 - traduccion V1..V15 a PostgreSQL.
- `1a55d48` fix(db): CHAR(36) -> varchar(36) para validate en Postgres.
- (Base previa: `2514c29` Fase 1 driver+flyway; `3ed9d7d` docs guia Fase 2.)

#### Cambios de codigo aplicados (backend)
- 15 migraciones `V1..V15` traducidas a Postgres: `TIMESTAMP(6) [DEFAULT CURRENT_TIMESTAMP(6)] [ON UPDATE...]` -> `timestamptz [DEFAULT now()]`; `TINYINT` -> `smallint` (V10); `CHAR(36)` -> `varchar(36)` (todas); V11 sin `AFTER`; V13 `now()`. V12 sin cambios.
- `RecipeRatingEntity.stars`: `columnDefinition "TINYINT"` -> `"smallint"`. Todas las entidades: `columnDefinition "CHAR(36)"` -> `"varchar(36)"`.
- Tests reapuntados a Postgres real (NO hay Docker/Testcontainers en la maquina): `application-test.yml` y `DevDataSeederTest` usan `${DB_TEST_URL/USERNAME/PASSWORD}` con driver `org.postgresql`, `ddl-auto=validate`. Dependencia H2 eliminada del `pom.xml`.
- DECISION CORREGIDA del plan (`docs/migracion-*.md` decision 2): NO se mantiene `CHAR(36)`; se usa `varchar(36)`. Motivo: Postgres reporta `CHAR` como `bpchar` (Types#CHAR) y Hibernate espera `VARCHAR` para el String id -> `validate` falla. `varchar(36)` ademas evita padding.

#### Infra creada y DURABLE (verificada en sesion)
- VPS Hetzner CX23, Ubuntu (codename `resolute`), IP publica `167.233.213.242`. Acceso `ssh root@167.233.213.242`.
- WireGuard operativo (ping PC->10.10.0.1 OK):
  - VPS `wg0` = `10.10.0.1/24`, `ListenPort 51820/udp`. Server pubkey `Kuu5/clk/xmMD7C4428zGFsGogs1vf3a9NlTAXFa5z0=`.
  - PC Windows peer = `10.10.0.2/32`, pubkey `/PBRk+zF/9uJHVabGkEH38KjzCeEfI5f5TJccU8/WXE=`. Cliente WireGuard instalado por winget; servicio `WireGuardTunnel$RecetasHetzner` en Running (persiste tras reboot). Split-tunnel (`AllowedIPs = 10.10.0.1/32`) para convivir con ProtonVPN activo.
  - `ufw` VPS: OpenSSH + `51820/udp` permitidos; `5432` permitido SOLO entrando por `wg0` (`ufw allow in on wg0 to any port 5432`).
- PostgreSQL 18 en el VPS (`apt` distro):
  - DBs: `recetas_familiares` (prod, VACIA) y `recetas_familiares_test`. Rol `recetas_app` (LOGIN, minimo privilegio, owner de ambas DBs).
  - `listen_addresses = 'localhost,10.10.0.1'` via `/etc/postgresql/18/main/conf.d/wireguard.conf`. VERIFICADO con `ss`: escucha en `10.10.0.1`, `127.0.0.1`, `::1` — NO en IP publica.
  - `pg_hba.conf`: `host recetas_familiares[_test] recetas_app 10.10.0.0/24 scram-sha-256`.
  - systemd drop-in `postgresql@18-main` con `Wants/After=wg-quick@wg0.service` (bind a 10.10.0.1 tras reboot).
- CREDENCIAL DB `recetas_app`: generada aleatoria EN EL VPS (openssl). NO versionada, NO en este archivo. La tiene el usuario. Proveer por env (`DB_TEST_PASSWORD` para tests; `DB_PASSWORD` para prod). Rotar si se filtro en algun log.

#### Seguridad
- 5432 NO expuesto a internet (verificado por bind + ufw + pg_hba). Trafico DB cifrado por WireGuard (por eso no se fuerza SSL intra-tunel; decidir si se anade igualmente en prod).
- RETRACTADO un falso positivo previo de la sesion: el "5432 publico responde" observado al inicio era ruido de ProtonVPN (no habia Postgres). Sin exposicion real.
- VibeSec sobre config de conexion: PENDIENTE (BLOQUE 7).

#### Estado real tras continuacion Codex (2026-07-09)
Estado por bloque: BLOQUE 1 (codigo) HECHO. BLOQUE 2 (Postgres VPS) HECHO. BLOQUE 3 (WireGuard) HECHO. BLOQUE 4 (tests reales) HECHO. BLOQUE 5 (config app) HECHO. BLOQUE 6 (datos) HECHO. **BLOQUE 7 (smoke E2E + seguridad + cierre) PENDIENTE.**

Bloque 4 ejecutado por Codex:
- Reset real de `recetas_familiares_test` en el VPS antes de retestear.
- Primera corrida Postgres fallo por `Schema-validation` en `recipe_ratings.stars`: Postgres tenia `smallint/int2` y la entidad Java usa `int` validado por Hibernate como `INTEGER`.
- Ajuste aplicado: `V10__create_ratings_schema.sql` y `RecipeRatingEntity.stars` usan `integer`. Motivo: conservar tipo Java/API `int` y no cambiar logica.
- Segundo fallo funcional Postgres: query de historial de chat con parametro nullable (`:beforeCreatedAt IS NULL`) daba `could not determine data type of parameter`. Ajuste aplicado: separar query sin cursor y query con cursor.
- `DevDataSeederTest` ya no asume base global vacia: valida recuentos acotados a la familia sembrada.
- Validacion real: `mvn test -f backend/pom.xml` con `DB_TEST_URL=jdbc:postgresql://10.10.0.1:5432/recetas_familiares_test` -> `Tests run: 116, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, `Total time: 04:15 min`.
- Commit creado: `fe7611f chore(db): fase 2 migracion postgres - tests contra postgres real`.

Bloque 5 ejecutado por Codex:
- `application.yml` fija `driver-class-name: org.postgresql.Driver`.
- `application-dev.yml` usa por defecto `jdbc:postgresql://10.10.0.1:5432/recetas_familiares`, usuario `recetas_app` y password por env.
- `application-prod.yml` deja `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` explicitos por env.
- Decision SSL: no se fuerza `sslmode=require` dentro de WireGuard porque el tunel ya cifra el trafico DB. Si se usa una ruta fuera de WireGuard o un Postgres gestionado, exigir TLS en `DB_URL`.
- Documentado arranque dev en §5.
- Validacion real: `mvn -DskipTests test-compile -f backend/pom.xml` -> `BUILD SUCCESS`.
- Commit creado: `ca9570c chore(config): fase 5 postgres por wireguard`.

Bloque 6 ejecutado por Codex:
- Flyway aplicado en `recetas_familiares` arrancando el backend local contra Postgres Hetzner; `GET /api/v1/health` -> `{"status":"UP",...}`.
- `flyway_schema_history` en Postgres: V1..V15 con `success=true`.
- Migracion de datos hecha por copia JDBC directa desde MySQL local a Postgres Hetzner, tabla por tabla, con columnas explicitas. No se importo `flyway_schema_history` de MySQL. MySQL local queda intacto.
- Recuento origen MySQL antes: `users=2`, `families=1`, `family_members=2`, `refresh_tokens=27`, `chat_messages=3`, `chat_message_clears=1`; resto de tablas funcionales existentes en origen = `0`.
- Recuento destino Postgres despues: `users=2`, `families=1`, `family_members=2`, `refresh_tokens=27`, `chat_messages=3`, `chat_message_clears=1`; resto de tablas funcionales = `0`; `chat_attachments=0` (tabla nueva V15, no existia en origen).
- `refresh_tokens.replaced_by_token_id` se cargo en dos pasos por la FK auto-referente: 4 updates aplicados.

Punto siguiente:
- BLOQUE 7 — Smoke E2E contra Postgres: health, Flyway V1..V15, registro/login, CRUD, sync pull/push, chat REST+WS.
- Ejecutar revision tipo VibeSec sobre config de conexion y documentar que `security-review` fuerte no aplica porque no se modificaron endpoints/auth, aunque si se revisan secretos/env/minimo privilegio/5432.
- Actualizar cierre de `CONTINUAR.md`, crear commit de cierre si todo queda validado y no dejar procesos backend vivos salvo decision explicita.

Metodo: commit por fase; MySQL local intacto como rollback operativo; todo en rama.

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
