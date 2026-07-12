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
- `backend/`: Spring Boot + PostgreSQL (Hetzner via WireGuard) + Flyway + JWT.
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
- Flyway y PostgreSQL como base principal (migrada desde MySQL en julio 2026).

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
- Storage protegido para imagenes y videos; no guardar binarios pesados directamente en la base de datos.
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

Estado actualizado el 2026-07-11:
- Chat imagenes UX ya esta implementado, validado visualmente Desktop<->Android, integrado y publicado en `main`.
- PostgreSQL en Hetzner, migracion de datos, operacion base de backups locales y backend/API publica HTTPS temporal quedaron integrados en `main` desde `feat/migracion-postgresql`.
- Backups offsite cifrados PostgreSQL: CERRADO 2026-07-11 (restic -> Hetzner Storage Box, restore validado). Ver trazabilidad y `docs/postgres-operacion-runbook.md`.
- Ensayo PITR en cluster aislado: CERRADO 2026-07-11 (recuperacion a punto en el tiempo con precision de transaccion, produccion intacta). Ver trazabilidad y runbook.
- CI/CD y rollback backend: CERRADO 2026-07-11 por Codex (opcion A: GitHub Actions con deploy SSH restringido, releases versionadas, rollback probado). Ver trazabilidad.
- Vigilancia dependencias: CERRADO 2026-07-11 por Codex (pgJDBC 42.7.13, suppressions PDFBox/Kotlin acotadas y con caducidad 2026-10-01). Ver trazabilidad.
- Backend/desktop pasan Dependency-Check `vulnerableDeps=0` tras el sprint de vigilancia. El runtime iOS/macOS sigue bloqueado en esta maquina Windows y COD-8 sigue parcial: no hay pruebas iOS ni pruebas UI automatizadas.

Dominio propio: APLAZADO por decision del usuario (2026-07-11). La app es operativa con `sslip.io`; comprar dominio mas adelante. Riesgos aceptados mientras tanto (documentados aqui para no olvidarlos):
- sslip.io es DNS de terceros gratuito sin SLA; si cae, la app no resuelve (ir por IP rompe TLS).
- Las renovaciones Let's Encrypt (~60-90 dias, Caddy automatico) comparten limite de emision con todo `sslip.io`; una renovacion puede fallar por cupo global agotado y dejar el certificado expirado.
- El hostname va acoplado a la IP del VPS; si la IP cambia, hay que reconfigurar todos los clientes.
Sprint futuro cuando exista dominio (~4-12 EUR/año): registro DNS A -> IP del VPS, hostname en Caddyfile, CORS/WS origins en backend, base URL en Android/Desktop/iOS, y verificar emision/renovacion del certificado nuevo.

Prioridad propuesta para el siguiente sprint no autorizado (fijada por el usuario el 2026-07-11):

1. Apuntar clientes a produccion (URL de API configurable). Problema detectado: los clientes instalables NO conectan con Hetzner tal cual. Desktop usa por defecto `http://localhost:8080/` (`desktop/.../api/ApiClient.java`, solo cambiable con `-Dapi.base.url`, sin UI); el APK Android lleva compilada `http://10.0.2.2:8080/` (emulador) en `DEFAULT_API_BASE_URL` de `android/app/build.gradle.kts`, sin ajuste en runtime. Alcance del sprint:
   - Desktop: campo de URL del servidor en Ajustes/Login persistido en Preferences, default `https://recetas.167.233.213.242.sslip.io/`; regenerar instalador Windows.
   - Android: ajuste de URL del servidor en pantalla de configuracion, default produccion; revisar `network_security_config.xml` (cleartext solo para 10.0.2.2; HTTPS produccion no necesita excepcion); regenerar APK.
   - Ambos: validar esquema https (permitir http solo para hosts de desarrollo), revisar derivacion de URL WebSocket (wss) y comprobar `app.upload.base-url` en el VPS apunta a la URL publica (Desktop no normaliza origen de uploads, residual conocido).
   - Validacion: login/sync/chat reales contra Hetzner desde Desktop instalado y movil/emulador con el APK nuevo.
   - Motivo de URL configurable y no horneada: `sslip.io` es temporal; con URL configurable, comprar dominio propio no obligara a redistribuir binarios.
   - Seguridad: VibeSec aplica (red, tokens, URL introducida por usuario).
   - Preflight de dependencias: si el usuario aporta credenciales Sonatype OSS Index, activar y validar el analyzer en backend/desktop. Sin credenciales, mantener OWASP con NVD/CISA y documentar la cobertura reducida.
   - Añadido por auditoria 2026-07-11 (Claude+Codex+Gemini): iOS tambien lleva URL horneada (`ios/composeApp/.../network/ApiClient.kt:23` = `http://localhost:8080/`); incluir iOS en el alcance de URL configurable aunque su runtime siga bloqueado.
   - Añadido por auditoria 2026-07-11: Desktop sync incompleto (NUEVO-1, ALTA): `desktop/.../SyncRepository.java` llama a `sync/pull` sin `limit` (descarga delta completo) y NO aplica `familyNotes` ni `recipePhotos` del pull. Portar paginado de Android y aplicar o retirar esos campos. Tras migrar Desktop, imponer `limit` default server-side en `SyncService.pull` (NUEVO-2, MEDIA).
2. COD-8 siguiente capa: Android `SyncWorker`/colas offline end-to-end con Room fake o DB in-memory; Desktop tests adicionales si aportan valor sin fragilizar.
3. iOS: validar runtime en macOS/dispositivo (Keychain, interceptor 401, Coil autenticado, pull paginado), revisar warnings de casts Keychain y AppIcon con `recetas.png` cuando exista el proyecto Xcode (COD-1/COD-2). Bloqueado sin macOS. Añadido por auditoria 2026-07-11 (NUEVO-4): `ios/.../sync/SyncRepository.kt:29` captura `Exception` generica y traga `CancellationException` (mismo bug que COD-7 ya corregido en Android); re-lanzar cancelaciones.
4. Dominio propio/API estable: cuando el usuario compre el dominio (ver nota de aplazamiento arriba).
5. UX-14 (sprint posterior dedicado): ayuda TOTALMENTE completa en toda la aplicacion. El MVP de Sprint 46 (HelpDialog Desktop, 9 vistas) es solo la base. Alcance objetivo, por fases si hace falta:
   - Desktop: ayuda contextual en TODOS los modulos, dialogos y formularios (crear/editar receta, stock, menu, compra, notas, miembros, exportaciones, busqueda global, diagnostico), cada pestaña de Ajustes, modo cocina y onboarding; tooltips en todos los controles sin label visible (formato `Accion (Ctrl+X)`, delay 400ms); foco y orden de tabulacion documentados en formularios.
   - Android: sistema de ayuda equivalente (pantalla o bottom sheet de ayuda por seccion, accesible desde TopAppBar), con `contentDescription` completo y ayuda del modo cocina/manos libres.
   - iOS: mismo patron cuando el runtime este desbloqueado (COD-1/COD-2).
   - Contenido: microcopy calido y no tecnico, cubriendo cada opcion, atajo, gesto y estado (vacio/error/offline), de forma que el usuario pueda usar toda la aplicacion sin ayuda externa.
   - Criterio de cierre: inventario de pantallas/dialogos vs temas de ayuda al 100%, revision Gemini de textos, accesibilidad verificada (TalkBack/tooltips).

### Roadmap funcional aprobado (2026-07-12, decision del usuario sobre `paraImplementar.txt`)

El usuario aporto 21 peticiones funcionales. Analisis contra estado real y decision registrada:

Ya cubiertos (verificar, no reimplementar):
- (6) Recuperar password: backend CRIT-2 desplegado; cierre en Sprint A.
- (7) Imagen en receta: fotos con ownership ya existen; verificar portada en listados.
- (9) Valoraciones: backend ya tiene valoraciones; falta exponer estrellas en clientes.
- (17) Datos sobreviven a reinstalacion: cumplido por diseño (fuente maestra PostgreSQL + sync).
- (18) Hetzner + backups: CERRADO 2026-07-11.
- (19) Listar miembros: Desktop ya lo tiene; falta pantalla Android.

Orden de sprints aprobado:
- Sprint A: EJECUTADO 2026-07-12 — UX cliente reset password, verificar email, borrar cuenta (punto 6). SMTP produccion sigue como pendiente operativo del usuario.
- Sprint B (quick wins): EJECUTADO 2026-07-12 (ver trazabilidad). (10) creador de receta visible se MOVIO a sprint propio: requiere cambio de contrato sync + migracion Room multiplataforma (regla §3); no es quick win.
- Sprint C (gestion familiar): EJECUTADO 2026-07-12 por Codex (ver trazabilidad). (3) editar miembros queda expuesto en Android y verificado en Desktop; (5) queda cubierto de facto por el modelo actual: registro = primera familia del usuario, que nace OWNER; no existe endpoint para crear familias adicionales. La restriccion plena se disenara con multi-familia.
- Sprint D (multi-familia, (4)+(13)+(12)): CERRADO DE PRODUCTO 2026-07-12. Backend EJECUTADO por Codex, clientes/sync-cache COMPLETADOS por Claude Code, prueba UI Android superada (mediodia), prueba GUI Desktop del usuario superada (tarde), CI backend verde. Ver trazabilidad "Cierre de producto Sprint D".
- Posteriores: (20) presencia online + avisos de actividad (limitacion: sin push, solo con app abierta; encaja con chat fase 4), (11) ranking de usuarios (depende de 9 y 10; plantear como gamificacion ligera acorde a filosofia del producto), (14) chat privado 1:1 (despues de chat fase 4/push).

DESCARTADOS TOTALMENTE por decision del usuario (2026-07-12):
- (16) Comparar recetas con internet + sugerencias IA de modificacion: coste recurrente, consentimiento de datos familiares, complejidad alta para valor incierto. YAGNI.
- (8) en version integrada (API externa de alimentos/recetas): mismos motivos. Solo sobrevive la version simple (abrir navegador) en Sprint B.

Estado verificado de los 22 puntos (2026-07-12 mediodia, Claude Code, contra codigo y UI real):
- COMPLETOS: 1, 2, 4, 5, 6*, 8-simple, 9, 12, 13, 15, 17, 18, 19, 21. (*6 espera SMTP del usuario para E2E de emails.)
  - (5) verificado en codigo: `FamilyService.createFamily` exige OWNER/ADMIN en alguna familia (o usuario sin membresias); MEMBER puro recibe 403. UI Android oculta "Crear familia" segun rol de la familia activa (mas conservador que backend: MEMBER activo con OWNER en otra familia no ve el boton; decision UI aceptada).
  - (21) verificado en codigo y produccion: StarterRecipeSeeder crea 2 EASY + 2 MEDIUM + 1 HARD.
- PARCIALES:
  - (3) rol/expulsion OK (Sprint C); NO existe endpoint para que OWNER/ADMIN edite datos/password de otro miembro (verificado: FamilyController solo tiene invite/list/role/remove/avatar/stats). El "password olvidada" queda cubierto por self-reset CRIT-2 via email. Si se quiere el literal del punto 3, falta sprint backend+clientes con decision de seguridad (admin-reset de password ajena es delicado).
  - (7) foto y portada en DETALLE OK (verificado Android hoy); las cards de LISTADO no muestran portada ni en Android ni en Desktop (`RecipeListView` sin imagenes). Mejora UX pendiente.
  - (14) chat familiar OK; chat privado 1:1 pendiente (tras chat fase 4 push).
- PENDIENTES: (10) creador de receta (sprint propio, contrato sync + Room), (11) ranking (depende de 9+10), (20) presencia online/avisos (sin push, solo app abierta), (22) scroll Desktop al redimensionar.
  - (22) NO estaba en el roadmap anterior. Verificado hoy: 7 vistas Desktop sin ScrollPane envolvente (WeeklyMenuView, CookingView, FamilyMembersView, LoginView, RecipeListView, StockView, NotesView; las de tabla tienen scroll interno pero cabeceras/formularios pueden quedar fuera al reducir ventana). Sprint UX Desktop propio.
- DESCARTADOS: (16) y (8) integrado.

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

Nota actual 2026-07-10: este analisis es historico. La migracion fue autorizada y ejecutada despues; el estado vigente esta en las secciones de Sprint Fase 2/PostgreSQL, operacion Hetzner y backend/API publica.

- Objetivo: evaluar a peticion del usuario si migrar la DB a Supabase o a Postgres en Hetzner. Sin cambios de codigo de migracion.
- Agente: Claude Code en solitario (analisis de solo lectura; no aplicaban skills de seguridad ni otros agentes: no habia diff).
- Aclaracion aportada al usuario: Supabase no es una DB (su motor es Postgres); Hetzner es hosting ortogonal; Supabase Cloud no corre en Hetzner (seria self-hosted). "Full Supabase" = reescritura de backend + 3 clientes + operar stack self-hosted; descartado.
- Decision acordada: Camino 1 = mantener Spring Boot y migrar solo el motor **MySQL 8.0 -> PostgreSQL**, con la DB en Hetzner. Clientes sin cambios.
- Evidencia de viabilidad recogida en ese momento (inspeccion 2026-07-08): todas las `@Query` son JPQL (0 nativeQuery); timestamps por `@PrePersist`/`@PreUpdate`; esquema portable; `DB_URL/USERNAME/PASSWORD` externalizados; dialecto Hibernate autodetectado. Estado final posterior: 15 migraciones Flyway, `varchar(36)`, tests contra PostgreSQL real por WireGuard.
- Entregable original: `docs/migracion-mysql-a-postgresql-plan.md` con decision, evidencia, alcance, decisiones entonces pendientes, plan paso a paso, validacion esperada, riesgos y rollback. Estado actual: decisiones cerradas y plan actualizado como trazabilidad ejecutada.
- Gotcha principal a decidir: Postgres autogestionado en Hetzner implica backups/PITR/hardening propios (Hetzner no da Postgres gestionado nativo).
- Estado original: sprint de migracion no autorizado en ese momento. Estado actual: sprint autorizado, ejecutado y desplegado en `feat/migracion-postgresql`.

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

### Sprint Chat imagenes UX - origen historico cerrado (2026-07-08; cerrado 2026-07-10)

Estado actual: este bloque ya no es pendiente. El alcance se ejecuto en Sprint 47 y continuaciones, con validacion visual real Desktop<->Android e integracion publicada en `main` el 2026-07-10.

- Origen: durante la prueba visual manual del chat, el usuario confirma que las imagenes enviadas/recibidas en Desktop se quedan dentro del chat sin posibilidad de abrirlas ni descargarlas. En Android no se ve la imagen en el chat; solo aparece el globo de mensaje con adjunto, sin abrir ni descargar.
- Severidad producto: funcional. El backend y el multipart existen, pero el adjunto no es consumible de forma suficiente por los usuarios.
- Alcance que dio origen al sprint:
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

### PUNTO DE RETOMA EXACTO — Migracion PostgreSQL en curso (2026-07-09, historico)

Documento redactado como ingeniero senior experto en programacion para retomar sin ambiguedad en la proxima sesion. Hay DOS hilos abiertos en paralelo, en ramas distintas. Leer entero antes de tocar nada.

Estado actual 2026-07-10: este punto de retoma quedo superado por los sprints posteriores. La rama `feat/migracion-postgresql` ya contiene la migracion PostgreSQL, datos, operacion DB, merge de `main` con Chat imagenes UX y despliegue backend/API publica HTTPS temporal.

#### Estado del repositorio
- Rama activa al cerrar la sesion: `feat/migracion-postgresql` (creada desde `main`).
- Historico: en ese momento `main` seguia sin los cambios del chat. Estado actual: Chat imagenes UX ya esta publicado en `main` e integrado en `feat/migracion-postgresql`.
- Carpeta `herztner/` sin versionar (IP VPS + clave publica + comando ssh). No commitear; considerar `.gitignore` mas adelante.

#### Hilo A — Chat imagenes UX (historico, ya integrado)
- Estado actual: `feat/chat-imagenes-ux` fue cerrado, validado visualmente, fusionado a `main` y publicado en remoto el 2026-07-10. Ver secciones "Sprint Chat imagenes UX (cont.)" e "Integracion Chat imagenes UX".
- El residual de prueba visual cross-device quedo cerrado: thumbnails, abrir original, guardar/descargar y estado 404 fueron validados en Desktop y Android.

#### Hilo B — Migracion MySQL -> PostgreSQL (EN CURSO, Fase 1 hecha)
- Tipo de migracion confirmado: **MySQL 8 -> PostgreSQL**, backend Spring Boot INTACTO (misma seguridad). NO es Supabase. Plan completo: `docs/migracion-mysql-a-postgresql-plan.md`.
- Decisiones cerradas (usuario eligio "defaults" + matices):
  - Hosting: Postgres autogestionado en VPS Hetzner (`167.233.213.242`).
  - UUID: decision corregida despues de validar Hibernate/PostgreSQL: usar `varchar(36)`, no `CHAR(36)`.
  - Datos: migrar `FamilyDemo` con `pgloader` (base no limpia).
  - Tests: subir a Testcontainers-Postgres.
  - Pooler: conexion directa para migraciones.
  - **Backend LOCAL por ahora** era el estado del 2026-07-09. Estado actual: backend desplegado en VPS detras de Caddy/HTTPS temporal.
  - **Red: WireGuard** (no Tailscale, no Cloudflare). Justificacion: con backend local, el trafico critico es backend local -> Postgres Hetzner (TCP crudo), no HTTP; WireGuard es la alternativa directa a Tailscale y no necesita dominio. Postgres escuchara SOLO en la interfaz WireGuard, jamas publico. Cloudflare Tunnel + Zero Trust queda APLAZADO para cuando se exponga la API publica (backend en VPS); ademas requiere un dominio en Cloudflare que el usuario NO tiene (la cuenta/plan es gratis, el nombre de dominio no). No se creo cuenta Cloudflare (accion interactiva del usuario, no automatizable).

- FASE 1 — HECHA y commiteada: commit `2514c29` en `feat/migracion-postgresql`.
  - `backend/pom.xml`: `com.mysql:mysql-connector-j` -> `org.postgresql:postgresql` (runtime); `org.flywaydb:flyway-mysql` -> `org.flywaydb:flyway-database-postgresql`. Versiones gestionadas por el BOM de Spring Boot (sin `<version>`).
  - Validado: `mvn test` -> 116 tests, 0 fallos (aun sobre H2 en MODE=MySQL).

- FASE 2 — SIGUIENTE, NO empezada. Alcance exacto:
  - CORRECCION al plan: hay **15 migraciones (V1..V15)**, no 14. El plan `docs/migracion-*.md` §2 dice 14 porque es anterior al chat; el chat anadio `V14__create_chat_schema.sql` y `V15__create_chat_attachments.sql`. Traducir las 15.
  - Traducir `backend/src/main/resources/db/migration/V1..V15` a sintaxis PostgreSQL: eliminar `ON UPDATE CURRENT_TIMESTAMP(6)` (la app fija `updated_at` via `@PreUpdate`); `DEFAULT CURRENT_TIMESTAMP(6)` -> `now()` o quitar default; `TIMESTAMP(6)` -> `timestamptz` (UTC; ya hay `hibernate.jdbc.time_zone: UTC`); usar `varchar(36)`; indices/PK/FK/UNIQUE/CHECK sin cambios.
  - ACOPLAMIENTO CRITICO: los tests usan H2 en `MODE=MySQL` con `ddl-auto=validate` (`backend/src/test/resources/application-test.yml`, y `DevDataSeederTest` con url H2 inline). Al pasar las migraciones a sintaxis Postgres, H2 deja de servir. Por eso Fase 2 DEBE incluir el cambio de tests a Testcontainers-Postgres EN EL MISMO COMMIT para no dejar un commit rojo. Anadir dependencia Testcontainers-postgresql (scope test), base class con `@Container PostgreSQLContainer` + `@DynamicPropertySource`, y quitar la config H2.
  - REQUISITO DE ENTORNO: Fase 2 necesita **Docker** en la maquina (Testcontainers arranca `postgres:16`). PREGUNTA ABIERTA al usuario sin responder: si hay Docker Desktop instalado/corriendo. Alternativas si no: Postgres local levantado por el usuario, o validar solo compilacion y aplazar la ejecucion de tests.
  - Criterio de cierre Fase 2: `mvn test` verde contra Postgres real + arranque con `ddl-auto=validate` sin desajustes entidad/columna.

- FASES POSTERIORES (estado actual): Fase 3 tests, Fase 4 config, Fase 5 infra/WireGuard, Fase 6 datos y Fase 7 smoke E2E ya quedaron cerradas en esta rama. Quedan como riesgos vivos: copia offsite cifrada, ensayo PITR completo, dominio propio y CI/CD/rollback backend.

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
- No hay `ENGINE=`, `AUTO_INCREMENT`, `UNSIGNED`, `ENUM(` en ninguna migracion; los UUID textuales quedaron en `varchar(36)` generados por la app.
- V11 y V12 son ALTER TABLE (avatar_url, storage_path). V13 (`ensure_family_owner_members`) tiene logica INSERT/UPDATE con subconsultas: revisar que la sintaxis sea ANSI/Postgres (evitar extensiones MySQL); traducir si hace falta.

Reglas de traduccion (aplicar a cada archivo):
1. Quitar el fragmento ` ON UPDATE CURRENT_TIMESTAMP(6)` (dejar la columna como `... NOT NULL`). La app fija `updated_at` en `@PreUpdate`.
2. `DEFAULT CURRENT_TIMESTAMP(6)` -> `DEFAULT now()`.
3. `TIMESTAMP(6)` -> `timestamptz` (la app ya opera en UTC con `hibernate.jdbc.time_zone: UTC`).
4. `varchar(36)` se usa para UUID textuales (decision corregida por compatibilidad con Hibernate validate en PostgreSQL).
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
- Si `validate` reporta mismatch, iterar en `columnDefinition` de entidades o en el tipo de la migracion hasta 0 errores (puntos delicados: `varchar(36)` para ids Java `String` y `timestamptz` vs `TIMESTAMP(6)` de las entidades).
- Commit por fase: mensaje `chore(db): fase 2 migracion postgres - traduccion V1..V15 + testcontainers`. No marcar validado sin ejecutar `mvn test` realmente.

### Sprint Fase 2 Postgres + infra Hetzner/WireGuard — SESION 2026-07-09 (CERRADO)

Agente lider: Claude Code (Opus 4.8), en solitario. Sprint pausado por cuota; continua otro agente IA.
Plan completo de la sesion (fuera del repo): `C:\Users\Gipsy Davy\.claude\plans\replicated-scribbling-petal.md`.

#### Commits de esta sesion (rama `feat/migracion-postgresql`)
- `c1f2680` chore(db): fase 2 - traduccion V1..V15 a PostgreSQL.
- `1a55d48` fix(db): CHAR(36) -> varchar(36) para validate en Postgres.
- (Base previa: `2514c29` Fase 1 driver+flyway; `3ed9d7d` docs guia Fase 2.)

#### Cambios de codigo aplicados (backend)
- 15 migraciones `V1..V15` traducidas a Postgres: `TIMESTAMP(6) [DEFAULT CURRENT_TIMESTAMP(6)] [ON UPDATE...]` -> `timestamptz [DEFAULT now()]`; `TINYINT` -> `integer` en V10 tras validar Hibernate; `CHAR(36)` -> `varchar(36)` (todas); V11 sin `AFTER`; V13 `now()`. V12 sin cambios.
- `RecipeRatingEntity.stars`: `columnDefinition "TINYINT"` -> `"integer"`. Todas las entidades: `columnDefinition "CHAR(36)"` -> `"varchar(36)"`.
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
- VibeSec sobre config de conexion: ejecutado por Codex en BLOQUE 7 (ver resultados abajo).

#### Estado real tras continuacion Codex (2026-07-09)
Estado por bloque: BLOQUE 1 (codigo) HECHO. BLOQUE 2 (Postgres VPS) HECHO. BLOQUE 3 (WireGuard) HECHO. BLOQUE 4 (tests reales) HECHO. BLOQUE 5 (config app) HECHO. BLOQUE 6 (datos) HECHO. BLOQUE 7 (smoke E2E + seguridad + cierre) HECHO.

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
- Commit creado: `411e584 chore(data): fase 6 migra datos familydemo a postgres`.

Bloque 7 ejecutado por Codex:
- Smoke REST contra backend local apuntando a Postgres Hetzner:
  - `GET /api/v1/health` -> `UP`.
  - Flyway en `recetas_familiares`: V1..V15 con `success=true`.
  - Registro/login con familia temporal `codex-smoke-*` -> OK.
  - CRUD de `stock-items`: `POST 201`, `GET 200`, `PUT 200`, listado incluye item, `DELETE 204`.
  - `sync/push` + `sync/pull` con `stockItems` -> item creado offline aparece en pull.
  - Chat REST: `POST /chat/messages 201` y `GET /chat/messages 200` con el mensaje.
- Smoke WebSocket/STOMP real:
  - Conexion a `ws://localhost:8080/ws`, `CONNECT` con header `Authorization: Bearer ...`, `SUBSCRIBE /topic/families/{familyId}/chat`.
  - Envio por REST tras la suscripcion; se recibio frame `MESSAGE` con el cuerpo esperado -> OK.
- Limpieza de datos temporales de smoke en Postgres por SQL en orden de FKs. Resultado: `remaining_smoke_users=0`; recuentos volvieron a los migrados (`users=2`, `families=1`, `family_members=2`, `refresh_tokens=27`, `chat_messages=3`, `chat_message_clears=1`, resto funcional `0`, `chat_attachments=0`).
- VibeSec/config aplicado como checklist:
  - `git grep` no encontro la credencial DB real ni asignaciones de `DB_PASSWORD` con secretos en archivos trackeados.
  - `recetas_app`: `rolsuper=false`, `rolcreatedb=false`, `rolcreaterole=false`, `rolreplication=false`, `rolbypassrls=false`.
  - Postgres escucha en `10.10.0.1:5432`, `127.0.0.1:5432`, `::1:5432`; no escucha en la IP publica.
  - `ufw`: default deny incoming; `5432/tcp` permitido solo `on wg0`; `22/tcp` y `51820/udp` expuestos por necesidad operativa.
  - Credenciales por env/placeholders; no secretos versionados; `herztner/` sigue sin versionar.
  - SSL: no se fuerza dentro de WireGuard; exigir TLS si la ruta deja de ser el tunel.
- `security-review` fuerte no se ejecuto como herramienta separada: no hay skill/tool disponible en esta sesion y no se modificaron Spring Security, JWT, CORS, ownership ni endpoints/auth. Alternativa aplicada: revision VibeSec/manual de secretos, red, privilegios DB y exposicion de puerto.
- Documentacion adicional actualizada: `backend/README.md` ya indica PostgreSQL/`varchar(36)` y `docs/migracion-mysql-a-postgresql-plan.md` deja corregida la decision `varchar(36)` frente a `CHAR(36)`.

Riesgos residuales / siguiente operativa:
- Rotacion de la credencial dev de `recetas_app`: resuelta en el sprint operativo posterior (ver seccion siguiente).
- Backups/PITR y prueba de restore: baseline resuelto en el sprint operativo posterior (ver seccion siguiente). Queda pendiente copia offsite cifrada y ensayo PITR completo en cluster aislado.
- Backend/API publica HTTPS temporal ya desplegado en VPS en el sprint posterior; quedan dominio propio estable y estrategia de rollback/CI-CD.
- Flyway 11.7.2 avisa que PostgreSQL 18.4 es mas nuevo que su version soportada probada (hasta PostgreSQL 17). Las migraciones V1..V15 aplicaron y validaron, pero conviene vigilar/actualizar Flyway cuando el BOM lo soporte.
- La migracion de datos fue por copia JDBC controlada, no pgloader; dataset pequeno validado por recuentos, sin transformacion masiva.

Metodo: commit por fase; MySQL local intacto como rollback operativo; todo en rama.

### Sprint Operacion PostgreSQL Hetzner — SESION 2026-07-09 (CERRADO)

Objetivo autorizado por el usuario: ejecutar el siguiente sprint recomendado tras la migracion PostgreSQL, centrado en operacion minima segura de la DB autogestionada: backups, restore, rotacion de credencial y documentacion.

Agente/herramientas:
- Agente ejecutor: Codex.
- VibeSec aplicado como guia/checklist por tocar secretos, red, base de datos y datos familiares.
- Multi-IA/subagentes: no usados; el usuario no autorizo delegacion paralela explicita en esta orden y el sprint manejaba secretos operativos.
- Gemini: no hay conector directo callable en esta sesion; no usado.
- `security-review`: no disponible como herramienta callable en esta sesion; alternativa aplicada: revision manual VibeSec + comprobaciones de secretos, privilegios, bind de red y restore.
- OWASP Dependency-Check: no aplica en este sprint porque no hubo cambios de dependencias ni codigo ejecutable de la app.

Infra aplicada en el VPS:
- Directorios creados con permisos restringidos:
  - `/var/backups/recetas-postgres/logical`
  - `/var/backups/recetas-postgres/base`
  - `/var/backups/recetas-postgres/wal`
- Scripts instalados:
  - `/usr/local/sbin/recetas-postgres-logical-backup`
  - `/usr/local/sbin/recetas-postgres-basebackup`
- Timers systemd habilitados:
  - `recetas-postgres-logical-backup.timer`: diario 03:15 UTC, `pg_dump --format=custom`, retencion 14 dias.
  - `recetas-postgres-basebackup.timer`: domingo 04:15 UTC, `pg_basebackup`, retencion 21 dias.
- WAL archiving activado en `/etc/postgresql/18/main/conf.d/recetas-archive.conf`:
  - `archive_mode=on`
  - `archive_timeout=15min`
  - `archive_command` copia WAL a `/var/backups/recetas-postgres/wal/%f`
- PostgreSQL reiniciado tras activar `archive_mode`; validado despues.

Validacion ejecutada:
- Tunneling DB: `Test-NetConnection 10.10.0.1 -Port 5432` -> `TcpTestSucceeded=True`.
- Estado Postgres: cluster `18/main` online; escucha en `10.10.0.1:5432`, `127.0.0.1:5432`, `::1:5432`; no escucha en IP publica.
- Privilegios: `recetas_app|false|false|false|false|false` para `rolsuper`, `rolcreatedb`, `rolcreaterole`, `rolreplication`, `rolbypassrls`.
- Espacio: `/` con 34G libres aprox.; DBs pequenas (`recetas_familiares` ~9 MB, `recetas_familiares_test` ~10 MB).
- Backup logico manual: `recetas-postgres-logical-backup.service` -> SUCCESS; dump creado en `/var/backups/recetas-postgres/logical/recetas_familiares_20260709T212925Z.dump`.
- Base backup manual: `recetas-postgres-basebackup.service` -> SUCCESS; backup creado en `/var/backups/recetas-postgres/base/base_20260709T212950Z`.
- WAL archiving: `pg_switch_wal()` -> `0/5000000`; `pg_stat_archiver` -> `archived_count=3`, `failed_count=0`.
- Restore logico real en base aislada `recetas_familiares_restore_check`:
  - Recuentos restaurados: `flyway=15`, `users=2`, `families=1`, `family_members=2`, `refresh_tokens=27`, `chat_messages=3`, `chat_message_clears=1`.
  - Base de restore eliminada despues; verificacion final `0`.
- Rotacion credencial `recetas_app`:
  - Se hizo una primera rotacion, pero la validacion local con JShell echo el valor; se considero credencial quemada y se roto de nuevo inmediatamente.
  - Segunda rotacion validada sin imprimir secreto desde VPS y desde la maquina local por JDBC contra `recetas_familiares` y `recetas_familiares_test`.
  - Nueva credencial guardada solo en `herztner/recetas_app.env` (no versionado).
- Busqueda de secretos trackeados: sin matches para credenciales DB reales fuera de `herztner/`.

Documentacion/cambios versionables:
- `.gitignore`: anadido `herztner/` para evitar commits accidentales de claves/config local sensible.
- `docs/postgres-operacion-runbook.md`: runbook operativo de backup, restore, PITR, rotacion y riesgos.
- `CONTINUAR.md`: cierre de sprint y resultados reales.

Riesgos residuales:
- Backups y WAL estan en el mismo VPS/disco. Falta copia offsite cifrada para cubrir perdida total del servidor.
- PITR queda configurado con base backup + WAL, pero falta ensayo completo en cluster aislado.
- Backend/API publica HTTPS temporal ya desplegado en VPS en el sprint posterior; quedan dominio propio estable y estrategia de rollback/CI-CD.
- Flyway 11.7.2 sigue avisando que PostgreSQL 18.4 es mas nuevo que su version soportada probada.

Siguiente sprint recomendado:
- `Sprint Backups offsite cifrados PostgreSQL`: copiar fuera del VPS los backups logicos, base backups y WAL necesarios, cifrados antes de salir del servidor, con verificacion de integridad y restore minimo documentado.

### Revision Gemini post-PostgreSQL/operacion — SESION 2026-07-09

Gemini reviso en solo lectura la rama `feat/migracion-postgresql`, los sprints de migracion/operacion y la documentacion relacionada.

Conclusiones integradas:
- Critico antes de produccion real: backups sin copia offsite. Los backups logicos, fisicos y WAL estan en el mismo VPS/disco.
- Medio: PITR no ensayado en cluster aislado.
- Medio: sin CI/CD ni rollback automatizado para el despliegue del backend.
- Medio: hostname `sslip.io` temporal; falta dominio propio estable.
- Medio: Flyway 11.7.2 aun no soporta oficialmente PostgreSQL 18.4.
- Menor: `docs/migracion-mysql-a-postgresql-plan.md` tenia decisiones antiguas marcadas como pendientes; corregido por Codex tras la revision.

Decision tras revision:
- `feat/migracion-postgresql` queda recomendada para merge a `main`.
- Siguiente sprint recomendado: `Sprint Backups offsite cifrados PostgreSQL`.
- Mantener vivos para infra: copia offsite cifrada, ensayo PITR completo, dominio propio y rollback/CI-CD.

### Sprint 47 - Chat imagenes UX: render fiable, visor y descarga (2026-07-09)

- Objetivo: cerrar la brecha funcional de imagenes del chat (render Android, abrir original y guardar/descargar en Android y Desktop). Autorizado por el usuario; sin cambios de backend.
- Agente lider: Claude Code en solitario. Codex y Gemini NO ejecutados en la sesion; se dejan bloques de verificacion copy-paste (solo lectura) para el usuario, segun regla de bloques IDE.
- Causa raiz del render Android (diagnosticada, no solo parcheada): el backend genera URLs de adjunto absolutas con `app.upload.base-url` (por defecto `http://localhost:8080`). En emulador el host del API es `10.0.2.2`, asi que `AuthInterceptor` no adjuntaba el Bearer (host distinto) y `localhost` apuntaba al propio emulador -> el thumbnail nunca cargaba. Desktop funcionaba porque corre en el mismo host `localhost` que el backend.
- Android implementado:
  - `ChatRepository`: nueva normalizacion de origen de las URLs de adjunto (`/uploads/**`) al host del API del cliente (`apiOrigin`), aplicada en un unico punto que cubre REST y WS (historial, envio, envio imagen, editar, borrar, export y `openRealtime`). URLs externas sin `/uploads/` se dejan intactas.
  - `ChatScreen`: `SubcomposeAsyncImage` con estados loading/error, thumbnail clicable y `ChatImageViewer` a pantalla completa (Dialog full-screen, cerrar y guardar).
  - `RecetasViewModel`: `saveChatImageToGallery` descarga el original con el `httpClient` autenticado y escribe en MediaStore (API 29+ sin permiso; en API<29 el visor solicita `WRITE_EXTERNAL_STORAGE`).
  - `AndroidManifest.xml`: `WRITE_EXTERNAL_STORAGE` con `maxSdkVersion=28`.
- Desktop implementado:
  - `ChatView`: thumbnail clicable (cursor mano + tooltip) abre `openAttachmentViewer` (dialogo modal con el original a tamano completo, fetch autenticado una sola vez) y `saveAttachmentToDisk` con `FileChooser`, reutilizando los bytes ya descargados.
- Validacion ejecutada en la sesion:
  - Android `./gradlew assembleDebug` -> BUILD SUCCESSFUL; `compileDebugKotlin` sin warnings ni errores.
  - Desktop `mvn test` -> 12 tests, 0 fallos; `mvn -DskipTests compile` OK.
  - Backend no tocado; no re-ejecutado.
- Seguridad ejecutada: VibeSec y `security-review` aplicados al diff en la sesion. 0 hallazgos de alta confianza. La normalizacion de URL reduce la superficie de fuga de Bearer (una URL hostil solo puede redirigir a la propia API). Path traversal fail-closed sigue en backend; nombre de archivo local generado por el cliente (timestamp).
- Riesgos residuales:
  - NO se realizo prueba visual real cross-device en esta sesion (no se arrancaron backend/emulador/Desktop GUI). El fix esta verificado por analisis de causa raiz + build/tests, pero falta validacion manual: thumbnail visible, abrir original y guardar Desktop<->Android con imagen real y estado de error ante 404.
  - Desktop ya normaliza el origen de adjuntos de chat en la continuacion Codex del 2026-07-09 (ver seccion siguiente). Queda pendiente solo la prueba visual real cross-device.
  - Sin tests UI automatizados (Compose/JavaFX), coherente con deuda COD-8.

### Sprint 47 (cont.) - Integracion hallazgos Codex/Gemini (2026-07-09)

- El usuario ejecuto los bloques copy-paste; hallazgos verificados contra codigo antes de integrar. Solo se aplico lo confirmado.
- Codex (4 confirmados):
  - MEDIO: `rewriteUploadUrl` (Android) usaba `indexOf("/uploads/")` sobre la cadena completa -> reescribia falsos positivos con `/uploads/` en el query y no bloqueaba `..`. Ahora `uploadPathOrNull` parsea con `java.net.URI`, toma solo el path, rechaza `..` y exige prefijo `/uploads/chat/` o `/uploads/chat_thumbnails/`.
  - BAJO: `saveChatImageToGallery` no comprobaba el resultado de `resolver.update(IS_PENDING=0)`. Ahora si devuelve 0 lanza error y el catch borra el `uri` y falla la operacion (sin "guardada" enganoso).
  - BAJO: visor Desktop no cancelaba al cerrar; añadido flag `closed` en `setOnHidden` que salta el `runLater` si el dialogo ya cerro.
  - BAJO (regla CLAUDE.md): `Files.write` corria en el hilo JavaFX; movido a hilo virtual, volviendo a FX solo para `onStatus`.
- Gemini (1 medio valido + 1 polish):
  - MEDIO (Android, no era FP): `ChatScreen` hace `return` antes del `Scaffold`/`SnackbarHost` global de `RecetasApp`, asi que los avisos (guardar imagen, borrar chat, errores export) no se veian con el chat abierto. Añadido `SnackbarHost` propio en `ChatScreen` que colecta `userMessage`; el colector global de `RecetasApp` se guarda con `rememberUpdatedState(chatOpen)` para no encolar un snackbar rezagado.
  - Polish (Desktop): visor usa `ProgressIndicator` en carga y label de error con estilo `chat-attachment-placeholder`.
  - Descartados como backlog (no scope): icono MoreVert vs "Opciones" en Desktop, animaciones de acciones de mensaje, contentDescription con caption en `ImageView` Desktop.
- Revalidacion: Android `assembleDebug` BUILD SUCCESSFUL (3 warnings preexistentes de tooltip deprecado, ajenos al sprint); Desktop `mvn test` 12/0.
- Seguridad: el endurecimiento de `rewriteUploadUrl` cierra el vector de reescritura por query y traversal; sin nuevos hallazgos.

### Sprint Chat imagenes UX (cont.) - Normalizacion Desktop y revalidacion Codex (2026-07-09)

- Objetivo: cerrar el residual detectado por revision posterior: Android ya reescribia los adjuntos `/uploads/chat*` al origen real del API, pero Desktop dependia de que las URLs absolutas generadas por backend coincidieran con `api.base.url`.
- Cambio aplicado:
  - `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ApiClient.java`: `fetchImage` normaliza solo rutas `/uploads/chat/` y `/uploads/chat_thumbnails/` al origen configurado del API, elimina query/fragment y rechaza path traversal `..`.
  - El Bearer se sigue enviando solo si la URL final pertenece al origen del API; URLs externas no permitidas no se reescriben ni reciben Authorization.
  - `desktop/src/test/java/org/gipsybuho/recetasfamiliares/core/ApiClientHttpTest.java`: tests para reescritura segura de adjuntos de chat y no filtrado de bearer en URLs externas.
- Validacion ejecutada:
  - Desktop `mvn test -f desktop/pom.xml` -> `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
  - Android `.\gradlew.bat testDebugUnitTest assembleDebug` en `android/` -> `BUILD SUCCESSFUL`; reportes unitarios: `ANDROID_TESTS=27 FAILURES=0 ERRORS=0 SKIPPED=0`.
  - `git diff --check` OK (solo avisos CRLF normales).
  - Busqueda de secretos trackeados: `NO_TRACKED_SECRET_MATCHES`.
- Seguridad/VibeSec:
  - No se expone token en URL ni en logs.
  - No se adjunta Authorization a hosts externos.
  - Reescritura acotada a rutas de adjuntos del chat; no afecta fotos externas de recetas.
- Riesgo residual:
  - CERRADO en la continuacion "Validacion visual real Desktop/Android" del 2026-07-09: thumbnail visible, abrir original, guardar/descargar y estado de error ante 404 validados con backend real y clientes GUI.
  - Sin tests UI automatizados Compose/JavaFX.

### Sprint Chat imagenes UX (cont.) - Validacion visual real Desktop/Android (2026-07-09)

- Objetivo: cerrar el residual del Sprint 47 con prueba visual real cross-device, sin cambios de codigo: Android <-> Desktop, thumbnails, visor, guardar/descargar y estado de error ante 404.
- Herramientas/agentes usados:
  - Codex tecnico en esta sesion.
  - Skill VibeSec como checklist manual de superficie de uploads/chat (Bearer, ownership, path traversal, 404 fail-closed).
  - No se ejecuto Gemini/Claude CLI ni OWASP en esta continuacion porque no hubo cambio de codigo ni dependencias; se verifico el comportamiento runtime ya implementado.
- Entorno levantado:
  - MySQL local accesible en `localhost:3306`; DB temporal `recetas_chat_visual_test` recreada.
  - Backend real Spring Boot desde `backend/target/recetas-familiares-backend-0.1.0-SNAPSHOT.jar`, perfil `dev`, `SERVER_PORT=8080`, `UPLOAD_DIR=backend\target\visual-uploads`, `UPLOAD_BASE_URL=http://localhost:8080`.
  - Android Emulator `Pixel_9_Pro`, app debug instalada, API via `10.0.2.2:8080`.
  - Desktop JavaFX lanzado con `mvn javafx:run` desde `desktop/`, API default `http://localhost:8080/`.
- Comandos/resultados relevantes:
  - `mvn -DskipTests package -f backend/pom.xml` -> `BUILD SUCCESS`.
  - `DROP DATABASE IF EXISTS recetas_chat_visual_test; CREATE DATABASE recetas_chat_visual_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` -> `MYSQL_VISUAL_DB_RESET_OK`.
  - `GET http://localhost:8080/api/v1/health` -> `{"status":"UP","checkedAt":"2026-07-09T22:01:22.281966900Z"}`.
  - Flyway backend dev aplico V1..V15 sobre `recetas_chat_visual_test` (log `backend/target/chat-visual-backend.out.log`).
  - `adb install -r android\app\build\outputs\apk\debug\app-debug.apk` -> `Success`.
  - `POST /api/v1/families/{familyId}/chat/messages/images` multipart con `backend\target\chat-visual-image.png` -> `HTTP_STATUS=201`, `attachmentCount=1`, URLs `/uploads/chat/...` y `/uploads/chat_thumbnails/...`.
  - `GET /uploads/chat_thumbnails/5a19101f-6711-49d9-b576-90168d1f68f2.jpg` con Bearer para adjunto roto controlado -> `BROKEN_THUMB_HTTP_STATUS=404`.
- Evidencia visual real capturada en `backend/target/` (no versionada):
  - Android recibio imagen API/WS y renderizo thumbnail: `android-chat-after-api-image.png`.
  - Android abrio visor full-screen y guardo en galeria: `android-chat-image-viewer.png`, `android-chat-image-save-toast.png`; MediaStore mostro `recetas-chat-1783635083850.png` en `Pictures/RecetasFamiliares/`.
  - Desktop cargo historial con thumbnail: `desktop-chat-after-api-image.png`.
  - Desktop abrio visor y guardo a disco: `desktop-chat-image-viewer.png`, `desktop-chat-save-result.png`; archivo generado `backend\target\desktop-chat-saved.png` (6885 bytes).
  - Desktop -> Android: enviado desde boton `Imagen` de Desktop; Desktop `2 mensajes`, Android recibio thumbnail sin recargar: `desktop-chat-after-desktop-send.png`, `android-chat-after-desktop-send.png`.
  - Android -> Desktop: enviado desde selector de imagen Android/MediaStore; Android mostro tercer mensaje y Desktop recibio por realtime/historial abierto: `android-chat-after-android-send.png`, `desktop-chat-after-android-send.png`.
  - 404/error state: insercion temporal en DB con attachment a rutas `/uploads/chat*` inexistentes pero ownership valido; Desktop y Android mostraron `Imagen no disponible`: `desktop-chat-404-state-scrolled.png`, `android-chat-reloaded-for-404-3.png`.
- Seguridad/VibeSec:
  - No se publico 5432 ni se toco infra.
  - No se versionaron tokens/credenciales ni capturas; sesion temporal quedo solo en `backend/target/chat-visual-session.json`.
  - El 404 se valido por ruta autenticada con membership real y fichero inexistente; no se abrieron rutas publicas ni URLs con token.
  - `herztner/` sigue sin versionar y no se toco.
- Riesgos residuales:
  - Sin tests UI automatizados Compose/JavaFX; esta validacion es manual/visual con capturas.
  - Los datos de prueba quedan solo en `recetas_chat_visual_test` y `backend/target/visual-uploads`; no afectan MySQL local original ni ramas de PostgreSQL.

### Sprint Integracion Chat imagenes UX - cierre tecnico de rama (2026-07-10)

- Objetivo: cerrar la rama `feat/chat-imagenes-ux` antes de publicarla/integrarla, revalidando en la sesion actual los cambios ya implementados y documentados el 2026-07-09.
- Agente lider: Codex. Skill VibeSec leida y usada como checklist manual por tratar URLs de adjuntos, Bearer, ownership de uploads y estado de cierre. Multiagente no ejecutado: no habia incertidumbre tecnica nueva tras la validacion visual real ya documentada. Gemini no disponible como herramienta directa; no se preparo bloque nuevo porque no hubo cambios funcionales nuevos. OWASP Dependency-Check no ejecutado: no cambiaron dependencias ni backend; se mantiene como herramienta para auditorias con `NVD_API_KEY`.
- Estado de rama antes de integrar:
  - `feat/chat-imagenes-ux` estaba 2 commits por delante de `origin/feat/chat-imagenes-ux`.
  - `main` y `origin/main` estaban en `e346970`.
  - `herztner/` seguia sin versionar y no se toco.
- Validacion ejecutada en esta sesion:
  - `mvn -f desktop\pom.xml test` -> `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
  - `.\gradlew.bat testDebugUnitTest assembleDebug --rerun-tasks` en `android/` -> `BUILD SUCCESSFUL`, 46 tareas ejecutadas; reportes unitarios: `ANDROID_TESTS=27 FAILURES=0 ERRORS=0 SKIPPED=0`.
  - `git diff --check main..HEAD` -> sin salida, OK.
  - Busqueda de secretos en el diff de la rama (`DB_TEST_PASSWORD`, `DB_PASSWORD`, `JWT_SECRET`, private keys) -> sin coincidencias.
- Seguridad/VibeSec:
  - Confirmado que la normalizacion de adjuntos se acota a `/uploads/chat/` y `/uploads/chat_thumbnails/`.
  - Confirmado que Desktop no envia `Authorization` a hosts externos y que Android reescribe solo rutas de uploads aceptadas al origen del API.
  - No se versionaron capturas, tokens, credenciales ni artefactos de `backend/target`.
- Riesgos residuales:
  - Sin tests UI automatizados Compose/JavaFX; cubierto por validacion visual manual real del 2026-07-09 y tests unitarios/build actuales.
  - Warnings Android preexistentes durante `compileDebugKotlin`: deprecaciones de tooltip/MenuAnchor, `Icons.Filled.Sort` y un safe-call innecesario en `TokenRefreshAuthenticator`.

### Sprint Backend en VPS/API publica - SESION 2026-07-10 (CERRADO)

- Objetivo autorizado: continuar infra tras PostgreSQL/operacion y desplegar el backend Spring Boot en el VPS con API publica segura, sin cambiar logica backend.
- Rama: `feat/migracion-postgresql`. Antes de desplegar se integro `main` por merge commit `40a81be`, resolviendo conflicto solo documental en `CONTINUAR.md` y conservando trazabilidad PostgreSQL + Chat imagenes UX.
- Arquitectura desplegada:
  - URL publica HTTPS temporal: `https://recetas.167.233.213.242.sslip.io`.
  - Caddy en `80/443`, con redireccion HTTP -> HTTPS y reverse proxy a `127.0.0.1:8080`.
  - Backend systemd `recetas-backend.service`, usuario sin login `recetas-backend`, jar en `/opt/recetas-familiares/backend/recetas-familiares-backend.jar`.
  - Env/secrets fuera de Git en `/etc/recetas-familiares/backend.env` (`0640 root:recetas-backend`).
  - Uploads persistentes en `/var/lib/recetas-familiares/uploads`.
  - DB por `jdbc:postgresql://10.10.0.1:5432/recetas_familiares`; `5432` sigue no publico.
- Provisioning aplicado en VPS:
  - Instalado `openjdk-21-jre-headless` (`21.0.11`) y Caddy (`2.6.2`) desde repo Ubuntu.
  - `ufw`: `22/tcp`, `51820/udp`, `80/tcp`, `443/tcp`; `5432/tcp` solo `on wg0`; `8080` no abierto.
  - Servicio endurecido con `NoNewPrivileges=true`, `PrivateTmp=true`, `ProtectSystem=strict`, `ProtectHome=true`, `ReadWritePaths=/var/lib/recetas-familiares/uploads`.
- Validacion ejecutada:
  - Build local: `mvn -f backend\pom.xml -DskipTests package` -> `BUILD SUCCESS`, jar generado.
  - Servicios VPS: `recetas-backend`, `caddy`, `postgresql@18-main` -> `active`.
  - `GET https://recetas.167.233.213.242.sslip.io/api/v1/health` -> `200 OK`, `{"status":"UP",...}`.
  - HTTP plano `http://recetas.167.233.213.242.sslip.io/api/v1/health` -> `308 Permanent Redirect` a HTTPS.
  - `GET /swagger-ui.html` en prod -> `404 Not Found`.
  - Flyway prod: `flyway_schema_history` -> 15 migraciones `success=true`, versiones V1..V15.
  - Puertos publicos: `443` accesible; `5432` y `8080` desde IP publica -> timeout/no acceso. En VPS: Caddy escucha `*:80/*:443`, backend solo `[::ffff:127.0.0.1]:8080`, Postgres `10.10.0.1/127.0.0.1/::1:5432`.
  - Smoke REST publico: registro/login temporal, CRUD stock, `sync/push`, `sync/pull`, chat REST `POST` + historial -> `SMOKE_REST_OK`.
  - Smoke WS publico: `wss://.../ws`, `CONNECT` con JWT en frame STOMP, `SUBSCRIBE /topic/families/{familyId}/chat`, envio REST y recepcion `MESSAGE` -> `SMOKE_WS_OK`.
  - Limpieza smoke: `remaining_smoke_users=0`, `remaining_smoke_families=0`.
  - Suite backend contra PostgreSQL real: tras reset de `recetas_familiares_test`, `mvn -f backend\pom.xml test` -> `Tests run: 116, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, `Total time: 04:10 min`.
- Seguridad/VibeSec:
  - VibeSec usado como checklist por tocar red, secretos, JWT, uploads y servicio publico.
  - No se imprimieron secretos; la credencial DB se paso al VPS por stdin y se elimino el temporal.
  - Busqueda de secretos trackeados sin coincidencias reales fuera de placeholders/documentacion y `herztner/` no versionado.
  - `security-review` no disponible como herramienta callable; alternativa aplicada: revision manual VibeSec + smoke auth/ownership + puertos/permisos.
  - OWASP Dependency-Check no ejecutado: no cambiaron dependencias Maven/Gradle; el sprint fue despliegue/runtime.
- Documentacion:
  - Nuevo `docs/backend-vps-deploy-runbook.md`.
  - `docs/postgres-operacion-runbook.md` actualizado: backend ya desplegado; riesgos infra vivos ajustados.
- Riesgos residuales:
  - Hostname `sslip.io` es temporal y depende de DNS externo; sustituir por dominio propio antes de uso estable.
  - Sin CI/CD ni rollback automatizado de jar.
  - Backups DB siguen sin copia offsite cifrada y PITR completo no ensayado.
  - Flyway 11.7.2 sigue avisando que PostgreSQL 18.4 es mas nuevo que su soporte probado.
  - Caddy 2.6.2 viene del repo Ubuntu; vigilar parches.

### Revision Gemini y merge PostgreSQL/API publica - SESION 2026-07-10

- Gemini reviso en solo lectura lo implementado en la sesion: Chat imagenes UX integrado en `main`, migracion PostgreSQL, operacion DB, despliegue backend/API publica HTTPS temporal, runbooks y riesgos residuales.
- Recomendacion recibida: `feat/migracion-postgresql` estaba tecnicamente lista para fusionarse a `main`.
- Accion ejecutada: merge fast-forward de `feat/migracion-postgresql` a `main` y push a `origin/main`.
- Cambios documentales aplicados antes del merge:
  - `CONTINUAR.md`: la seccion de bloqueantes deja de marcar Chat imagenes UX como pendiente y establece como siguiente sprint `Backups offsite cifrados PostgreSQL`.
  - `CONTINUAR.md`: los puntos historicos de Chat imagenes UX y migracion PostgreSQL quedan marcados como superados/cerrados, evitando instrucciones obsoletas.
  - `docs/migracion-mysql-a-postgresql-plan.md`: actualizado de plan pendiente a trazabilidad ejecutada; mantiene `varchar(36)`, PostgreSQL real por WireGuard, sin Testcontainers y backend/API publica ya desplegado.
- VibeSec usado como checklist manual de cierre por tratar sprint con red, secretos, uploads, datos familiares y despliegue publico. No se tocaron secretos ni `herztner/`.
- Siguiente sprint recomendado tras merge: `Backups offsite cifrados PostgreSQL`, porque es el riesgo critico vivo.

### Cierre de sesion Codex - 2026-07-10

Objetivo del cierre:
- Dejar el repositorio y la documentacion listos para que Codex, Claude Code, Gemini u otro agente retomen sin memoria externa.
- No se implemento runtime nuevo en este cierre; solo documentacion de estado y plan de continuacion.

Estado Git al cerrar:
- Rama activa esperada: `main`.
- `main`, `origin/main`, `feat/migracion-postgresql` y `origin/feat/migracion-postgresql` estaban alineadas tras el merge de PostgreSQL/API publica. El commit funcional/documental previo al cierre era `610ed6f docs: registrar merge postgres a main`.
- `feat/chat-imagenes-ux` queda historica en `3adef78`; su contenido ya esta integrado en `main`.
- `herztner/` sigue fuera de Git y no debe versionarse.

Estado actual del producto/proyecto:
- Backend Spring Boot mantiene la logica original; el cambio de motor MySQL -> PostgreSQL ya esta integrado en `main`.
- PostgreSQL Hetzner esta operativo en el VPS, accesible por WireGuard/local VPS en `10.10.0.1:5432`, y no debe exponerse a internet.
- DBs operativas: `recetas_familiares` y `recetas_familiares_test`; rol `recetas_app` con privilegios minimos.
- Migraciones Flyway V1..V15 estan traducidas a PostgreSQL y validadas; ids textuales en `varchar(36)`, no `CHAR(36)`.
- Tests backend apuntan a PostgreSQL real por env `DB_TEST_URL`, `DB_TEST_USERNAME`, `DB_TEST_PASSWORD`; H2 fue eliminado del backend.
- Datos `FamilyDemo` fueron migrados desde MySQL local a PostgreSQL Hetzner con recuentos antes/despues; MySQL local queda como rollback historico.
- Backups locales DB configurados: `pg_dump` diario, `pg_basebackup` semanal, WAL archiving local y restore logico probado. Todo sigue en el mismo VPS/disco.
- Backend esta desplegado en VPS como `recetas-backend.service`, detras de Caddy/TLS en la URL temporal `https://recetas.167.233.213.242.sslip.io`.
- Chat imagenes UX esta cerrado: thumbnails, visor, guardar/descargar y error 404 validados visualmente Desktop<->Android; integrado en `main`.

Validaciones ya ejecutadas en la sesion/sprints cerrados:
- Desktop chat imagenes: `mvn -f desktop\pom.xml test` -> 14 tests, 0 fallos.
- Android chat imagenes: `.\gradlew.bat testDebugUnitTest assembleDebug --rerun-tasks` -> BUILD SUCCESS; reportes unitarios 27 tests, 0 fallos.
- Backend PostgreSQL real: tras reset de `recetas_familiares_test`, `mvn -f backend\pom.xml test` -> 116 tests, 0 fallos, `BUILD SUCCESS`.
- Backend package para VPS: `mvn -f backend\pom.xml -DskipTests package` -> `BUILD SUCCESS`.
- Smoke publico API: health HTTPS 200/UP, HTTP -> 308 HTTPS, Swagger prod 404, Flyway V1..V15, registro/login, CRUD stock, sync push/pull, chat REST y chat WS/STOMP.
- Seguridad runtime: `5432` y `8080` no accesibles desde IP publica; backend escucha solo loopback; Caddy en 80/443; Postgres en WireGuard/loopback.
- Limpieza smoke: usuarios/familias temporales eliminados.
- Escaneo de secretos trackeados en los cierres: sin secretos reales; solo placeholders documentales.

Herramientas/agentes disponibles y uso recomendado:
- Codex: agente tecnico principal en esta sesion; puede ejecutar shell, editar repo, validar Maven/Gradle, hacer commits y preparar bloques para otros agentes.
- Gemini: no hay herramienta directa callable; usar bloques copy-paste para revision transversal producto/documentacion/UX.
- VibeSec-Skill: disponible y debe usarse como checklist si se toca red, secretos, JWT, uploads, datos familiares, permisos o cierre de sprint.
- `security-review`: no disponible como herramienta callable en esta sesion; usar alternativa manual VibeSec y documentar la limitacion.
- OWASP Dependency-Check: perfiles existen en backend/desktop; ejecutar cuando cambien dependencias o en auditoria dedicada, preferiblemente con `NVD_API_KEY`.
- Multiagente Codex: disponible si el usuario autoriza delegacion paralela; no usar por ceremonia si el sprint maneja secretos operativos.

Riesgos vivos que NO deben borrarse:
- Critico: backups PostgreSQL sin copia offsite cifrada; backups logicos, base backups y WAL siguen en el mismo VPS/disco.
- Medio: PITR completo no ensayado en cluster aislado.
- Medio: hostname `sslip.io` temporal; falta dominio propio estable.
- Medio: no hay CI/CD ni rollback automatizado del jar backend.
- Medio: Flyway 11.7.2 avisa que PostgreSQL 18.4 es mas nuevo que su soporte probado.
- Menor/operativo: Caddy 2.6.2 viene del repo Ubuntu; vigilar parches.
- Menor/calidad: no hay tests UI automatizados Compose/JavaFX; iOS runtime sigue bloqueado sin macOS/dispositivo.

Punto exacto de retoma para el proximo agente:
1. Ejecutar `git checkout main` y `git pull --ff-only`.
2. Confirmar `git status --short --branch` limpio y que `origin/main` esta en la cabecera mas reciente.
3. Leer obligatoriamente `CLAUDE.md`, esta seccion de `CONTINUAR.md`, `docs/postgres-operacion-runbook.md` y `docs/backend-vps-deploy-runbook.md`.
4. No tocar ni imprimir secretos. No versionar `herztner/`. No exponer `5432` ni `8080` al publico.
5. Antes de arrancar el siguiente sprint, confirmar con el usuario el destino offsite y credenciales disponibles. Sin destino/credenciales, no se puede cerrar el sprint de backups offsite.

Siguiente sprint recomendado y plan establecido:

`Sprint Backups offsite cifrados PostgreSQL`

Objetivo:
- Eliminar el riesgo critico de perdida total de datos si se pierde el VPS/disco, copiando backups PostgreSQL fuera del VPS con cifrado antes de salida y restore verificable.

Plan por fases:
1. Preflight:
   - Verificar WireGuard/SSH, servicios `postgresql@18-main`, timers locales de backup, `pg_stat_archiver`, backups recientes y espacio.
   - Confirmar que `5432` sigue solo en WireGuard/loopback y que no hay secretos en Git.
2. Decision de destino offsite:
   - Preferencia tecnica: repositorio `restic` cifrado sobre destino S3 compatible, Backblaze B2, Cloudflare R2, AWS S3, Hetzner Storage Box/SFTP o destino equivalente que proporcione el usuario.
   - Guardar credenciales solo en el VPS, por ejemplo `/etc/recetas-familiares/offsite-backup.env` con permisos `0600 root:root`.
3. Implementacion:
   - Instalar/configurar herramienta de copia cifrada (`restic` recomendado; `rclone` solo como backend/transporte si aplica).
   - Crear script root-only `/usr/local/sbin/recetas-postgres-offsite-backup` para copiar `/var/backups/recetas-postgres` al repositorio cifrado.
   - Crear `recetas-postgres-offsite-backup.service` y `.timer`, programado despues de los backups locales.
   - Aplicar politica de retencion offsite compatible con local: al menos 14 dias logicos, 21 dias base backups, WAL suficiente para la ventana PITR definida.
4. Validacion obligatoria:
   - Ejecutar backup offsite manual y comprobar snapshot/listado remoto.
   - Ejecutar `restic check` o verificacion equivalente.
   - Restaurar desde offsite a un directorio temporal aislado.
   - Validar al menos `pg_restore --list` del dump mas reciente y, si es viable, restaurar en DB aislada y comparar recuentos basicos.
   - Confirmar logs sin secretos y permisos correctos.
5. Documentacion y cierre:
   - Actualizar `docs/postgres-operacion-runbook.md` con comandos reales de backup/restore offsite y rotacion de credenciales.
   - Actualizar `CONTINUAR.md` con comandos ejecutados, salidas reales, riesgos residuales y siguiente sprint.
   - Commit por fase con mensaje sugerido: `chore(infra): backups offsite cifrados postgres`.

Sprints posteriores recomendados:
1. Ensayo PITR completo en cluster aislado.
2. Dominio propio/API estable y actualizacion de Caddy/CORS/WS origins.
3. CI/CD y rollback automatizado del backend.
4. Auditoria dependencias/OWASP y actualizacion Flyway cuando soporte PostgreSQL 18.x oficialmente.
5. Tests UI automatizados Compose/JavaFX para flujos criticos.

### Sprint Backups offsite cifrados PostgreSQL — CERRADO (2026-07-11)

- Objetivo: eliminar el riesgo critico de perdida total de datos copiando los backups PostgreSQL fuera del VPS con cifrado antes de salida y restore verificado.
- Agente lider: Claude Code en solitario. Codex/Gemini no aplican: sprint de infraestructura con secretos operativos y SSH directo al VPS (criterio ya documentado en el cierre 2026-07-10).
- Destino elegido por el usuario: Hetzner Storage Box (`u630198`). Credenciales aportadas via `herztner/storagebox.env` (fuera de Git).
- Implementado en el VPS:
  - restic 0.18.1 (repo Ubuntu) + sshpass (solo para el bootstrap de la clave).
  - Clave SSH dedicada `/root/.ssh/storagebox_ed25519`; `authorized_keys` subido por SFTP puerto 22 (el puerto 23/SSH de la Storage Box esta desactivado; no hizo falta activarlo). Auth por password ya no se usa tras el bootstrap.
  - Alias `storagebox` en `/root/.ssh/config`; repositorio restic cifrado `sftp:storagebox:recetas-postgres-restic` (id `0f2acf7603`).
  - Secretos `0600 root:root`: `/etc/recetas-familiares/storagebox.env` y `/etc/recetas-familiares/offsite-backup.env` (passphrase restic generada en el VPS).
  - `/usr/local/sbin/recetas-postgres-offsite-backup` (0700): backup tag `scheduled` + `forget --keep-daily 14 --keep-weekly 5 --prune` + `check`. Falla cerrado.
  - `recetas-postgres-offsite-backup.service` + `.timer` (diario 05:15 UTC, tras logico 03:15 y basebackup dominical 04:31; `Persistent=true`), timer `enabled` y activo.
  - Copia de emergencia de la passphrase restic guardada fuera del VPS en `herztner/restic-offsite-passphrase.env` (verificado que Git la ignora).
- Validacion ejecutada (2026-07-11):
  - Preflight: servicios `postgresql@18-main`/`recetas-backend`/`caddy` active; WireGuard operativo (la unit `wg-quick@wg0` estaba `failed` por un restart viejo `wg0 already exists`; tunel vivo, `reset-failed` aplicado, unit `enabled`); `pg_stat_archiver` failed_count=0; backups locales presentes; 34G libres.
  - Ejecucion manual del service -> OK en ~5s; `restic snapshots` muestra snapshot `ec451073` (69.7 MiB); `restic check` sin errores.
  - Restore offsite: `restic restore latest` a directorio aislado -> `logical/`, `base/`, `wal/` presentes; dump mas reciente restaurado en DB aislada `recetas_familiares_offsite_check`; recuentos identicos a produccion (flyway=15, users=2, families=1, family_members=2, chat_messages=3); DB y temporales eliminados.
  - Nota operativa: `pg_restore --list` fallo dentro de `/root` por permisos del usuario postgres; el restore completo desde `/tmp` valida el dump. Documentado en runbook.
- Seguridad ejecutada: skill VibeSec invocada en sesion como checklist; verificado: secretos `0600`/`0700` root-only, logs journald sin passwords/passphrases, temporales de bootstrap borrados, `5432` solo WireGuard/loopback y `8080` solo loopback (sin cambios), arbol Git limpio y `herztner/*` ignorado. `security-review`/Dependency-Check no aplican: sin cambios de codigo de aplicacion ni dependencias.
- Archivos modificados en repo: `docs/postgres-operacion-runbook.md`, `CONTINUAR.md`. Resto del trabajo es configuracion en VPS/Storage Box.
- Riesgos residuales:
  - VPS y Storage Box comparten proveedor/cuenta Hetzner; no hay tercera copia en proveedor independiente.
  - Passphrase restic con una unica copia fuera del VPS (`herztner/`); perderla junto al VPS hace irrecuperable el repositorio offsite.
  - PITR completo sigue sin ensayar en cluster aislado (siguiente sprint recomendado).
  - Restore offsite validado con volumen pequeño actual; revalidar cuando el volumen crezca.

### Sprint Ensayo PITR en cluster aislado — CERRADO (2026-07-11)

- Objetivo: validar recuperacion real a punto en el tiempo (base backup + WAL) sin tocar el cluster activo.
- Agente lider: Claude Code en solitario. Codex/Gemini no aplican (infra operativa con SSH directo al VPS, mismo criterio que el sprint offsite).
- Metodo:
  - Marcadores de precision en la DB de mantenimiento `postgres` (tabla temporal `pitr_marker`, sin tocar datos de la app): marcador 1 a las 08:25:16Z, target `T_MID=08:25:19.749326Z`, marcador 2 a las 08:25:22Z; `pg_switch_wal()` tras cada uno (WAL 07 y 08 archivados, failed_count=0).
  - Cluster aislado en `/var/tmp/pitr-test`: base backup `base_20260709T212950Z` + WAL del archivo local, `recovery_target_time=T_MID`, `recovery_target_action=promote`, sin TCP (solo socket local), `archive_mode=off` para no contaminar el archivo WAL.
- Resultado:
  - Recovery paro exactamente antes del commit del marcador 2 (`recovery stopping before commit of transaction 2130`); cluster promovido a timeline 2.
  - Cluster recuperado contenia SOLO el marcador 1 (produccion tiene ambos): precision de transaccion demostrada.
  - Recuentos de `recetas_familiares` identicos a produccion (flyway=15, users=2, families=1, family_members=2, chat_messages=3).
- Trampas documentadas en runbook: configs Debian fuera del basebackup (crear `postgresql.conf`/`pg_hba.conf` minimos), `max_connections` >= primario (con 20 aborta), `archive_mode=off` obligatorio en el ensayo, `cp: cannot stat 00000002.history` es sondeo normal de timelines.
- Limpieza verificada: cluster parado, `/var/tmp/pitr-test` eliminado, tabla `pitr_marker` borrada de prod.
- Produccion intacta tras el ensayo: `postgresql@18-main`/`recetas-backend`/`caddy` active, `pg_is_in_recovery()=f`, archiver sin fallos, health publico HTTP 200.
- Seguridad: sin cambios de codigo, dependencias, secretos ni superficie de red (cluster de ensayo sin TCP y desechado). VibeSec invocado en esta misma sesion (sprint offsite); para este ensayo aplican los mismos controles verificados. `security-review`/Dependency-Check no aplican.
- Archivos modificados en repo: `docs/postgres-operacion-runbook.md`, `CONTINUAR.md`.
- Riesgo residual: el ensayo restauro desde los backups locales; falta ensayar el mismo PITR partiendo exclusivamente del repositorio offsite (el restore offsite en si ya esta validado). Recuperacion con volumen de datos pequeño; revalidar cuando crezca.

### Cierre de sesion Claude Code - 2026-07-11

Objetivo del cierre:
- Dejar repositorio y documentacion listos para retomar el siguiente sprint sin memoria externa.
- En este cierre no queda runtime nuevo pendiente; todo lo implementado en la sesion esta validado, documentado y pusheado.

Estado Git al cerrar:
- Rama `main` alineada con `origin/main` en `6b2c20d`, arbol limpio.
- Commits de la sesion: `9c8549c` (backups offsite cifrados), `b74c8f4` (ensayo PITR validado), `6b2c20d` (dominio aplazado, prioridades reordenadas).

Cerrado en esta sesion (2026-07-11):
- Sprint Backups offsite cifrados PostgreSQL: restic -> Hetzner Storage Box `u630198` por SFTP/22 con clave dedicada; timer diario 05:15 UTC; retencion 14d/5w; restore desde offsite validado en DB aislada con recuentos identicos a prod. Passphrase restic: copia unica fuera del VPS en `herztner/restic-offsite-passphrase.env` (Git la ignora; no perderla).
- Sprint Ensayo PITR en cluster aislado: recuperacion a punto en el tiempo con precision de transaccion demostrada con marcadores; produccion intacta; procedimiento y trampas en `docs/postgres-operacion-runbook.md`.
- Decision de producto: dominio propio APLAZADO por el usuario; app sigue en `https://recetas.167.233.213.242.sslip.io` con riesgos aceptados documentados en la seccion 8.

Estado operativo del VPS al cerrar:
- Servicios `postgresql@18-main`, `recetas-backend`, `caddy` active; health publico 200.
- Backups: local (logico diario 03:15, basebackup domingos 04:15, WAL) + offsite cifrado (05:15). `pg_stat_archiver` sin fallos.
- WireGuard operativo; la unit `wg-quick@wg0` tuvo estado `failed` cosmetico (restart viejo con `wg0 already exists`), limpiado con `reset-failed`; si reaparece con tunel vivo, no es incidencia.
- Nada nuevo expuesto: `5432` solo WireGuard/loopback, `8080` solo loopback, Storage Box solo accesible con clave desde el VPS.

Punto exacto de retoma para el proximo agente:
1. `git checkout main && git pull --ff-only`; confirmar `git status` limpio en `6b2c20d` o posterior.
2. Leer `CLAUDE.md`, esta seccion, `docs/postgres-operacion-runbook.md` y `docs/backend-vps-deploy-runbook.md`.
3. No imprimir secretos; no versionar `herztner/`; no exponer `5432`/`8080`; los `.env` de `herztner/` llevan CRLF de Windows (hacer `sed -i 's/\r$//'` si se copian al VPS).
4. Acceso VPS: `ssh root@167.233.213.242` (clave ya instalada en esta maquina).

Siguiente sprint recomendado (NO autorizado): `CI/CD y rollback backend`

Objetivo:
- Eliminar el deploy manual del jar y ganar rollback operativo en un comando.

Decision previa que debe tomar el usuario al arrancar:
- Opcion A (CI/CD completo): GitHub Actions construye, testea y despliega via SSH al VPS. Requiere crear una clave SSH dedicada de deploy (usuario no-root restringido) y guardarla en GitHub Secrets: superficie nueva a valorar con VibeSec.
- Opcion B (conservadora): pipeline de Actions solo build+test; deploy/rollback mediante script local versionado que ejecuta el usuario desde su PC via SSH. Sin secretos en GitHub.
- Nota tecnica para ambas: los tests backend requieren PostgreSQL real (`DB_TEST_URL`); en Actions usar service container `postgres:18` (H2 fue eliminado).

Plan por fases:
1. Estructura de releases en VPS: `/opt/recetas-familiares/backend/releases/<fecha>-<gitsha>.jar` + symlink `current.jar`; `recetas-backend.service` pasa a apuntar al symlink; conservar ultimas 5 releases.
2. Script de deploy (local o Actions segun opcion): copiar jar nuevo a releases, mover symlink, `systemctl restart recetas-backend`, esperar health 200; si falla, no avanzar el symlink.
3. Script de rollback `recetas-backend-rollback`: symlink a la release anterior + restart + health check.
4. Pipeline GitHub Actions: `mvn test` (con Postgres service) + package en cada push a `main`; artefacto jar versionado; deploy segun opcion elegida.
5. Validacion obligatoria: deploy real de prueba con health 200, rollback real de prueba con health 200, y runbook `docs/backend-vps-deploy-runbook.md` actualizado con ambos flujos.
6. Cierre: VibeSec (secretos/SSH/CI), trazabilidad en este archivo, commit `chore(infra): ci/cd y rollback backend`.

Sprints posteriores recomendados (orden vigente de la seccion 8): vigilancia dependencias, COD-8 siguiente capa, iOS runtime (bloqueado sin macOS), dominio propio (cuando se compre), UX-14.

### Sprint CI/CD y rollback backend — CERRADO (2026-07-11)

- Objetivo: eliminar el deploy manual del jar backend y dejar rollback operativo en un comando.
- Agente tecnico ejecutor: Codex. Claude Code es el agente principal del proyecto segun la regla operativa, pero no hubo herramienta directa para delegar en Claude/Gemini; no se consultaron otros agentes porque el sprint manejaba SSH, Secrets y VPS productivo. Skill usada: VibeSec-Skill como checklist manual de seguridad.
- Decision del usuario: opcion A, CI/CD completo con GitHub Actions desplegando por SSH al VPS.
- Implementado en repo:
  - `.github/workflows/backend-ci-cd.yml`: en `push` a `main`, service container `postgres:18`, `mvn test`, `mvn -DskipTests package`, artifact jar y deploy automatico.
  - `scripts/backend/deploy-backend-ci.sh`: deploy por SSH con known_hosts estricto y jar por stdin.
  - `scripts/backend/rollback-backend.ps1` y `scripts/backend/rollback-backend.sh`: rollback remoto en un comando.
  - `infra/backend/*`: scripts root-owned para deploy, rollback, dispatcher SSH forzado, sudoers y unit systemd con `current.jar`.
  - `docs/backend-vps-deploy-runbook.md`: actualizado con CI/CD, rollback, releases, Secrets y riesgos.
- Implementado en VPS:
  - Releases versionadas en `/opt/recetas-familiares/backend/releases/<fecha>-<gitsha>.jar`.
  - Symlink activo `/opt/recetas-familiares/backend/current.jar`; `recetas-backend.service` apunta al symlink.
  - Release inicial desde jar legado: `20260711T085302Z-abd9030bb3f9.jar`.
  - Usuario `recetas-deploy` con password bloqueada, shell `/bin/bash` solo para permitir comando forzado, `authorized_keys` con `restrict,command="/usr/local/sbin/recetas-backend-ssh-dispatch"`.
  - Sudoers limitado a `/usr/local/sbin/recetas-backend-deploy *` y `/usr/local/sbin/recetas-backend-rollback`.
  - Clave SSH dedicada generada en `herztner/recetas-backend-deploy-ed25519` (fuera de Git) y guardada como `BACKEND_DEPLOY_KEY` en GitHub Secrets. Tambien configurados `BACKEND_DEPLOY_HOST`, `BACKEND_DEPLOY_PORT`, `BACKEND_DEPLOY_USER`, `BACKEND_DEPLOY_KNOWN_HOSTS`.
- Validacion ejecutada en esta sesion:
  - `mvn -f backend\pom.xml test` inicialmente fallo porque `recetas_familiares_test` conservaba datos de pruebas anteriores (92 fallos por `409 Email is already registered`); se reseteo SOLO `recetas_familiares_test`.
  - Tras reset: `mvn -f backend\pom.xml test` -> 116 tests, 0 fallos, `BUILD SUCCESS`.
  - `mvn -f backend\pom.xml -DskipTests package` -> `BUILD SUCCESS`.
  - Deploy de prueba por el canal restringido `recetas-deploy`: `20260711T090617Z-abd9030bb3f9.jar` -> comando 0 + health publico `200/UP`.
  - Rollback real con `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backend\rollback-backend.ps1` -> `rolled back to 20260711T090456Z-abd9030bb3f9` + health publico `200/UP`.
  - GitHub Actions real tras push de `88f9129`: run `29147299227`, jobs `Build and test backend` y `Deploy backend` completados con `success`.
  - Tras Actions, `current.jar` apunta a `/opt/recetas-familiares/backend/releases/20260711T091234Z-88f91292cdcd.jar`; health publico `200/UP`; servicios `recetas-backend`, `caddy`, `postgresql@18-main` active.
  - Retencion: 4 releases presentes, por debajo del maximo de 5.
  - Seguridad de red verificada: Caddy en `80/443`; backend en loopback `8080`; PostgreSQL en `10.10.0.1/127.0.0.1/::1:5432`; `ufw` solo permite `5432/tcp on wg0`.
  - Prueba negativa: `ssh recetas-deploy@167.233.213.242 id` con la clave de deploy devuelve `command not allowed` (exit 126).
- Seguridad/VibeSec:
  - `git check-ignore` confirma que `herztner/recetas-backend-deploy-ed25519` y `.pub` estan ignoradas.
  - Busqueda de secretos trackeados sin claves reales; solo placeholders documentales (`DB_PASSWORD=<secret>`, `JWT_SECRET=<secret>`).
  - `visudo -cf /etc/sudoers.d/recetas-backend-deploy` OK.
  - El dispatcher valida `release-id`, limita upload a 200 MiB, exige jar/zip por magic bytes, staging bajo `/var/tmp/recetas-backend-deploy` y no permite shell arbitraria.
  - El deploy cambia temporalmente `current.jar` para arrancar la candidata; si health falla, revierte al symlink anterior, reinicia y borra la candidata. No deja una release no sana como `current.jar`.
  - `security-review` no disponible como herramienta callable en esta sesion; alternativa aplicada: VibeSec manual + pruebas negativas + revision de puertos/secretos/sudoers.
- Archivos modificados: `.github/workflows/backend-ci-cd.yml`, `infra/backend/*`, `scripts/backend/*`, `docs/backend-vps-deploy-runbook.md`, `CONTINUAR.md`.
- Riesgos residuales:
  - La clave de deploy vive en GitHub Secrets; si se compromete, un atacante podria desplegar un jar malicioso. Mitigacion: usuario no-root, comando forzado, known_hosts fijado, sudoers limitado y rotacion de clave ante sospecha.
  - `main` despliega automaticamente; si el repo empieza a aceptar contribuciones externas, activar branch protection/reviews obligatorios antes de confiar en CD directo.
  - El hostname `sslip.io` sigue aplazado por decision del usuario; dominio propio pendiente.
  - Flyway sigue avisando que PostgreSQL 18.4 es mas nuevo que su soporte probado.
- Siguiente sprint recomendado (NO autorizado): vigilancia dependencias (revisar `desktop/owasp-suppressions.xml` antes de 2026-10-01, monitorizar Kotlin >= 2.4.20 estable cuando exista, PDFBox/Caddy/Flyway).

### Sprint Vigilancia dependencias - CERRADO (2026-07-11)

- Objetivo: revisar dependencias criticas y suppressions antes de 2026-10-01, cerrar vulnerabilidades accionables y dejar riesgos residuales trazados.
- Agente tecnico ejecutor: Codex. No se usaron subagentes ni Gemini: no habia herramienta directa Gemini callable y el alcance era acotado. Skill usada: VibeSec-Skill como checklist manual. `security-review` no estuvo disponible como herramienta callable.
- Contexto leido en la sesion: `CLAUDE.md`, `CONTINUAR.md`, `docs/backend-vps-deploy-runbook.md`, `docs/postgres-operacion-runbook.md`, manifests Maven/Gradle afectados y `desktop/owasp-suppressions.xml`.
- Cambios aplicados:
  - `backend/pom.xml`: override explicito `postgresql.version=42.7.13` para sacar pgJDBC de la rama vulnerable 42.7.4-42.7.11 (CVE-2026-54291).
  - `desktop/owasp-suppressions.xml`: nota Kotlin actualizada; se mantiene suppression exacta de `kotlin-stdlib:2.4.0` hasta que exista Kotlin 2.4.20 estable.
  - `desktop/owasp-suppressions.xml`: suppressions exactas y caducadas el 2026-10-01 para `org.apache.pdfbox:pdfbox:3.0.7` y `org.apache.pdfbox:pdfbox-io:3.0.7` por CVE-2026-23907/CVE-2026-33929. Justificacion: Dependency-Check las mapea a PDFBox/ExtractEmbeddedFiles; la app Desktop solo genera PDFs con `PDDocument`/`PDPage`/`PDPageContentStream` y no extrae adjuntos de PDFs de usuario.
- Validacion ejecutada:
  - `mvn -f backend\pom.xml -DskipTests verify -P security-audit` inicial -> `BUILD FAILURE` por `org.postgresql:postgresql:42.7.11` / CVE-2026-54291.
  - Disponibilidad Maven: `org.postgresql:postgresql:42.7.13` disponible; `org.apache.pdfbox:pdfbox:3.0.8` no disponible; `org.jetbrains.kotlin:kotlin-stdlib:2.4.20` no disponible; `2.4.20-Beta1` si disponible.
  - Tras cambios: `mvn -f backend\pom.xml -DskipTests verify -P security-audit` -> `BUILD SUCCESS`; reporte OWASP backend `vulnerableDeps=0`, `suppressed=0`.
  - `mvn -f desktop\pom.xml -DskipTests verify -P security-audit` -> `BUILD SUCCESS`; reporte OWASP desktop `vulnerableDeps=0`, `suppressed=3`.
  - `mvn -f backend\pom.xml dependency:tree -Dincludes=org.postgresql:postgresql` -> `org.postgresql:postgresql:jar:42.7.13:runtime`.
  - `mvn -f backend\pom.xml test` inicial fallo por datos residuales en `recetas_familiares_test` (92 fallos `409 Email is already registered`); se recreo SOLO esa DB de test en el VPS.
  - Tras reset de test DB: `mvn -f backend\pom.xml test` -> 116 tests, 0 fallos, `BUILD SUCCESS`.
  - `mvn -f desktop\pom.xml test` -> 14 tests, 0 fallos, `BUILD SUCCESS`.
  - Push del commit `e2b122b` disparo GitHub Actions `Backend CI/CD` run `29147923334`: jobs `Build and test backend` y `Deploy backend` -> `success`; health publico `200/UP`; `current.jar` en `/opt/recetas-familiares/backend/releases/20260711T093600Z-e2b122b9a011.jar`; servicios `recetas-backend`, `caddy`, `postgresql@18-main` active; 5 releases retenidas.
- Seguridad/VibeSec:
  - Sin secretos nuevos ni cambios en `herztner/`, GitHub Secrets, Caddy, WireGuard, PostgreSQL, puertos o backups.
  - No se expuso `5432` ni `8080`; el sprint solo cambio declaracion de dependencias y suppressions versionadas.
  - Suppressions acotadas por GAV exacto, CVE exacto y fecha de expiracion; no se anadio suppression global por CPE.
  - Dependency-Check avisa que el analyzer Sonatype OSS Index esta deshabilitado por falta de credenciales; aceptado como riesgo de cobertura, NVD si se uso.
- Archivos modificados: `backend/pom.xml`, `desktop/owasp-suppressions.xml`, `CONTINUAR.md`.
- Riesgos residuales:
  - Revisar antes de 2026-10-01: Kotlin 2.4.20 estable, PDFBox 3.0.8+ en Maven Central y mantener o retirar suppressions.
  - Flyway sigue avisando que PostgreSQL 18.4 es mas nuevo que su soporte probado.
  - Caddy 2.6.2 sigue pendiente de vigilancia de parches del repo Ubuntu.
  - No se actualizaron Android/iOS: se inspeccionaron versiones, pero no habia accion directa en este sprint y no se ejecutaron builds Gradle.
- Siguiente sprint recomendado (NO autorizado): COD-8 siguiente capa, tests Android `SyncWorker`/colas offline end-to-end con Room fake o DB in-memory.

Actualizacion posterior (2026-07-11): el usuario fijo como siguiente sprint `Apuntar clientes a produccion (URL de API configurable)`; la recomendacion COD-8 pasa a segundo lugar. Detalle completo del alcance en la seccion 8.

### Revision Gemini - Sonatype OSS Index (2026-07-11)

- Hallazgo procesado por Codex a peticion del usuario: `CONFIRMADO`, Dependency-Check tiene el analyzer Sonatype OSS Index deshabilitado por falta de credenciales/token.
- Verificacion real en esta sesion:
  - Entorno: `NVD_API_KEY` presente; `OSS_INDEX_USERNAME`, `OSS_INDEX_PASSWORD`, `OSSINDEX_USERNAME`, `OSSINDEX_PASSWORD`, `SONATYPE_OSS_INDEX_USERNAME` y `SONATYPE_OSS_INDEX_TOKEN` ausentes (solo se comprobo presencia, no se imprimieron valores).
  - Plugin OWASP verificado con `mvn org.owasp:dependency-check-maven:12.2.2:help -Ddetail=true -Dgoal=check`: parametros disponibles `ossIndexUsername`, `ossIndexPassword`, `ossIndexServerId`, `ossIndexAnalyzerEnabled`, `ossIndexWarnOnlyOnRemoteErrors`.
  - `backend/pom.xml` y `desktop/pom.xml` solo configuran NVD (`nvdApiKeyEnvironmentVariable=NVD_API_KEY`); no hay configuracion OSS Index.
- Decision aplicada: no tocar `backend/pom.xml` ni `desktop/pom.xml` sin credenciales, porque no se podria validar el analyzer y tocar `backend/**` dispararia CI/CD y deploy a produccion con una mejora no comprobable.
- Actualizacion tras prueba de credenciales aportadas por el usuario (2026-07-11):
  - Se probo autenticacion directa sin guardar secretos ni imprimir token.
  - Endpoint legado `https://ossindex.sonatype.org/api/v3/component-report` con Basic Auth -> `401 Unauthorized`.
  - Endpoint actual Sonatype Guide `https://api.guide.sonatype.com/api/v3/component-report` con Basic Auth -> `401 Unauthorized` usando el email aportado y tambien username placeholder.
  - Prueba Maven real con `settings.xml` temporal fuera del repo, `ossIndexServerId=ossindex`, `ossIndexAnalyzerUrl=https://api.guide.sonatype.com` y `org.owasp:dependency-check-maven:12.2.2:check` sobre backend -> `BUILD FAILURE`; log relevante: `Sonatype OSS Index / Guide has invalid credentials`, `401 Unauthorized`.
  - Resultado: credencial no valida para OSS Index/Guide API en esta sesion. No se modificaron POMs porque versionar el token es inseguro y la configuracion dejaria rojo Dependency-Check; se mantiene NVD/CISA + analizadores locales. El token fue pegado en chat y debe considerarse expuesto: rotar/revocar en Sonatype si procede.
- Siguiente sprint: si se aportan credenciales, configurar el analyzer preferentemente via Maven `settings.xml`/`ossIndexServerId` o variables de entorno seguras, ejecutar `mvn -f backend\pom.xml -DskipTests verify -P security-audit` y `mvn -f desktop\pom.xml -DskipTests verify -P security-audit`, comprobar en logs/reportes que OSS Index no queda deshabilitado, y mantener sin imprimir secretos. Si no hay credenciales, dejar explicitamente la auditoria como NVD/CISA + analizadores locales.

### Sonatype OSS Index habilitado - Dependency-Check (2026-07-11)

- Objetivo: habilitar Sonatype OSS Index/Guide en OWASP Dependency-Check sin versionar secretos.
- Configuracion local fuera de Git:
  - `~/.m2/settings.xml` actualizado con `<server><id>ossindex</id>...</server>`.
  - Backup local creado antes de editar `settings.xml`.
  - No se imprimio ni versiono el PAT; busqueda en repo solo encuentra el id `ossindex`, no el token.
- Cambios versionados:
  - `backend/pom.xml` y `desktop/pom.xml`: `dependency-check-maven` 12.2.2 mantiene NVD y anade `ossIndexServerId=ossindex`.
  - `backend/pom.xml`: `logback.version=1.5.38` para cerrar CVE-2026-13006 detectada por OSS Index en `logback-core:1.5.34`.
  - `backend/owasp-suppressions.xml`: suppression exacta y caducada el 2026-10-01 para `org.springframework.security:spring-security-web:6.5.11` / CVE-2026-47838. Justificacion: la app usa JWT, no X.509/client certificates ni `SubjectDnX509PrincipalExtractor`; Maven Central no tiene 6.5.x posterior a 6.5.11 y 7.x es salto mayor no compatible como cambio quirurgico de Boot 3.5.
  - `desktop/pom.xml`: `gson.version=2.14.0` para cerrar CVE-2025-53864 detectada por OSS Index en `gson:2.10.1`.
  - `desktop/dependency-reduced-pom.xml`: actualizado por Maven para reflejar `ossIndexServerId` y Gson 2.14.0.
- Validacion ejecutada:
  - `mvn -f backend\pom.xml org.owasp:dependency-check-maven:check -DossIndexServerId=ossindex -DskipTests` -> OSS Index usado sin `401`, `BUILD SUCCESS`; antes de correcciones detecto Logback y Spring Security.
  - `mvn -f backend\pom.xml -DskipTests verify -P security-audit` -> `BUILD SUCCESS`; OSS Index activo; reporte final backend `vulnerableDeps=0`.
  - `mvn -f desktop\pom.xml -DskipTests verify -P security-audit` -> `BUILD SUCCESS`; OSS Index activo; reporte final desktop `vulnerableDeps=0`, `suppressed=3` (Kotlin/PDFBox ya documentados).
  - `mvn -f desktop\pom.xml test` -> 14 tests, 0 fallos.
  - `mvn -f backend\pom.xml test` fallo inicialmente por DB de test sucia (92 fallos `409 Email is already registered`); se recreo SOLO `recetas_familiares_test`.
  - Tras reset de test DB: `mvn -f backend\pom.xml test` -> 116 tests, 0 fallos, `BUILD SUCCESS`.
- Seguridad/VibeSec:
  - VibeSec usado como checklist manual por tocar dependencias/security-audit y manejo de credenciales locales.
  - No se pusieron credenciales en POM ni en archivos versionables; `settings.xml` queda local en la maquina del usuario.
  - `security-review` no disponible como herramienta callable; validacion alternativa: Dependency-Check con NVD + OSS Index + revision manual de alcance de CVE X.509.
- Riesgos residuales:
  - La credencial OSS Index ahora vive en `~/.m2/settings.xml`; proteger ese archivo y rotar el PAT si se expone.
  - La suppression Spring Security debe revisarse antes de 2026-10-01 o cuando exista fix compatible con Spring Boot 3.5 / Spring Security 6.5.x.
  - Flyway/PostgreSQL 18 y suppressions Desktop Kotlin/PDFBox quedan como riesgos ya documentados.

### Estado actual para el siguiente sprint - 2026-07-11 (verificacion Claude Code)

Punto exacto en el que queda el proyecto tras la sesion OSS Index de Codex:

- OSS Index: FUNCIONAL y habilitado. Verificacion independiente de Claude Code en esta fecha: `mvn -DskipTests verify -P security-audit` en backend y desktop -> `Finished Sonatype OSS Index Analyzer` + `BUILD SUCCESS`, sin `401`, sin `invalid credentials`, sin `analyzer disabled`. Credenciales validas solo en `~/.m2/settings.xml` (server `ossindex`), fuera de Git.
- PENDIENTE DE COMMIT al escribir esto: los cambios de codigo del sprint OSS Index de Codex estaban aun sin commitear en el arbol (`backend/pom.xml` con `ossIndexServerId` + logback 1.5.38, `desktop/pom.xml` con `ossIndexServerId` + gson 2.14.0, `backend/owasp-suppressions.xml` nuevo con CVE-2026-47838, `desktop/dependency-reduced-pom.xml` regenerado). Quien retome debe comprobar `git status`/`git log`: si esos archivos siguen sucios, cerrar ese commit ANTES de empezar otro sprint (el push disparara CI/CD y deploy: verificar run de Actions `success` y health publico 200 despues).
- Cobertura de auditoria de dependencias vigente: NVD/CISA + OSS Index + analizadores locales. Backend y desktop `vulnerableDeps=0`.
- Siguiente sprint fijado por el usuario (NO autorizado): `Apuntar clientes a produccion (URL de API configurable)`. Alcance completo en la seccion 8, prioridad 1. Recordatorio del problema: Desktop instalado apunta a `http://localhost:8080/` y el APK a `http://10.0.2.2:8080/`; ningun instalable conecta con Hetzner hasta ejecutar ese sprint.
- Despues, en orden: COD-8 (SyncWorker e2e), iOS runtime (bloqueado sin macOS), dominio propio (cuando se compre), UX-14.

### Auditoria completa multiagente + fixes alcance (c) - 2026-07-11

- Objetivo: auditoria destructiva completa (Claude Code lider + contraste Codex y Gemini en solo lectura via bloques IDE), seguida de fixes autorizados por el usuario con alcance (c).
- Skills/seguridad ejecutadas en la sesion: `security-review` (OWASP sistematico sobre backend/infra/clientes) y `VibeSec` (checklist sobre el diff de auth). Codex/Gemini aportaron contraste; todos sus hallazgos nuevos fueron verificados en codigo por Claude Code antes de integrarlos (2 errores de Gemini corregidos: el `.iml` si existia sin trackear, e "imagenes chat" fue lectura erronea suya).
- Veredicto auditoria: madurez 7/10, riesgo seguridad MEDIO, riesgo operativo MEDIO-ALTO hasta cerrar el sprint de clientes->produccion. Informe completo en la conversacion de la sesion.
- Fixes aplicados (alcance c):
  - `AuthService.register`: `saveAndFlush` + catch `DataIntegrityViolationException` -> 409 (carrera de registro duplicado ya no da 500).
  - `RefreshTokenService.rotate` + `RefreshTokenRepository.revokeIfActive`: rotacion atomica (UPDATE condicional `revoked_at IS NULL`); el perdedor de una carrera de refresh recibe 401 y su reemplazo se descarta sin entregarse.
  - `FamilyService.parseRole`: error sin reflejar input (COD-10 cerrado).
  - `application-prod.yml`: `server.address=${SERVER_ADDRESS:127.0.0.1}` (defensa en profundidad, coherente con runbook Caddy loopback).
  - `.github/workflows/dependency-audit.yml` NUEVO: Dependency-Check semanal (lunes 06:00 UTC) + manual, backend y desktop, requiere secret `NVD_API_KEY` en GitHub (CONFIGURAR ANTES del primer run; OSS Index corre anonimo en CI).
  - Documental: CLAUDE.md y CONTINUAR.md ahora dicen PostgreSQL (no MySQL); README corregido (chat implementado, 116 tests, PostgreSQL, nota Desktop Windows-only por DPAPI); borrados `auditoria.txt`, `resultado auditoria.txt` (recuperables en git) y `.iml` local.
  - Hallazgos NUEVO-1/2/4 y URL horneada iOS registrados en seccion 8 para el sprint de clientes.
- Validacion: `mvn compile` backend OK (exit 0). `mvn test`: 116 tests ejecutados, 105 errores TODOS por entorno (sin PostgreSQL de test local: PSQLException/Flyway al cargar contexto), 11 unitarios en verde (`ChatStompAuthChannelInterceptorTest`, `FileStorageServiceTest`). Los tests de las clases tocadas son de integracion: LOS VALIDARA EL CI en el push (job build con Postgres service). NO hacer push sin vigilar ese run.
- Cierre (2026-07-11, misma sesion): commits `4046f38` (fix backend), `3f9b6a9` (ci dependency-audit), `679cd02` (docs) pusheados a `main`. Run `Backend CI/CD` sobre `679cd02`: `Build and test backend` SUCCESS (116 tests contra Postgres en CI, incluidos los de auth modificados) y `Deploy backend` SUCCESS. Health publico verificado: HTTP 200 `{"status":"UP"}` a las 16:24 UTC. Los fixes de auth estan EN PRODUCCION.
- Riesgos residuales:
  - `server.address` loopback por defecto: si algun consumidor accedia al backend por IP WireGuard/no-loopback sin pasar por Caddy, definir `SERVER_ADDRESS` en `backend.env`.
  - Suppression CVE-2026-47838: Codex indica (fuentes NVD/Spring no verificadas offline) que 6.5.11 esta fuera del rango afectado; revisar y retirar la suppression si se confirma.
  - Secret `NVD_API_KEY` PENDIENTE de crear en GitHub (Settings > Secrets > Actions); sin el, el workflow `dependency-audit.yml` fallara en su primer run (lunes 06:00 UTC o manual).

### Estado actual para el siguiente sprint - 2026-07-11 tarde (post-auditoria, fixes desplegados)

Punto exacto del proyecto tras la auditoria multiagente y los fixes de alcance (c):

- `main` = `679cd02`, arbol limpio, todo pusheado y DESPLEGADO en produccion (run CI/CD verde, health 200 UP verificado 16:24 UTC).
- Produccion incluye ahora: registro concurrente→409, rotacion de refresh atomica (single-use real), error de rol sin reflejar input, `server.address` loopback por defecto.
- Workflow nuevo `.github/workflows/dependency-audit.yml`: Dependency-Check semanal (lunes 06:00 UTC) + manual para backend y desktop. ACCION PENDIENTE DEL USUARIO: crear secret `NVD_API_KEY` en GitHub antes del primer run.
- Documentacion sincronizada con la realidad: PostgreSQL en CLAUDE.md/CONTINUAR/README, README con estado real (chat implementado, 116 tests, Desktop Windows-only por DPAPI), raiz sin `auditoria.txt`/`resultado auditoria.txt`.
- SIGUIENTE SPRINT (fijado por el usuario, autorizado para ejecucion por Codex el 2026-07-11): `Apuntar clientes a produccion` — alcance completo en seccion 8, prioridad 1, INCLUYENDO los añadidos de auditoria: URL horneada tambien en iOS (`ios/composeApp/.../network/ApiClient.kt:23`), NUEVO-1 (Desktop sync sin paginar y que descarta `familyNotes`/`recipePhotos`) y NUEVO-2 (limit default server-side tras migrar Desktop).
- Recordatorio operativo: push a `main` con cambios en `backend/**` = deploy automatico a produccion; verificar run de Actions y health tras cada push.
- Despues de ese sprint, en orden: COD-8 (SyncWorker e2e), iOS runtime (bloqueado sin macOS; incluye NUEVO-4 CancellationException), dominio propio (cuando se compre), UX-14.

### Sprint clientes a produccion - ejecucion Codex (2026-07-11)

- Objetivo: clientes instalables apuntan por defecto a produccion y permiten configurar URL de servidor con validacion; Desktop sync deja de descargar delta completo; backend aplica `limit` por defecto si el cliente no lo envia.
- Contexto leido en la sesion: `CONTINUAR.md` secciones 6, 8 y 10 indicadas por el usuario, `CLAUDE.md`, auditoria/estado post-auditoria en esta seccion, y archivos citados del alcance antes de modificarlos. Skill usada: VibeSec-Skill como checklist manual por tocar clientes HTTP/WS, sync y backend.
- Commits de implementacion:
  - `49ed69c feat(desktop): configurar servidor y sync paginado`
  - `7523a70 feat(android): configurar url de servidor`
  - `2492c3c feat(ios): configurar url de servidor`
  - `30381c7 fix(backend): paginar sync sin limit` (commit backend separado; su push disparo CI/CD y deploy).
- Cambios Desktop:
  - `desktop/.../core/ServerConfig.java`: proveedor unico de URL con default `https://recetas.167.233.213.242.sslip.io/`, precedencia de `-Dapi.base.url`, persistencia en `Preferences`, normalizacion y validacion (solo `https`; `http` solo para `localhost`, `127.0.0.1`, `10.0.2.2`; rechazo de espacios, query/fragment, ruta extra y credenciales embebidas).
  - `ApiClient`, `RecipeRepository`, `LoginView`, `MainWindow`, `build-installer.ps1`: eliminan defaults locales duplicados, usan base dinamica, exponen campo de servidor en login/ajustes, y el instalador usa produccion por defecto. WebSocket deriva del mismo origen (`https` -> `wss`).
  - `SyncRepository` Desktop: `limit=200`, tope 50 paginas, no avanza `lastSyncTime` si el pull queda incompleto; acumula/aplica `familyNotes`, `recipePhotos` y `shoppingListItems` de forma explicita. Donde no hay cache local (`recipePhotos`, shopping lists/items), queda no-op documentado.
- Cambios Android:
  - `android/app/build.gradle.kts`: `DEFAULT_API_BASE_URL` pasa a produccion.
  - `ServerUrlStore.kt`, `DynamicBaseUrlInterceptor.kt`, `AppContainer.kt`, interceptores auth/refresh, `ChatRepository.kt`, login y perfil: URL persistida configurable, validacion equivalente a Desktop, reescritura dinamica de Retrofit/OkHttp, y comparacion de origen completo antes de adjuntar Bearer o refrescar tokens. `network_security_config.xml` no se amplio.
- Cambios iOS:
  - `ServerUrlPreference.kt` common + `ServerUrlPreference.ios.kt`: default produccion, validacion comun y persistencia en `NSUserDefaults`.
  - `ApiClient.kt`, `App.kt`, `LoginScreen.kt`, `MainTabScreen.kt`, `SettingsScreen.kt`: base URL inyectable/dinamica, campo de servidor en login/ajustes y comparacion dinamica de origen para Bearer/refresh.
- Cambios backend:
  - `backend/.../sync/SyncService.java`: `pull(..., limit=null)` usa `DEFAULT_PULL_LIMIT=200` y devuelve pagina con `hasMore/nextSince`; clientes antiguos sin `limit` siguen recibiendo contrato paginado.
  - `SyncControllerTest.java`: caso nuevo para pull sin `limit` con mas de 200 recetas.
- Validacion ejecutada:
  - Desktop `mvn test` -> `BUILD SUCCESS`, 20 tests, 0 fallos/errores.
  - Desktop `mvn -DskipTests compile` -> `BUILD SUCCESS`.
  - Desktop `.\build-installer.ps1` -> `BUILD COMPLETADO`; instalador `desktop/output/RecetasFamiliares-Instalador-v1.1.exe` (52.724.168 bytes) y app-image regenerados; config contiene `-Dapi.base.url=https://recetas.167.233.213.242.sslip.io/`.
  - Android `.\gradlew test` -> `BUILD SUCCESS`. Warnings observados: trust store `NUL` no legible, safe call innecesario en `TokenRefreshAuthenticator.kt`, deprecations Compose existentes.
  - Android `.\gradlew assembleDebug` -> `BUILD SUCCESS`; APK `android/app/build/outputs/apk/debug/app-debug.apk` (23.946.786 bytes).
  - iOS `.\gradlew compileKotlinMetadata compileKotlinIosX64 compileKotlinIosArm64 compileKotlinIosSimulatorArm64`: primer intento fallo por mismatch `URLBuilder`/`Url` en `ApiClient.kt`; corregido con overload; segundo intento -> `BUILD SUCCESS`. Warnings: expect/actual beta y avisos preexistentes de Keychain/interop.
  - Backend `mvn test` local -> `BUILD FAILURE` por entorno PostgreSQL/Flyway local, no por compilacion: Surefire 117 tests ejecutados, 0 failures, 106 errors; raiz repetida `Unable to obtain connection from database: The server requested password-based authentication, but no password was provided by plugin null`. Compilacion main/test alcanzo Surefire.
  - `git diff --check HEAD~4..HEAD` -> sin salida.
  - Busqueda de hardcodes cliente `http://localhost:8080` / `http://10.0.2.2:8080` en fuentes cliente -> sin matches fuera de tests/config backend no tocada.
  - Push a `main` hasta `30381c7` -> workflow `Backend CI/CD` run `29160918726`: `Build and test backend` success, `Deploy backend` success. Health publico posterior: HTTP 200 `{"status":"UP","checkedAt":"2026-07-11T17:07:08.697120286Z"}`.
- Pendientes obligatorios antes de marcar cierre funcional:
  - Prueba real Desktop instalado contra Hetzner: login + sync + chat. EJECUTADA 2026-07-11 noche (ver entrada de cierre mas abajo). VALIDADA.
  - Prueba real APK en emulador contra Hetzner: login + sync + chat. EJECUTADA 2026-07-11 noche. VALIDADA.
  - iOS runtime en macOS/dispositivo. Bloqueado en Windows; solo se compilaron targets Kotlin. SIGUE ABIERTO (COD-1/COD-2).
  - Revision final de seguridad por Claude Code/VibeSec-security-review: EJECUTADA (PASS, ver entrada anterior).
- Riesgos residuales:
  - La URL configurable es una frontera de confianza: la validacion bloquea esquemas peligrosos, credenciales embebidas y `http` no-dev, pero no puede distinguir un servidor HTTPS malicioso elegido por el usuario.
  - Al cambiar servidor estando logado, Desktop/Android/iOS fuerzan salida/cierre de sesion para no reutilizar tokens en otro origen; queda pendiente prueba manual de esa UX.
  - Android mantiene WorkManager programado; tras cambio de servidor/cierre de sesion no se valido manualmente el comportamiento de sync periodico sin token.
  - iOS persistencia en `NSUserDefaults` y flujo de logout tras cambio de URL no se validaron en runtime.

Revision final de seguridad Claude Code (2026-07-11, misma fecha, sesion de auditoria):
- Verificado contra codigo real (no solo el informe de Codex): commits presentes en `main` (`49ed69c..7c26714`, arbol limpio), run `29160918726` success (build+deploy) y health 200 UP re-verificados via API.
- Revisados en detalle los puntos criticos de seguridad: `ServerConfig.java` (validacion correcta: allowlist de esquema, sin userinfo/query/fragment/ruta, http solo hosts dev, normalizacion canonica, fallback fail-safe si la preferencia guardada es invalida), `ServerUrlConfig/ServerUrlStore.kt` (reglas equivalentes), `DynamicBaseUrlInterceptor.kt` (reescribe solo peticiones del origen inicial), `TokenRefreshAuthenticator.kt` (compara origen completo scheme+host+port contra el proveedor actual antes de responder credenciales a un 401 y refresca contra la base dinamica), iOS `ApiClient.kt` (Bearer solo al host del API), `SyncService.java` (DEFAULT_PULL_LIMIT=200, modo ilimitado eliminado) y `SyncRepository.java` Desktop (paginado con guarda de cursor estancado, aplica familyNotes/recipePhotos).
- Grep del diff completo del sprint: sin logging nuevo de URLs/tokens; sin secretos.
- Veredicto: PASS sobre lo revisado. Alcance de la revision: los archivos de seguridad critica al 100%; el resto del diff (UI, wiring) por muestreo.
- Riesgo señalado adicional: binarios ANTIGUOS de Desktop (v1.1 previa) contra el backend nuevo ahora reciben pull paginado (200 filas + hasMore que ignoran) en vez de delta completo: sync silenciosamente parcial si una entidad supera 200 filas pendientes. A escala familiar es improbable; se mitiga instalando el instalador regenerado.
- El sprint sigue ABIERTO hasta las pruebas manuales del usuario (Desktop instalado y APK contra Hetzner: login + sync + chat).

### Cierre del sprint clientes a produccion - pruebas reales + fixes + badge chat (2026-07-11 noche, Claude Code)

- Pruebas manuales guiadas con el usuario contra Hetzner (backend real, familia demo `Los Demo` con 2 usuarios, 3 recetas completas, 5 items de stock con caducidades y 3 notas creados via API):
  - Desktop: login OK, URL de produccion por defecto OK, recetas con ingredientes/pasos OK, stock con avisos de caducidad OK, notas OK, chat en tiempo real OK.
  - Android (emulador Pixel 9 Pro, API 36): login OK, sync completo OK, chat cruzado en vivo Desktop<->Android con dos usuarios distintos VALIDADO en ambas direcciones (wss).
  - Cuentas demo en produccion: `demo.familia@recetas.local` (OWNER) y `demo.abuela@recetas.local` (MEMBER), familia `Los Demo`.
- Incidencia Android diagnosticada y resuelta SIN tocar codigo: `CertPathValidatorException: Trust anchor not found` en el emulador. Causa raiz: **Avast intercepta el TLS del equipo** y re-firma con su CA (visible via openssl: emisor `Avast Web/Mail Shield`); Windows/Java confian en esa CA pero el emulador no. Solucion: excepcion en Avast para `recetas.167.233.213.242.sslip.io`. Ese MITM de Avast tambien explica los fallos TLS (exit 35) del curl de git-bash en esta maquina.
- Bugs PREEXISTENTES de Desktop destapados por la primera prueba con datos reales y corregidos (`2d97a15`):
  - 5 DTOs de pagina en `SyncDtos.java` con formato Spring (`content`/`totalElements`/`number`) en vez del contrato `PageResponse` (`items`/`totalItems`/`page`): notas, favoritos, menus, listas de compra e items deserializaban a null y las vistas quedaban vacias sin error. `RecipePageResponse` ya era correcto (por eso recetas funcionaba).
  - `StockRepository.load()` esperaba array crudo -> excepcion en el widget "proximo a caducar" del dashboard; la vista de stock lo enmascaraba con la cache de sync.
  - 3 peticiones con `size=200` frente al `@Max(100)` del backend -> 400 silencioso.
- Funcionalidad nueva pedida por el usuario e implementada: aviso de mensajes de chat no leidos (`1f52ddb` Desktop, `0b6fff3` Android).
  - Desktop: conexion WS viva desde el login; contador `(N)`/`9+` en el boton de la sidebar; se limpia al abrir el chat; se cierra en logout.
  - Android: socket de badge app-scoped mientras hay sesion; `Badge` Material 3 en el icono de chat de la TopAppBar con contentDescription accesible; ids vistos no re-cuentan (ediciones/reconexiones); limpieza al abrir chat; cierre en logout/cambio de servidor.
  - Validado en vivo por el usuario en ambas direcciones: mensaje con chat oculto -> numerito; abrir chat -> se limpia.
  - Limitacion documentada: el aviso requiere app abierta (WebSocket); notificaciones push con app cerrada = fase 4 del chat.
- Seguridad: VibeSec (checklist sobre el diff): sin secretos, sin logging nuevo de datos, el socket reutiliza la autenticacion JWT existente en CONNECT, el badge no expone contenido de mensajes. PASS.
- Validaciones: Desktop `mvn test` SUCCESS; Android `assembleDebug` + `testDebugUnitTest` SUCCESS; instalador Windows regenerado tras los fixes: `desktop/output/RecetasFamiliares-Instalador-v1.1.exe` (50,3 MB, BUILD COMPLETADO, URL de produccion embebida como default); APK con badge en `android/app/build/outputs/apk/debug/app-debug.apk`; prueba visual en vivo de los dos clientes por el usuario: "todo ok". Residual menor: la prueba en vivo se hizo con la app en modo dev (`mvn javafx:run`), mismo codigo que el instalador regenerado; smoke test del exe instalado pendiente si se quiere rigor total.
- Sprint `Apuntar clientes a produccion`: **CERRADO para Desktop y Android**. iOS queda abierto (runtime bloqueado sin macOS, COD-1/COD-2). Riesgos residuales previos vigentes: WorkManager tras cambio de servidor sin validar manualmente; binarios Desktop antiguos ignoran pull paginado.
- Recordatorio: los datos demo viven en produccion; borrarlos cuando dejen de ser utiles (cuentas `*@recetas.local`, familia `Los Demo`). RESUELTO: ver limpieza mas abajo.

### Limpieza de datos demo + estado final de sesion (2026-07-11 noche, Claude Code)

Limpieza demo en produccion:
- Familia `Los Demo` y las 2 cuentas demo ELIMINADAS de la base de produccion via JDBC (WireGuard, transaccion unica, script fail-closed que abortaba si la familia tenia algun miembro no demo; sin ON DELETE CASCADE en el esquema, borrado en orden inverso de dependencias). Filas: 8 mensajes chat + 1 clear, 3 notas, 5 stock, 3 recetas (12 pasos, 17 ingredientes), 9 refresh tokens, 2 memberships, 2 usuarios, 1 familia. Script temporal borrado tras el uso; credenciales solo desde `herztner/recetas_app.env`, nunca impresas.
- Verificado: login demo devuelve 401 y `/health` sigue 200 UP.
- Nota tecnica reutilizable: no hay psql en este PC; el patron que funciona es `java -cp <ruta .m2>/postgresql-42.7.13.jar Script.java` con `DB_URL/DB_USERNAME/DB_PASSWORD` de entorno (quitar CRLF de los .env de Windows).

PUNTO EXACTO DEL PROYECTO (para retomar en la proxima sesion, cualquier agente):
- `main` limpio y pusheado; produccion desplegada, verde y validada en vivo. Ultimo estado de commits en `git log`.
- La aplicacion ES un producto funcionando: Desktop y Android instalables conectan a `https://recetas.167.233.213.242.sslip.io/` por defecto, con URL configurable, sync completo, chat en tiempo real entre usuarios y aviso de no leidos.
- Artefactos regenerados y vigentes: `desktop/output/RecetasFamiliares-Instalador-v1.1.exe` (50,3 MB) y `android/app/build/outputs/apk/debug/app-debug.apk` (con badge).
- Pendientes operativos del usuario: crear secret `NVD_API_KEY` en GitHub (workflow `dependency-audit.yml`, primer run lunes 06:00 UTC); instalar el .exe regenerado si quiere la copia final en el sistema.
- Deuda/bloqueos vigentes: iOS runtime (COD-1/COD-2, sin macOS); chat fase 4 (video + push notifications; el aviso de no leidos actual requiere app abierta); dominio propio aplazado; WorkManager tras cambio de servidor sin validacion manual; recuperacion de password/verificacion email/borrado de cuenta (CRIT-2/IMP-6 de la auditoria 2026-07-11) con backend API iniciado por Codex en sprint parcial posterior; faltan SMTP/secret y UX cliente para cierre de producto.

SIGUIENTE SPRINT (fijado, NO autorizado aun): **COD-8 siguiente capa — tests e2e de sincronizacion offline Android**
- Alcance (CONTINUAR §8, prioridad 2): `SyncWorker` y colas offline end-to-end con Room fake o DB in-memory; cubrir pull paginado con tope, push con `baseSyncVersion` (convencion COD-3: dirty = syncVersion negativo), tombstones, conflictos 409 (server gana tras pull) y reintentos de WorkManager. Desktop: tests adicionales solo si aportan valor sin fragilizar.
- Candidato añadido por la sesion de hoy: incluir la validacion manual pendiente de WorkManager tras cambio de servidor/logout (riesgo residual del sprint de clientes).
- Alternativa si el usuario prefiere valor de producto antes que tests: CRIT-2 de la auditoria (reset de password + verificacion de email), que es el mayor hueco para usuarios reales.
- Apoyo IA recomendado: Codex como ejecutor tecnico (tests, build, WorkManager); no requiere Gemini salvo que se toque UX.

### Sprint COD-8 sync offline Android e2e - ejecucion Codex (2026-07-11)

- Objetivo: ampliar COD-8 con tests e2e/fakes de `SyncRepository`/`SyncWorkerRunner` y colas offline Android, sin tocar backend ni contratos API.
- Contexto leido en la sesion: `CONTINUAR.md` secciones 7 Android, 8 prioridad 2 y 10 entradas 2026-07-11; `CLAUDE.md`; archivos Android implicados (`Repositories.kt`, `SyncWorker.kt`, DAOs/entidades, DTOs y tests existentes). Skill usada: VibeSec-Skill como checklist manual por tocar sync offline/soft delete/datos familiares. `security-review` final queda para Claude Code segun el reparto indicado por el usuario.
- Bug real encontrado y corregido en commit separado:
  - `cf8abed fix(android): resolver conflicto sync 409 con pull`
  - Causa: `SyncRepository.pushThenPull()` propagaba `HttpException 409` de `pushSync`; WorkManager terminaba en retry y no ejecutaba pull, asi que el servidor no podia ganar el conflicto de lote completo.
  - Fix: en 409 de push, ejecutar `pullOnce(protectPending=false)` para traer la version canonical del servidor y sobrescribir los pendientes locales conflictivos. `CancellationException` sigue relanzandose.
- Tests/fakes anadidos:
  - `4a42fb6 test(android): cubrir sync offline e2e`
  - Nuevo `SyncRepositoryE2eTest` con fake stateful de DAOs sobre mapas en memoria y API mockeada. Cubre: pull paginado `limit=200`, tope 50 paginas sin avanzar cursor, colas offline COD-3 (`syncVersion=0`, negativos con base), tombstones, borrado local de entidades creadas offline, 409 con servidor ganador tras pull, `CancellationException` relanzada y worker tras logout/cambio de servidor (`familyId=null`) sin push/pull ni crash.
- Validacion ejecutada:
  - Primer intento dirigido `.\gradlew testDebugUnitTest --tests "org.gipsybuho.recetasfamiliares.data.repository.SyncRepositoryE2eTest"` -> `BUILD FAILED`, 6 tests, 1 fallo esperado: el test de 409 recibia `retrofit2.HttpException` porque produccion no hacia pull. Este fallo justifico el bugfix de produccion separado.
  - Tras el fix: el mismo comando dirigido -> `BUILD SUCCESSFUL`.
  - Android `.\gradlew test` -> `BUILD SUCCESSFUL`; reportes `testDebugUnitTest`: 37 tests, 0 failures, 0 errors, 0 skipped.
  - Android `.\gradlew assembleDebug` -> `BUILD SUCCESSFUL`; APK regenerado `android/app/build/outputs/apk/debug/app-debug.apk` (23.947.973 bytes).
  - `git diff --check` -> sin errores; solo avisos LF/CRLF de Windows.
- Riesgos residuales:
  - Los tests usan fakes stateful de DAOs, no `Room.inMemoryDatabaseBuilder`; no se anadieron Robolectric/room-testing para evitar dependencias de test nuevas. Cubren la logica del repositorio real, pero no validan SQL generado ni scheduler real de WorkManager.
  - La validacion de WorkManager tras logout/cambio de servidor es de runner/repositorio (`familyId=null` -> success sin llamadas API), no prueba instrumentada del scheduler Android.
  - No se tocaron Desktop/iOS/backend en este sprint. iOS runtime y tests UI automatizados siguen como deuda.

Revision final Claude Code (2026-07-11, misma fecha):
- Verificado contra codigo real: commits `cf8abed`/`4a42fb6`/`5a88cce` en `main`, arbol limpio, **0 archivos backend tocados** (sin deploy; confirmado que no hubo run nuevo de Actions).
- Diff del fix 409 revisado linea a linea: semantica correcta y conservadora. `pullOnce(protectPending=false)` solo sobrescribe pendientes cuya fila cambio en el servidor desde `lastSyncTime` (el pull es incremental), que son exactamente las conflictivas; los pendientes sin conflicto no vienen en el delta, sobreviven y se re-empujan en el siguiente ciclo. `CancellationException` se relanza ANTES del catch de `HttpException`. Coherente con el contrato documentado (409 de lote completo, servidor gana tras pull).
- Suite re-ejecutada de primera mano por Claude Code: `gradlew test` -> EXIT 0, **37 tests, 0 failures, 0 errors** (conteo desde los XML de test-results; coincide con lo reportado por Codex).
- Seguridad (VibeSec checklist sobre el diff): sin secretos, sin logging nuevo, sin cambio de superficie de red ni de ownership; el overwrite de conflicto queda acotado al `familyId` de la sesion. PASS.
- **Sprint COD-8 (capa SyncWorker/colas offline Android): CERRADO.** Riesgos residuales: los listados por Codex (fakes en vez de Room real; sin prueba instrumentada del scheduler). COD-8 global sigue parcial: faltan tests UI/instrumentados e iOS.

### Hotfix CI Dependency Audit Desktop - 2026-07-11 noche (Codex)

- Contexto: el workflow `Dependency Audit` ya tenia `NVD_API_KEY` configurado en GitHub, pero el job `dependency-check desktop` fallaba antes de ejecutar OWASP al resolver plugins Maven con `java.security.NoSuchAlgorithmException: Error constructing implementation (algorithm: Default, provider: SunJSSE, class: sun.security.ssl.SSLContextImpl$DefaultSSLContext)`.
- Causa raiz: `desktop/.mvn/jvm.config` estaba versionado con `-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NUL`. Ese ajuste ayuda a Maven en algunos entornos Windows, pero lo lee tambien `mvn -f desktop/pom.xml` en Ubuntu GitHub Actions y rompe el `DefaultSSLContext` antes de descargar dependencias.
- Cambio aplicado: eliminado `desktop/.mvn/jvm.config` del repo. El instalador/runtime Windows conserva sus opciones especificas en `desktop/build-installer.ps1`; solo se quita la configuracion global de arranque de Maven versionada.
- Validacion ejecutada en Windows tras retirar el archivo:
  - `mvn -B -f desktop/pom.xml -DskipTests compile` -> `BUILD SUCCESS`.
  - `mvn -B -f desktop/pom.xml -P security-audit -DskipTests verify` -> `BUILD SUCCESS`; Dependency-Check 12.2.2 ejecuto NVD y Sonatype OSS Index y genero reportes HTML/JSON.
  - `git diff --check --cached` -> sin errores.
- Seguridad: VibeSec-Skill usado como checklist por tratarse del pipeline de auditoria de dependencias; sin secretos versionados, sin logging nuevo, sin cambios de auth/ownership/API/backend.
- Impacto operativo: no se tocaron `backend/**`, `infra/backend/**`, `scripts/backend/**` ni `.github/workflows/backend-ci-cd.yml`; no deberia disparar deploy automatico de backend. Si un entorno Windows local vuelve a necesitar `Windows-ROOT`, configurarlo fuera del repo via `MAVEN_OPTS` o perfil local de IDE.
- Verificacion GitHub tras push: workflow manual `Dependency Audit` run `29165213615` sobre `636b557` -> `success`; jobs `Backend dependency audit` y `Desktop dependency audit` completados con `success`. Revalidacion posterior sobre `main` actual `66f7377`: run `29165644055` -> `success`; `Desktop dependency audit` success y `Backend dependency audit` success.

### Sprint CRIT-2 backend auth lifecycle - ejecucion Codex (2026-07-11 noche)

- Objetivo parcial autorizado por `continua`: iniciar CRIT-2/IMP-6 por backend para recuperar password, verificar email y permitir borrado/anomizacion de cuenta sin SQL manual. Alcance real de esta entrada: backend/API/migracion/tests; no se implemento UX cliente en Desktop/Android/iOS.
- Contexto leido en la sesion: `CONTINUAR.md` secciones de estado/prioridades/trazabilidad 2026-07-11, `CLAUDE.md`, `auditoria.md` en lo relativo a CRIT-2/IMP-6, y codigo backend de auth/security/users/families/OpenAPI. Skill usada: VibeSec-Skill como checklist por tocar auth, tokens y ciclo de cuenta. Sin Gemini/subagentes: no autorizados explicitamente para esta ejecucion.
- Cambios backend:
  - Nuevos endpoints: `POST /api/v1/auth/password-reset/request`, `POST /api/v1/auth/password-reset/confirm`, `POST /api/v1/auth/email-verification/request`, `POST /api/v1/auth/email-verification/confirm`, `DELETE /api/v1/auth/account`.
  - Tokens de accion de cuenta en tabla nueva `account_action_tokens`: token raw aleatorio de 64 bytes, persistencia solo de hash SHA-256 Base64URL, TTL configurable, invalidacion de tokens activos previos del mismo tipo, consumo con lock pesimista y purga programada.
  - Reset password anti-enumeracion: request responde aceptado aunque el email no exista; si existe y `MAIL_ENABLED=true`, envia email. Confirm cambia hash BCrypt existente y revoca todos los refresh tokens del usuario.
  - Verificacion email: `users.email_verified` y `email_verified_at`; registro/request generan email si mail esta activo; confirm consume token y marca verificado. Login sigue permitido aunque no verificado para compatibilidad.
  - Borrado de cuenta autenticado: exige password actual, revoca refresh tokens, anonimiza email/nombre/avatar/password, soft-delete de membership; si borra al unico miembro soft-delete de familia, y si borra al owner promueve ADMIN mas antiguo o miembro mas antiguo.
  - Mail: `spring-boot-starter-mail` y config por entorno (`MAIL_ENABLED`, `SMTP_*`, `MAIL_FROM`, `APP_PUBLIC_URL`). Con `MAIL_ENABLED=false` no requiere `JavaMailSender`; con `MAIL_ENABLED=true` falla al arrancar si no hay `SMTP_HOST`/sender.
  - Seguridad/OpenAPI/rate-limit: solo endpoints publicos de reset/verificacion quedan permitidos sin bearer; `DELETE /auth/account` queda autenticado y rate-limited; JWT de usuario ya borrado limpia contexto en vez de romper filtro.
- Validacion ejecutada en Windows:
  - `mvn -B -f backend/pom.xml -Dtest=AuthServiceTest test` -> `BUILD SUCCESS`; 7 tests, 0 failures, 0 errors.
  - `mvn -B -f backend/pom.xml -DskipTests compile` -> `BUILD SUCCESS`.
  - `mvn -B -f backend/pom.xml -P security-audit -DskipTests verify` -> `BUILD SUCCESS`; Dependency-Check 12.2.2 ejecuto NVD/Sonatype y genero reportes HTML/JSON.
  - `mvn -B -f backend/pom.xml test` -> `BUILD FAILURE` por entorno local: Flyway no obtiene conexion PostgreSQL porque el servidor pide password y no se proporciono (`The server requested password-based authentication, but no password was provided`). No se marca como suite verde; gate real queda en GitHub Actions con DB de CI.
  - `git diff --check` -> sin errores; solo avisos LF/CRLF de Windows.
- CI/deploy:
  - Commit backend `0cdeb47` fue pusheado a `main`; job `Build and test backend` de GitHub Actions paso verde.
  - El deploy de ese run desplego en servidor (`deployed 20260711T203147Z-0cdeb47aaa6d`) y health externo desde Windows devolvio 200/`UP`, pero el job quedo rojo dos veces por timeout DNS del runner resolviendo `sslip.io` en el health check (`curl: (28) Resolving timed out`).
  - Hotfix `a7296e5 fix(ci): estabilizar health check backend sslip`: para hosts `*.sslip.io`, el health check usa `curl --resolve host:puerto:ip` extrayendo la IP del hostname y mantiene TLS/SNI. Tambien anade connect-timeout y retry corto.
  - Verificacion tras hotfix: `Backend CI/CD` run `29167339404` sobre `a7296e5` -> `success`; jobs `Build and test backend` y `Deploy backend` success; health externo `https://recetas.167.233.213.242.sslip.io/api/v1/health` -> 200 `UP` (`checkedAt` 2026-07-11T20:39:22Z).
- Pendiente operativo antes de cerrar CRIT-2:
  - Configurar SMTP real y secrets/env en produccion (`MAIL_ENABLED=true`, `SMTP_HOST`, credenciales, `MAIL_FROM`, `APP_PUBLIC_URL`).
  - Implementar pantallas/flujo cliente: pedir reset, introducir token/nueva password, reenviar/verificar email, borrar cuenta desde perfil con confirmacion.
  - Decidir politica de producto: si `email_verified=false` solo se informa o si en un sprint futuro se restringen acciones.
- Riesgos residuales:
  - Los enlaces de email apuntan a rutas cliente (`/reset-password`, `/verify-email`) que aun no existen como experiencia web/app; API si existe.
  - Si SMTP falla durante envio, se registra warning sin token ni datos sensibles, pero el usuario no recibe el correo; requiere monitorizacion/config operativa.
  - No hay test de integracion completo por DB local ausente; CI tras push debe decidir el gate.
  - CRIT-2 queda **parcial**, no cerrado de producto.

### Sprint A - CRIT-2 UX cliente (2026-07-12, Claude Code)

- Objetivo: cerrar la parte cliente de CRIT-2 — recuperar contraseña, verificar email y borrar cuenta desde Desktop y Android contra los endpoints backend ya desplegados. Autorizado por el usuario junto con el roadmap funcional de `paraImplementar.txt` (ver seccion 8).
- Contexto leido en la sesion: `CONTINUAR.md` (estado, prioridades, trazabilidad CRIT-2 backend), `CLAUDE.md`, contrato backend real (`AuthController`, DTOs de request, `AuthRateLimitFilter`, `AuthService`) y codigo cliente implicado (LoginView/ProfileView/ApiClient Desktop; RecetasApp/ProfileScreen/RecetasViewModel/Repositories Android).
- Cambios Desktop: `AuthDtos`/`UserDtos` (+`emailVerified`), `ApiClient.deleteWithBody`, `AuthRepository` (5 metodos de cuenta), `PasswordResetDialog` nuevo (2 pasos, anti-enumeracion en el copy), enlace "¿Has olvidado tu contraseña?" en `LoginView` (solo modo login), card "Cuenta" en `ProfileView` (estado verificacion via `/users/me`, enviar/confirmar codigo, eliminar cuenta con password y aviso irreversible), `MainWindow` pasa `showLogin` como callback de cuenta borrada.
- Cambios Android: DTOs nuevos + `emailVerified` en `UserResponseDto`, endpoints en `RecetasApi` (DELETE con body via `@HTTP(hasBody=true)`), metodos en `AuthRepository`, `UserRepository.me()`, `RecetasViewModel` (estado `emailVerified`, request/confirm reset, request/confirm verificacion, `deleteAccount` con logout al exito), `ForgotPasswordDialog` en login y seccion "Cuenta" + dialogos en `ProfileScreen`.
- Fix de seguridad/UX detectado por la revision (VibeSec) y corregido en backend: `deleteAccount` con password incorrecta devolvia 401; el authenticator OkHttp de Desktop/Android reaccionaba al 401 con refresh+retry y terminaba limpiando la sesion local del usuario. Ahora devuelve 403 (bearer valido, accion rechazada); test `deleteAccountRejectsWrongPassword` refuerza el status 403. Clientes mapean 403 -> "Contraseña incorrecta".
- Seguridad ejecutada en la sesion: VibeSec y security-review invocados sobre el diff. Verificado: rate limit cubre los 5 endpoints nuevos (POST y DELETE), sin secretos ni tokens en logs/UI, campos de password enmascarados, tokens solo en body HTTPS (nunca en URL), copy anti-enumeracion ("si el correo existe..."), minimo 12 caracteres alineado con backend. Sin hallazgos de alta confianza.
- Validacion ejecutada: backend `mvn -Dtest=AuthServiceTest test` 7 tests 0 fallos (suite completa requiere DB; gate real en GitHub Actions tras push); Desktop `mvn test` 20 tests 0 fallos; Android `gradlew test` 37 tests 0 fallos + `assembleDebug` OK (APK regenerado).
- Agentes IA: sin Codex/Gemini en esta sesion (cambios cliente siguiendo contrato backend ya revisado por Codex; el usuario autorizo proceder directo con el sprint).
- PENDIENTE OPERATIVO para cerrar CRIT-2 del todo (requiere accion del usuario):
  - SMTP real en produccion: definir proveedor y setear `MAIL_ENABLED=true`, `SMTP_HOST`, `SMTP_PORT`, credenciales, `MAIL_FROM`, `APP_PUBLIC_URL` en el entorno del VPS. Sin esto los correos no salen y los flujos cliente no pueden probarse end-to-end.
  - Prueba manual end-to-end de los 3 flujos con SMTP activo (Desktop y Android).
- Riesgos residuales: flujos cliente validados por compilacion y tests, sin prueba manual E2E (bloqueada por SMTP); los emails del backend enlazan rutas web (`/reset-password`) que no existen — los clientes piden pegar el codigo, valido pero mejorable cuando haya web o deep links; iOS sin estos flujos (runtime bloqueado, COD-1/COD-2); instalador Windows no regenerado en esta sesion (el .exe vigente no incluye estas pantallas).

### Sprint B - Quick wins del roadmap (2026-07-12, Claude Code)

- Objetivo: puntos 1, 2, 8-simple, 9-Desktop, 15, 19 y 21 del roadmap funcional aprobado. Punto 10 (creador de receta) APLAZADO a sprint propio: exige campo nuevo en modelo sincronizado (migracion backend + Room + DTOs sync en 3 clientes), incompatible con "quick win" segun regla §3.
- Verificaciones del roadmap: (7) fotos de receta + portada ya existian en Android y Desktop — nada que implementar (backlog menor: thumbnail en cards de listado); (9) estrellas 1-5 ya completas en Android; faltaba Desktop y se implemento en este sprint.
- Backend:
  - (21) `StarterRecipeSeeder`: 5 recetas conocidas (tortilla de patatas y gazpacho EASY; carbonara y lentejas MEDIUM; paella HARD) con ingredientes y pasos, sembradas al registrar familia. Flag `app.starter-recipes.enabled` (default true; false en `application-test.yml` para no romper asserts de familias vacias).
  - (15) Imagen de grupo: migracion `V17__add_avatar_url_to_families.sql` (aditiva nullable), `FamilyEntity.avatarUrl`, `FamilyResponse.avatarUrl`, `POST /families/{id}/avatar` (multipart, solo OWNER/ADMIN, mismo `FileStorageService` endurecido), serving `/uploads/family_avatars/**` en `UploadController` con ownership por familia y 404 fail-closed.
- Desktop: boton "Salir" en sidebar (cierra app conservando sesion); "🌐 Buscar en la web" en detalle de receta (URLEncoder + navegador); seccion Valoraciones completa en `RecipeDetailView` (media, editor propio con 5 estrellas clicables + comentario, crear/actualizar/eliminar, lista de las de otros; DTOs y 4 metodos nuevos en `RecipeRepository`); avatar de familia en `ProfileView` (render autenticado + "Cambiar imagen del grupo" solo admin).
- Android: boton actualizar en cabecera de Notas (ademas del pull-to-refresh existente); "Salir de la aplicacion" en perfil; "Buscar en la web" en menu del detalle de receta; seccion Miembros en perfil (endpoint `GET /members` añadido a `RecetasApi` + `FamilyMemberDto`); seccion Familia con imagen de grupo (subida solo admin, compresion compartida `compressAvatarImage`); perfil ahora scrolleable (`verticalScroll`).
- Seguridad (checklist VibeSec/security-review aplicado al diff en la sesion): upload familia solo OWNER/ADMIN validado en backend; serving con allowlist de nombre UUID y ownership; avatarUrl de familia solo lo escribe el backend (no acepta URLs de cliente); seeder son constantes; busqueda web solo expone el titulo de la receta elegido por el usuario. Sin hallazgos de alta confianza.
- Validacion ejecutada: backend `AuthServiceTest` 7/0 + compile OK (suite completa requiere DB; gate en CI tras push); Desktop `mvn test` 20/0; Android `gradlew test` + `assembleDebug` OK.
- Riesgos residuales: sin prueba manual de UI en esta sesion (flujos validados por compilacion/tests); tests de integracion backend (FamilyController/RecipeController) corren en CI con `starter-recipes.enabled=false` — el seeder en si no tiene test de integracion propio; iOS sin estos cambios (runtime bloqueado); instalador Windows y APK de release no regenerados.

### Sprint C - ejecucion Codex (2026-07-12)

- Objetivo: gestion familiar, puntos (3) editar miembros y (5) crear familia solo owner/admin segun roadmap §8. Alcance aplicado: no inventar endpoint de crear familias adicionales y no tocar sync/modelos sincronizados.
- Contexto leido en la sesion: `CONTINUAR.md` (seccion "Cierre de sesion 2026-07-12 — PUNTO EXACTO" y §8), `CLAUDE.md`, VibeSec-Skill, backend `families/` (`FamilyService`, `FamilyController`, `FamilyMemberRepository`) y codigo cliente implicado (`FamilyMembersView`, `ProfileView`, `ProfileScreen`, `RecetasApi`, `Repositories.kt`, `RecetasViewModel`).
- Backend/producto: sin cambios de codigo backend. Verificado que `PUT /api/v1/families/{id}/members/{userId}/role` y `DELETE /api/v1/families/{id}/members/{userId}` ya aplican autorizacion OWNER/ADMIN en backend, bloquean tocar al OWNER, auto-cambio y auto-expulsion, y revocan refresh tokens al expulsar. Para (5), no existe endpoint de crear familia adicional; la unica via funcional de crear familia es `AuthService.register`, que crea la primera familia del usuario y lo registra como OWNER. No se detecto via actual por la que un MEMBER pueda crear o derivar familias.
- Desktop: revisado `FamilyMembersView`; ya cubre cambiar rol y expulsar con confirmacion, visible solo para admins/owner segun sesion y con botones bloqueados para self/OWNER. `ProfileView` no requirio cambios.
- Android: añadidos DTO/metodos Retrofit para cambiar rol y expulsar miembro; `FamilyMemberRepository` y `RecetasViewModel` exponen acciones con mensajes genericos; `ProfileScreen` añade menu de gestion por miembro dentro de la seccion Miembros para OWNER/ADMIN, oculta acciones sobre self/OWNER, confirma la expulsion y ejecuta haptico si la preferencia lo permite.
- Seguridad: VibeSec-Skill usado como checklist sobre roles/ownership. Verificado que la autorizacion real vive en backend; la UI solo oculta acciones. Los errores cliente son genericos y no distinguen existencia/permisos; no se introducen secretos, tokens, logs sensibles, cambios CORS/JWT ni almacenamiento nuevo. `/security-review` no estuvo disponible como herramienta callable (`tool_search` no encontro herramienta); alternativa aplicada: revision manual del diff contra VibeSec + `git diff --check`.
- Validacion ejecutada en Windows: Android `.\gradlew test` -> `BUILD SUCCESS`; Android `.\gradlew assembleDebug` -> `BUILD SUCCESS`; Desktop `mvn -f desktop/pom.xml test` -> `BUILD SUCCESS`, 20 tests, 0 fallos; Desktop `mvn -f desktop/pom.xml -DskipTests compile` -> `BUILD SUCCESS`; `git diff --check` -> sin errores (solo avisos LF/CRLF de Windows). Backend suite no ejecutada porque no hubo cambios de codigo backend.
- Riesgos residuales: sin prueba manual de UI en emulador/JavaFX en esta sesion; Android compilo con warnings preexistentes/no bloqueantes (`menuAnchor` deprecado en `InviteMemberDialog` y condicion siempre verdadera en `RecetasViewModel`); iOS sigue sin paridad de esta gestion familiar; la restriccion plena de "crear familias adicionales solo owner/admin" queda para el diseno multi-familia de Sprint D.

### Sprint D backend/API/security - ejecucion Codex (2026-07-12)

- Alcance delegado por el usuario: backend/API/security de multi-familia, puntos (4)+(13)+(12), sin tocar clientes. No se cambia `sync/pull`, `sync/push` ni modelos sincronizados.
- Decision de API: `GET /api/v1/families` ya lista las familias activas del usuario y el cambio de familia activa queda como seleccion cliente del `familyId` usado en rutas existentes. Nuevo `POST /api/v1/families` crea una familia adicional; solo lo puede usar un usuario autenticado sin familia activa o con al menos una membresia OWNER/ADMIN. La familia nueva nace con el usuario como OWNER y ejecuta `StarterRecipeSeeder`.
- Decision de copia: nuevo `POST /api/v1/families/{sourceFamilyId}/recipes/{recipeId}/copy` con body `{ "targetFamilyId": "..." }`. Requiere membresia activa en origen y rol OWNER/ADMIN en destino; bloquea copiar dentro de la misma familia. Copia cabecera de receta, ingredientes, pasos y fotos no borradas. No copia favoritos, valoraciones, notas ni menus porque son estado contextual de la familia destino.
- Migraciones: no se anadio migracion de esquema; las tablas actuales (`families`, `family_members`, `recipes`, contenidos y fotos) ya soportan multiples familias por usuario y copias como filas nuevas.
- Seguridad: VibeSec-Skill usado como checklist por tocar ownership, roles y datos familiares. La autorizacion real vive en backend; las consultas filtran por `familyId`; la copia reutiliza fotos solo tras autorizacion explicita origen/destino, y `UploadController` sigue autorizando por familias propietarias del `storagePath`. Errores por permisos usan respuestas genericas existentes (`request_error`) sin stacks ni secretos. `/security-review` no estuvo disponible como herramienta callable en Codex; alternativa aplicada: revision manual VibeSec sobre el diff.
- Validacion ejecutada en Windows: `mvn -B -f backend/pom.xml "-Dtest=FamilyServiceTest,RecipeServiceTest" test` -> `BUILD SUCCESS`, 6 tests, 0 fallos; `mvn -B -f backend/pom.xml -DskipTests compile` -> `BUILD SUCCESS`; `mvn -B -f backend/pom.xml test` -> `BUILD FAILURE` por entorno local, Flyway no obtiene conexion PostgreSQL porque el servidor pide password y no hay `DB_TEST_PASSWORD` (`The server requested password-based authentication, but no password was provided`). Los tests de controlador anadidos quedan como gate de CI/DB test.
- Riesgos residuales: clientes aun no consumen los endpoints nuevos; login sigue devolviendo una familia primaria por compatibilidad y los clientes deben listar/seleccionar familia. Android/Desktop/iOS requieren trabajo separado de UI/cache activa por familia antes de cerrar Sprint D de producto. Worktree local contiene cambios Android/Desktop ajenos a esta entrada y no se tocaron desde esta tarea backend.

### Sprint D cliente multi-familia — auditoria + ejecucion (2026-07-12, Claude Code + Codex + Gemini)

- Contexto: el worktree contenia ~1530 lineas sin commitear de Sprint D (backend Codex + clientes a medias). Auditoria autorizada por el usuario (alcance a+b), luego ejecucion completa autorizada via opciones.
- Hallazgos de auditoria (Claude, verificados en codigo; Codex y Gemini como segunda opinion copy-paste, ambos integrados):
  - CRITICO C1: Android no compilaba — la UI llamaba a `createFamily`/`copyRecipeToFamily` sin capa API/repo/viewmodel (RecetasApi/ApiDtos solo tenian cambio de EOL). CORREGIDO: DTOs + endpoints Retrofit + `FamilyMemberRepository.createFamily` + `RecipeRepository.copyToFamily` + viewmodel.
  - ALTA A1/A2 (A2 por Codex): carrera de cursor sync — `lastSyncTime` resolvia la familia activa al escribir, y el 409 de push relanzaba `pullOnce(protectPending=false)` sobre la familia nueva. CORREGIDO: `SessionStore.lastSyncTimeFor/setLastSyncTime(familyId,...)` y `pullOnce(familyId, protectPending)` con familia capturada; test E2E nuevo "push 409 tras cambio de familia".
  - MEDIA M1: `POST /api/v1/families` sin tope. CORREGIDO: `MAX_ACTIVE_MEMBERSHIPS=10` (400 Family limit reached) + test.
  - MEDIA M2 (Codex+Gemini): Desktop reimplementaba la copia cliente-side sin fotos y con mensaje contradictorio. CORREGIDO: Desktop usa `POST /copy` atomico; mensajes unificados "la receta y sus fotos se copiaran" en Desktop y Android.
  - MEDIA M4/M5 (Codex): respuestas tardias pintaban miembros/stats/ratings/`_totalPages` de la familia anterior. CORREGIDO: guard de familia activa antes de asignar estado.
  - MEDIA M6 (Codex): iOS aplicaba paginas de pull sin validar `dto.familyId`. CORREGIDO: filtro defensivo en `applyPage`; query muerta `selectAllIngredients` eliminada.
  - BAJA B1: `RecipeDetail` llamaba `loadFamilyInfo()` (red + posible auto-cambio de familia) en cada apertura. CORREGIDO: eliminado; `families` se carga en login y perfil.
- Decisiones de producto del usuario (2026-07-12):
  - Fotos al copiar receta: SI se copian (referencia compartida de `storagePath`; `UploadController` ya autoriza por lista de familias propietarias y el borrado de fotos es soft delete sin borrar el fisico).
  - Cualquier MEMBER del origen puede copiar (equivale a reescribirla a mano); fijado por `RecipeServiceTest` (origen solo exige membresia).
  - Cambio de familia en Desktop: directo sin dialogo de confirmacion, aviso en barra de estado (dialogo eliminado).
- UX Gemini aplicado: titulo "Cambiar familia activa", label "Nombre de la nueva familia", nota informativa en el bottom sheet de copia Android.
- Validado en esta sesion (Windows): Android `gradlew test assembleDebug` BUILD SUCCESSFUL (warnings preexistentes: `menuAnchor`, tooltips deprecados, condicion siempre true); Desktop `mvn test` 21/0; backend `-Dtest=FamilyServiceTest,RecipeServiceTest` 8/0; iOS `compileKotlinIosSimulatorArm64` BUILD SUCCESSFUL (warnings Keychain preexistentes). VibeSec invocado sobre el diff.
- Riesgo residual: suite backend completa NO ejecutada en local (Flyway exige `DB_TEST_PASSWORD`; los `*ControllerTest` de multi-familia quedan como gate de CI). Sin prueba manual UI con dos familias reales. Limpieza de huerfanos de fotos (futura) debera contar filas activas de TODAS las familias por `storagePath` antes de borrar el fisico. Logout Android sigue sin vaciar Room (residuo local sin exposicion en UI, preexistente).

### Cierre de sesion 2026-07-12 (Claude Code) — PUNTO EXACTO DEL PROYECTO

Estado para retomar en la proxima sesion, cualquier agente:

- Punto de arranque confirmado por Codex para Sprint C: `main` en `181e528`; produccion venia desplegada y verde segun contexto del usuario. En Sprint C no se toco backend ni se hizo push de deploy en esta documentacion.
- Ejecutado en esta sesion (2026-07-11 noche a 2026-07-12):
  1. **Sprint A CRIT-2 UX cliente** (commits `389dc43`, `f6a9231`, `e1644c9`): reset password, verificar email y borrar cuenta en Desktop y Android; fix backend 401→403 en `DELETE /auth/account` (el 401 hacia que el authenticator OkHttp limpiara la sesion local).
  2. **Sprint B quick wins** (commits hasta `4d1c6b9`): refresh notas Android, boton salir ambos, buscar receta en web, valoraciones Desktop, miembros en perfil Android, imagen de grupo familiar (V17 + endpoint + serving con ownership), StarterRecipeSeeder (5 recetas al registrar familia).
  3. Roadmap funcional de 21 puntos documentado en §8 con decision del usuario: (16) y (8-integrado) DESCARTADOS; (10) aplazado a sprint propio por contrato sync.
  4. **Sprint C gestion familiar** (ejecucion Codex): Android permite cambiar rol/expulsar miembros desde Perfil; Desktop revisado y ya cubria ambas acciones; punto (5) documentado como cubierto de facto hasta multi-familia.
- Validaciones de la sesion Codex Sprint C: Android `gradlew test` + `assembleDebug` OK; Desktop `mvn test` 20/0 + `-DskipTests compile` OK; backend no ejecutado porque no hubo cambios de codigo backend; `git diff --check` sin errores.
- Limitacion conocida: sin `gh` CLI en este PC no se verificaron los runs de Actions; confirmar en GitHub → Actions que los 2 ultimos runs de `Backend CI/CD` (post `389dc43` y post `4d1c6b9`) estan verdes. Si el segundo fallara en tests de integracion, sospechar de `FamilyControllerTest`/seeder pese a `starter-recipes.enabled=false` en `application-test.yml`.

PENDIENTES OPERATIVOS DEL USUARIO (bloquean cierres, no el desarrollo):
- SMTP real en el VPS para cerrar CRIT-2: `MAIL_ENABLED=true`, `SMTP_HOST/PORT`, credenciales, `MAIL_FROM`, `APP_PUBLIC_URL` en `/etc/recetas-familiares/backend.env`; despues prueba E2E de reset/verificacion/borrado desde ambos clientes.
- Regenerar instalador Windows (`desktop/build-installer.ps1`) y distribuir APK nuevo: los binarios vigentes NO incluyen las pantallas de Sprint A/B.

SIGUIENTE SPRINT: **Cierre de Sprint D multi-familia**
- Codigo completo en las 4 plataformas (ver trazabilidad "Sprint D cliente multi-familia"). Falta para declarar cierre: commit + runs verdes de CI (los `*ControllerTest` multi-familia solo corren con DB) + prueba manual con dos familias y roles mixtos (crear familia, cambiar activa, copiar receta con fotos, verificar aislamiento de datos y del chat).
- Posteriores: presencia online (20), ranking (11), chat 1:1 (14, tras chat fase 4 push), y sprint propio para (10) creador de receta (contrato sync + Room).

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

### Continuacion Codex 2026-07-12 - cierre operativo Sprint D multi-familia

- Punto de partida: `main` estaba en `b027212 feat: multi-familia en backend, Android, Desktop e iOS`, ya pusheado a `origin/main`; worktree limpio salvo `paraImplementar.txt` sin trackear.
- GitHub Actions:
  - `Backend CI/CD` run `29183497963` sobre `b027212` -> `success`; jobs `Build and test backend` y `Deploy backend` -> `success`.
  - Health externo tras deploy Sprint D: `GET https://recetas.167.233.213.242.sslip.io/api/v1/health` -> 200 `UP`.
- E2E API real contra produccion con datos temporales `codex.e2e.20260712091313-c12320.*@example.com`:
  - Creadas dos familias (`sourceFamilyId=720d826e-0192-4b42-8036-21b4519bcb32`, `targetFamilyId=461bb7b7-54c7-4027-90f1-b0f82e1c87d8`).
  - Roles mixtos: guest `MEMBER` en origen y `ADMIN` en destino.
  - Receta creada en origen con ingredientes, pasos y foto real subida desde `recetas.png`.
  - Copia ejecutada por guest desde origen a destino: conserva ingredientes, pasos y foto; la foto copiada mantiene la referencia compartida de URL/storage.
  - Chat verificado aislado por `familyId`: mensaje origen no aparece en destino y viceversa.
  - Tras expulsar guest de origen, `GET /families/{source}/recipes` devuelve 403 y sigue accediendo a la receta/foto copiada en destino.
  - Copia dentro de la misma familia rechazada con 400.
- Hallazgo durante limpieza: `DELETE /api/v1/auth/account` devolvia 500 al anonimizar usuario porque `AuthService.deleteAccount()` generaba `UUID:UUID` (73 bytes) para BCrypt, que rechaza passwords de mas de 72 bytes.
- Hotfix aplicado y desplegado:
  - Commit `e8b2ce9 fix(auth): avoid bcrypt limit on account deletion`.
  - Cambio: anonimizar cuenta con un solo UUID aleatorio antes de hashear.
  - Test nuevo en `AuthServiceTest` con `BCryptPasswordEncoder` real para cubrir el limite de 72 bytes.
  - Validacion local: `mvn -B -f backend/pom.xml -Dtest=AuthServiceTest test` -> 8/0; `mvn -B -f backend/pom.xml -DskipTests compile` -> `BUILD SUCCESS`; `git diff --check` -> sin errores (solo avisos CRLF Windows).
  - `Backend CI/CD` run `29184008888` sobre `e8b2ce9` -> `success`; jobs `Build and test backend` y `Deploy backend` -> `success`.
  - Health externo post-hotfix: 200 `UP` (`checkedAt` 2026-07-12T07:21:29Z).
- Limpieza:
  - Las recetas/fotos temporales se soft-deletearon por API.
  - Tras hotfix, `DELETE /auth/account` de guest y owner -> 204.
  - Login posterior de ambas cuentas -> 401.
  - Verificacion JDBC acotada al sufijo temporal: `users_active=0`, `families_active=0`, `members_active=0`.
- Seguridad:
  - VibeSec-Skill usado como checklist por tocar ciclo de vida de cuenta, roles, ownership y datos multi-tenant.
  - No se detecto fuga tenant en la E2E API: filtros por `familyId`, permisos de copia y serving de foto compartida se comportaron como esperado.
- Pendiente para cierre de producto Sprint D:
  - Falta prueba UI guiada en clientes reales (Desktop/Android) con dos familias y roles mixtos: crear/cambiar familia activa desde UI, copiar receta con foto desde UI y comprobar aislamiento visual de recetas/chat/cache.
  - En esta maquina no hay `adb` disponible/conectado; Desktop puede compilarse, pero la prueba visual requiere interaccion manual.
  - Por tanto, backend/API/CI/deploy quedan verificados, pero no declarar Sprint D cerrado de producto hasta completar la prueba UI cliente.

### Regeneracion de binarios cliente 2026-07-12 - Codex

- APK Android regenerado con `android\gradlew.bat clean assembleDebug`.
  - Ruta: `android/app/build/outputs/apk/debug/app-debug.apk`.
  - Tamano: 22,88 MB.
  - SHA-256: `C5622598F598EB292E55B2BC4D92B4D52542E60112DAD5CABBC2E8A6AB6FA72B`.
  - API por defecto confirmada en build: `https://recetas.167.233.213.242.sslip.io/`.
- Instalador Windows regenerado con `pwsh -NoProfile -ExecutionPolicy Bypass -File desktop/build-installer.ps1`.
  - Ruta instalador: `desktop/output/RecetasFamiliares-Instalador-v1.1.exe`.
  - Tamano: 50,32 MB.
  - SHA-256: `7969F34AB4F7113CAD51F3837B44995EFA6988724F0F357D8F56B2ABE136432E`.
  - App-image: `desktop/output/RecetasFamiliares/RecetasFamiliares.exe`.
  - SHA-256 app-image exe: `BFB9F6FBC3E692CAAE672BC8AC3E58C6F682706716367959599186C64B8D6713`.
  - API por defecto: `https://recetas.167.233.213.242.sslip.io/`.
- Validacion posterior:
  - Android `gradlew.bat test` -> `BUILD SUCCESSFUL`.
  - Desktop `mvn -f desktop/pom.xml test` -> 21 tests, 0 fallos.
- Nota: primer intento de `desktop/build-installer.ps1` con Windows PowerShell 5 fallo por lectura UTF-8 de caracteres decorativos del script; rerun con PowerShell 7 (`pwsh`) correcto.

### Cierre de sesion Codex 2026-07-12 09:32 Europe/Madrid - handoff a Claude Code

- Estado exacto al cerrar:
  - Rama: `main`.
  - Ultimo commit remoto antes de esta nota: `c6ec627 docs: registrar binarios cliente regenerados`.
  - Worktree sin cambios trackeados pendientes; queda `paraImplementar.txt` sin trackear, preexistente, usado como roadmap funcional. No borrarlo ni commitearlo sin decision explicita.
  - Produccion backend verificada tras hotfix: `GET https://recetas.167.233.213.242.sslip.io/api/v1/health` -> 200 `UP`.
- Lo que ya esta hecho y NO debe repetirse salvo sospecha de regresion:
  - Sprint D multi-familia esta implementado y pusheado en backend, Android, Desktop e iOS.
  - Backend CI/CD verde para Sprint D (`29183497963` sobre `b027212`) y hotfix auth (`29184008888` sobre `e8b2ce9`).
  - E2E API real de dos familias/roles/copia con foto/chat aislado paso contra produccion.
  - Hotfix `DELETE /auth/account` por limite BCrypt desplegado y validado; limpieza de cuentas temporales confirmada por API y JDBC.
  - APK debug y EXE Windows regenerados con las implementaciones actuales; rutas y hashes en la seccion anterior.
- Siguiente punto exacto para Claude Code:
  - Sprint recomendado: **Cierre de Sprint D multi-familia - prueba UI cliente real**.
  - No empezar presencia online, ranking, chat 1:1 ni creador de receta hasta decidir si Sprint D queda cerrado de producto.
  - Objetivo: prueba manual guiada en Desktop y Android con dos familias y roles mixtos, usando los binarios regenerados y backend de produccion.
- Runbook minimo de prueba UI:
  1. Leer `CLAUDE.md` completo y las ultimas secciones de `CONTINUAR.md`.
  2. Confirmar `git status --short --branch`; no tocar `paraImplementar.txt` salvo instruccion del usuario.
  3. Desktop: usar `desktop/output/RecetasFamiliares-Instalador-v1.1.exe` o app-image `desktop/output/RecetasFamiliares/RecetasFamiliares.exe`.
  4. Android: instalar `android/app/build/outputs/apk/debug/app-debug.apk` en dispositivo/emulador. En esta sesion no habia `adb`; si sigue sin estar, pedir prueba manual al usuario o conectar dispositivo.
  5. Crear datos temporales identificables (`claude.e2e.<fecha>.*`) y limpiar al terminar con `DELETE /auth/account`; el bug de borrado ya esta corregido.
  6. Verificar desde UI: crear segunda familia, cambiar familia activa, ver miembros/roles, copiar receta con foto entre familias, comprobar que recetas/chat/notas/stats/ratings no muestran datos de la familia anterior tras cambiar.
  7. Probar rol mixto: usuario `MEMBER` en origen y `ADMIN` en destino puede copiar; al perder origen debe conservar destino y no ver origen.
  8. Revisar especialmente cache/sync al cambiar familia activa: que no aparezcan items antiguos ni cursores cruzados tras refresh, relogin o cambio rapido.
  9. Si hay cualquier cambio en auth/roles/sync/ownership/cache multi-tenant, usar VibeSec-Skill y documentar riesgos.
  10. Si la prueba UI pasa, actualizar esta seccion marcando Sprint D cerrado de producto con fecha, cuentas temporales limpiadas, comandos/acciones reales y riesgos residuales. Si falla, corregir solo el blocker, ejecutar tests afectados y repetir la prueba.
- Riesgos vivos para no olvidar:
  - Android logout no vacia Room; se considera residuo local preexistente sin exposicion UI, pero debe observarse en la prueba de cambio de familia.
  - La limpieza futura de fotos fisicas debe contar filas activas de TODAS las familias por `storagePath` antes de borrar archivos compartidos.
  - SMTP real sigue pendiente para cerrar CRIT-2 end-to-end de emails; no bloquea Sprint D multi-familia.
  - APK actual es `debug`; no hay firma release configurada en `android/app/build.gradle.kts`.
  - iOS compila, pero runtime real sigue bloqueado sin macOS/dispositivo.
- Herramientas confirmadas en esta maquina:
  - Codex CLI, Gemini CLI y Claude Code CLI disponibles.
  - VibeSec-Skill disponible; `security-review` callable no disponible.
  - OWASP Dependency-Check configurado por Maven en backend/desktop con `security-audit`, requiere `NVD_API_KEY`.
  - `gh` CLI no disponible.
  - `adb` no disponible/conectado al cierre de esta sesion.

### Sprint Cierre D - prueba UI cliente Android + fixes auth (2026-07-12 mediodia, Claude Code)

- Objetivo: ejecutar la prueba UI real multi-familia pendiente para el cierre de producto de Sprint D, siguiendo el runbook del handoff Codex.
- Agente lider: Claude Code en solitario. Codex/Gemini no consultados: prueba manual guiada + fixes puntuales verificados con tests; no habia incertidumbre de diseno que justificara segunda opinion. VibeSec invocado (se toco autenticacion).
- Herramientas usadas: emulador `Pixel_9_Pro` + adb (input/screencap) para prueba UI real automatizada; PowerShell `Invoke-RestMethod`/`curl.exe` para preparar datos E2E contra produccion. No hizo falta instalar plugins/skills nuevos (autorizacion del usuario registrada; no aplico).

Prueba UI Android EJECUTADA Y SUPERADA (guest `MEMBER` en "E2E Origen", `OWNER` en "E2E Destino"):
- Login desde UI contra produccion con URL default correcta.
- Selector "Cambiar familia activa" con familia actual deshabilitada; el orden de filas varia (la actual se recoloca): no asumir posicion fija al automatizar.
- Cambio de familia: recetas/stats/miembros/roles correctos por familia; pull con cursor por familia trae ingredientes/pasos/fotos tras el cambio.
- "Copiar a otra familia" desde el menu del detalle: la copia aparece en destino con foto (portada+carrusel) e ingredientes (10) verificados visualmente.
- Chat aislado: mensaje enviado en Origen no aparece en el chat de Destino.
- Relanzar la app mantiene sesion sin crash y conserva la familia activa.

3 BUGS REALES encontrados durante la prueba y CORREGIDOS en la sesion:
1. CRASH al arrancar con sesion invalida (cuenta borrada / refresh revocado): `RecetasViewModel.refresh()` tenia try/finally SIN catch y `HttpException 401` mataba el proceso en cada arranque. Fix: catch (re-lanza `CancellationException`) y, si el authenticator ya limpio la sesion, `_isLoggedIn=false` -> vuelve a login. Tambien evita crash de pull-to-refresh sin red.
2. CARRERA de refresh concurrente (Android `TokenRefreshAuthenticator` y Desktop `ApiClient.authenticate`): sin single-flight, N peticiones paralelas con token caducado (el detalle dispara ratings+photos+uploads a la vez) refrescaban en paralelo con el MISMO refresh token; la rotacion atomica del backend (`revokeIfActive`) revoca a los perdedores -> `session.clear()` -> logout/estado roto aleatorio. Reproducido en vivo en el emulador a los ~40 min de sesion. Fix en AMBOS clientes: `synchronized` + re-chequeo del token vigente (si otro hilo ya refresco, reintenta con el token nuevo sin refrescar). iOS usa el plugin Auth de Ktor (single-flight propio); verificar en macOS cuando haya runtime.
3. Estado ZOMBIE tras limpieza de sesion: con la sesion borrada por el authenticator, la UI seguia "logueada" mostrando datos vacios (los flows por familia emiten vacio con familyId null y los repos hacen early-return sin error). Fix: observador de `familyIdFlow` en `RecetasViewModel` que vuelve a login cuando la sesion desaparece de verdad.

Hallazgos SIN corregir (documentados, fuera de alcance quirurgico del sprint):
- PRIVACIDAD (media, mismo dispositivo): tras morir una sesion, el Perfil mostro nombre/email del usuario ANTERIOR ("Abuela Demo", cache local de sesiones del 11-jul) hasta relogin. Confirma el riesgo conocido "logout no vacia Room/cache de usuario". Sprint recomendado: limpiar caches locales al iniciar sesion con un usuario distinto al anterior.
- UX menor: tras login, el header del perfil muestra nombre/email vacios hasta que `/users/me` responde.
- UX menor: cards de listado de recetas sin foto de portada (Android y Desktop); la foto solo se ve en el detalle.
- UI vs backend en "Crear familia": el gating usa el rol de la familia ACTIVA; backend permite crear si eres OWNER/ADMIN de CUALQUIER familia. Conservador, no es fallo de seguridad.

Validaciones ejecutadas en la sesion:
- Android: `gradlew testDebugUnitTest assembleDebug` BUILD SUCCESSFUL (2 veces, tras cada tanda de fixes); APK reinstalado y flujo completo re-probado en emulador.
- Desktop: `mvn test` 21 tests, 0 fallos, BUILD SUCCESS (compila el fix de ApiClient).
- `git diff --check` sin errores (solo avisos CRLF de Windows).
- Seguridad: VibeSec invocado sobre el diff (single-flight sin deadlock, fail-closed preservado, sin tokens en logs, EncryptedSharedPreferences intacto): 0 hallazgos.
- Produccion: health 200 UP; sin cambios de backend en este sprint.

Datos E2E en produccion (NO limpiados a proposito, para la prueba GUI Desktop del usuario):
- Cuentas: `claude.e2e.20260712.owner@example.com` (OWNER de "E2E Origen") y `claude.e2e.20260712.guest@example.com` (OWNER de "E2E Destino", MEMBER en "E2E Origen"). Password compartida de prueba conocida por el usuario de la sesion (no documentada aqui).
- Familias: "E2E Origen" (1eddd914-...) con paella + foto y 1 mensaje de chat; "E2E Destino" (1646a34e-...) con 6 recetas (5 seed + paella copiada con foto).
- LIMPIEZA tras la prueba Desktop: `DELETE /api/v1/auth/account` con cada cuenta (bug bcrypt ya corregido) o desde la propia app (Perfil > Cuenta > Borrar).

PENDIENTE para declarar Sprint D CERRADO DE PRODUCTO:
- Prueba GUI Desktop manual del usuario (JavaFX no automatizable en esta sesion): login guest, cambiar familia activa, copiar receta con foto, verificar aislamiento visual de recetas/chat. OJO: el instalador/app-image v1.1 actual NO incluye el fix single-flight de esta sesion; regenerar instalador (`desktop/build-installer.ps1` con pwsh 7) o probar con `mvn javafx:run`.
- Regenerar APK distribuible (el APK del emulador ya lleva los fixes; el hash documentado en la seccion "Regeneracion de binarios" quedo obsoleto).
- Commit de esta sesion pusheado (ver git log); CI verde a verificar en GitHub Actions (sin `gh` en este PC).

Siguientes sprints candidatos tras el cierre D (orden sugerido):
1. Higiene de sesion/cache local Android (hallazgo de privacidad de esta sesion) + limpieza de Room en logout/cambio de usuario.
2. (3) completo si se desea: edicion de datos/password de miembro por OWNER/ADMIN (decision de seguridad previa).
3. (22) scroll/responsive Desktop.
4. (10) creador de receta visible -> luego (11) ranking.
5. (20) presencia online + (14) chat 1:1 tras chat fase 4/push.

### Continuacion Codex 2026-07-12 tarde - binarios post-fix auth/single-flight

- Punto de partida: `main` alineado con `origin/main` en `00cdb1f fix(auth): single-flight refresh y arranque robusto con sesion invalida`; worktree sin cambios trackeados pendientes. Sigue `paraImplementar.txt` sin trackear, preexistente, no tocar sin decision explicita.
- Alcance ejecutado: solo cierre operativo automatizable de Sprint D. No se modifico codigo fuente en esta continuacion.
- Android:
  - Comando: `android\gradlew.bat clean testDebugUnitTest assembleDebug` -> `BUILD SUCCESSFUL`.
  - APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
  - Tamano: 23.990.383 bytes.
  - SHA-256: `246F72463E567EF004F34E4AC5A9879D42BE01FD6ED49D8B17A179572176E88C`.
  - Warnings observados: preexistentes/de menor impacto (`stripDebugDebugSymbols`, safe call innecesaria, `menuAnchor`/tooltips deprecados, condicion siempre true, icono `Sort` deprecado).
- Desktop:
  - Comando: `mvn test` en `desktop/` -> 21 tests, 0 fallos, `BUILD SUCCESS`.
  - Primer intento de `pwsh -NoProfile -ExecutionPolicy Bypass -File desktop/build-installer.ps1` fallo porque dos procesos `RecetasFamiliares.exe` abiertos desde `desktop/output/RecetasFamiliares` bloqueaban el app-image. Se cerraron solo esos procesos y se reintento.
  - Segundo intento: `desktop/build-installer.ps1` con PowerShell 7 -> build completado; JDK 21.0.11 LTS, Maven NetBeans y NSIS detectados.
  - Instalador: `desktop/output/RecetasFamiliares-Instalador-v1.1.exe`.
  - Tamano instalador: 52.761.331 bytes.
  - SHA-256 instalador: `B95E25178B9C5A3A6C645DF4DF861F0233EA8FB9664CCBE4BB942FABB0094A01`.
  - App-image exe: `desktop/output/RecetasFamiliares/RecetasFamiliares.exe`.
  - Tamano app-image exe: 458.752 bytes.
  - SHA-256 app-image exe: `BFB9F6FBC3E692CAAE672BC8AC3E58C6F682706716367959599186C64B8D6713`.
  - API por defecto embebida: `https://recetas.167.233.213.242.sslip.io/`.
- Seguridad:
  - `VibeSec-Skill` leido/usado como checklist por estar cerrando trabajo relacionado con auth/sesion/cache, pero en esta continuacion no hubo cambios de codigo.
  - `security-review` no disponible como herramienta callable en esta sesion; alternativa aplicada: no se tocaron endpoints/backend ni contratos, y se limitaron acciones a build/test/hashes.
- CI:
  - `gh` CLI no disponible.
  - La pagina publica de GitHub Actions muestra los runs Backend CI/CD previos para `b027212` y `e8b2ce9`, pero no un run para `00cdb1f`.
  - Revisado `.github/workflows/backend-ci-cd.yml`: el workflow solo se dispara por cambios en `backend/**`, `infra/backend/**`, `scripts/backend/**` o el propio YAML. `00cdb1f` toca Android, Desktop y documentacion, por lo que no se espera deploy backend nuevo.
- Estado Sprint D:
  - Sigue SIN declararse cerrado de producto.
  - Falta la prueba GUI Desktop real/manual: login guest, cambiar familia activa, copiar receta con foto, verificar aislamiento visual de recetas/chat/cache y limpiar cuentas E2E al terminar (`DELETE /auth/account` o Perfil > Cuenta > Borrar).
  - Tras esa prueba, si pasa, actualizar esta seccion y entonces cerrar Sprint D; despues avanzar al sprint 1 sugerido: higiene de sesion/cache local Android + limpieza de Room en logout/cambio de usuario.

### Continuacion Codex 2026-07-12 tarde - credenciales nuevas para prueba Desktop Sprint D

- Motivo: el password del set E2E anterior no estaba documentado. Se creo un set E2E nuevo contra produccion para la prueba GUI Desktop manual.
- No se documenta el password en este archivo. Se comunico al usuario en la conversacion de la sesion.
- Guest para login Desktop: `sprintd.desktop.20260712175028.guest@example.com`.
- Owner auxiliar: `sprintd.desktop.20260712175028.owner@example.com`.
- Familias:
  - Origen: `SprintD Origen Desktop` (`283dca5f-a7a5-4405-a365-6113223df5ba`), guest con rol `MEMBER`.
  - Destino: `SprintD Destino Desktop` (`6c405931-3494-4701-9663-0848b402e095`), guest con rol `OWNER`.
- Datos preparados:
  - Receta origen: `Paella Sprint D Desktop` (`9ea02a64-9358-475b-ae74-804d2adf1d45`) con ingredientes, pasos y foto subida via `/photos/upload`.
  - Chat: mensaje distinto en origen y destino para comprobar aislamiento visual.
- Verificacion API previa: login guest OK; lista de familias devuelve destino OWNER y origen MEMBER.
- Limpieza pendiente tras la prueba Desktop: borrar guest y owner desde Perfil > Cuenta > Borrar, o por `DELETE /api/v1/auth/account` autenticado con cada cuenta.
- Limpieza ejecutada despues por Codex via API porque el usuario no encontro opcion visible de borrado en Desktop:
  - `DELETE /api/v1/auth/account` guest -> 204; login posterior -> 401.
  - `DELETE /api/v1/auth/account` owner auxiliar -> 204; login posterior -> 401.
  - Hallazgo UX/funcional: en la prueba Desktop el usuario no encontro la accion de borrar cuenta ni en Miembros ni en Ajustes. Revisar visibilidad/ubicacion de borrado de cuenta Desktop antes de dar por pulida la UX de CRIT-2 en cliente Desktop.

### Cierre de producto Sprint D multi-familia (2026-07-12 tarde/noche, Claude Code)

- El usuario confirmo en esta sesion que la prueba GUI Desktop manual PASO (set E2E `sprintd.desktop.20260712175028.*`): login guest, cambio de familia activa, copia de receta con foto y aislamiento visual de recetas/chat correctos.
- Con esto Sprint D queda CERRADO DE PRODUCTO. Evidencia acumulada: E2E API real (Codex), prueba UI Android automatizada (Claude Code, mediodia), prueba GUI Desktop manual (usuario, tarde), CI backend verde (runs `29183497963` y `29184008888`), binarios regenerados con fixes auth (`00cdb1f`).
- Agente lider: Claude Code. Codex/Gemini no consultados: cierre documental sin cambios de codigo. VibeSec/security-review no aplican (solo documentacion; sin tocar auth, ownership ni datos).
- Cuentas E2E: set `sprintd.desktop.*` limpiado por Codex via `DELETE /auth/account` (documentado arriba). OJO: el set anterior `claude.e2e.20260712.*` (familias "E2E Origen"/"E2E Destino") NO tiene limpieza documentada y su password no quedo registrada; verificar si sigue vivo en produccion y limpiarlo (el usuario conoce la password de sesion, o borrar desde la app Android con login de cada cuenta).
- Backlog que hereda del cierre (orden sugerido ya registrado en la seccion anterior):
  1. Sprint E - higiene de sesion/cache local Android (fuga de privacidad: datos del usuario anterior visibles tras cambio de sesion) + visibilidad de "Borrar cuenta" en Desktop (hallazgo de la prueba del usuario).
  2. (3) completo: edicion de datos/password de miembro por OWNER/ADMIN (decision de seguridad previa).
  3. (22) scroll/responsive Desktop (7 vistas sin ScrollPane envolvente).
  4. (10) creador de receta visible -> (11) ranking.
  5. (20) presencia online + (14) chat 1:1 tras chat fase 4/push.
- Riesgos residuales vivos: SMTP produccion pendiente (usuario); APK debug sin firma release; iOS runtime bloqueado sin macOS; cuentas `claude.e2e.20260712.*` posiblemente vivas.

### Sprint E - Higiene de sesion/cache local Android + visibilidad cuenta Desktop (2026-07-12 noche, Claude Code)

- Objetivo: cerrar el hallazgo de privacidad de Sprint D (datos del usuario anterior visibles en el mismo dispositivo) y el hallazgo UX de la prueba Desktop ("Eliminar cuenta" no encontrable).
- Agente lider: Claude Code. Codex/Gemini: bloques IDE de revision solo lectura entregados al usuario al cierre (peticion explicita del usuario).
- Android:
  - `SessionStore.clear()` preserva `last_user_id` (solo UUID, en EncryptedSharedPreferences) y expone `lastKnownUserId` para detectar cambio de usuario tras cualquier limpieza de sesion (logout, authenticator, cambio de servidor).
  - `AuthRepository` recibe `clearLocalData`; en `login()`, si el usuario difiere del ultimo conocido, vacia Room ANTES de escribir la sesion nueva (fail-closed: si el wipe falla, falla el login y no se mezclan datos).
  - `RecetasViewModel.wipeLocalCaches()` (clearAllTables en Dispatchers.IO, best-effort) invocado en `logout()` y en los dos caminos de cambio/reset de URL de servidor.
  - `AppContainer` inyecta el wipe real (`database.clearAllTables()` con `withContext(Dispatchers.IO)`).
  - Decision de producto: al cerrar sesion se pierden cambios offline no sincronizados; privacidad en dispositivo compartido gana sobre el residuo local. Datos maestros en el servidor.
- Desktop: boton de sidebar "👤 Mi perfil y cuenta" (vista `profile` existente, todos los roles, con estado activo en la sidebar). "Eliminar cuenta" ya vivia en Perfil > Cuenta pero solo era accesible clicando la user card y el usuario no lo descubrio.
- Validacion ejecutada: Android `gradlew testDebugUnitTest assembleDebug` BUILD SUCCESSFUL (incluye `AuthRepositoryUserChangeTest` nuevo: usuario distinto vacia cache, mismo usuario conserva, primer login no vacia); Desktop `mvn test` 21 tests 0 fallos.
- Seguridad: VibeSec invocado sobre el diff, 0 hallazgos criticos. `security-review` no aplica (backend intacto). Residuales: wipe de logout best-effort con segunda barrera fail-closed en login de usuario distinto; un pull en vuelo iniciado antes del logout podria escribir tras el wipe (ventana pequena, cubierta por la segunda barrera).
- Pendiente/observaciones: Desktop mantiene caches en memoria hasta que el pull del siguiente login las reemplaza (evaluar si hace falta limpieza explicita al cambiar de usuario en el mismo proceso); binarios NO regenerados en esta sesion (APK/instalador actuales no llevan Sprint E); warning Kotlin "Condition is always true" en RecetasViewModel preexistente.

Integracion revision Codex/Gemini (alcance B autorizado por el usuario):
- Codex reporto 3 hallazgos; los 3 verificados contra codigo y CONFIRMADOS (el primero rebajado de ALTA a MEDIA: sin exposicion entre familias en UI, todas las queries filtran por familyId activo; el impacto real era residuo en disco + cache vacia autorreparable). Gemini reporto 1 accionable (aviso de perdida de cambios offline al cerrar sesion), tambien aplicado.
- Fix carrera: guard de sesion en SyncRepository por USUARIO (`ownerUserId` capturado al iniciar; se relee tras cada respuesta y antes de aplicar ACKs del push). Cambiar de FAMILIA con sync en vuelo sigue siendo legitimo (cursor por familia; el primer intento de guard por familyId rompio el test E2E `push 409 tras cambio de familia` y se corrigio a userId).
- Fix orden: `wipeJob` en RecetasViewModel; `login()` hace `join()` del wipe pendiente antes de tocar el repositorio.
- Fix robustez: `SessionStore.pendingWipe` (sobrevive a clear(), se marca antes de intentar el wipe, se desmarca solo al exito); `AuthRepository.login()` fuerza el vaciado si esta marcado aunque el usuario coincida.
- UX: dialogo de confirmacion al cerrar sesion en Android (aviso de borrado local y de cambios sin sincronizar, haptic en confirmar).
- Tests añadidos: orden wipe->sesion, wipe fallido aborta login sin escribir tokens, pendingWipe fuerza vaciado, y pull en vuelo no aplica datos si la sesion del usuario murio. Android `testDebugUnitTest assembleDebug` BUILD SUCCESSFUL (46 tests).
- VibeSec re-aplicado al diff de la integracion: 0 hallazgos. Residual documentado: ventana TOCTOU microscopica entre el guard y el upsert (reducida de segundos a microsegundos; cerrar del todo exigiria transaccion Room con check dentro, no justificado).

### Limpieza cuentas E2E claude.e2e.20260712.* (2026-07-12 noche, Claude Code)

- Verificado en BD de produccion (SSH + psql via root@VPS): las 2 cuentas y las familias "E2E Origen"/"E2E Destino" seguian vivas.
- El password no estaba documentado; en vez de DELETE por SQL a mano, se uso el flujo del propio backend: token de PASSWORD_RESET inyectado en `account_action_tokens` (Base64URL de SHA-256, mismo formato que `AccountActionTokenService.hash`), `POST /auth/password-reset/confirm` (204), login (200) y `DELETE /auth/account` (204) por cada cuenta; login posterior 401.
- Estado final verificado por SQL: usuarios anonimizados (`deleted+<id>@deleted.recetas.local`, deleted=t) y ambas familias soft-deleted (el propio `AuthService.removeMembershipForDeletedUser` las cerro al quedarse sin miembros).
- Residuo minimo aceptado: los archivos fisicos de la foto de la paella quedan huerfanos en `uploads/` del VPS (no servibles: ownership fail-closed y familias borradas). Entra en la limpieza futura de huerfanos ya documentada.
- SMTP CONFIGURADO 2026-07-12 noche: el usuario aporto App Password de Gmail; añadidas MAIL_ENABLED=true, MAIL_FROM, SMTP_HOST=smtp.gmail.com:587, SMTP_USERNAME, SMTP_PASSWORD y APP_PUBLIC_URL a `/etc/recetas-familiares/backend.env` (backup previo `backend.env.bak-20260712`, chmod 600), servicio reiniciado, health UP.
- Prueba de envio real: `POST /auth/password-reset/request` a la cuenta real del usuario -> 202, sin errores en journalctl, y el usuario CONFIRMO la recepcion del correo en Gmail (2026-07-12 noche).
- SMTP deja de ser pendiente: entrega real verificada. CRIT-2 cerrado operativo: reset (request+entrega+confirm validados; confirm ejercitado hoy ademas en la limpieza E2E), borrado de cuenta validado en produccion. Residual menor: el correo de "verificar email" no se ha probado con buzon real, pero usa el mismo canal SMTP ya verificado.

### Cierre de sesion Claude Code 2026-07-12 noche — punto exacto para retomar

Estado exacto al cerrar:
- Rama `main` alineada con `origin/main`, ultimo commit `50b2d83`. Arbol limpio; solo `paraImplementar.txt` sin trackear (intocable sin decision explicita).
- Cerrado en esta sesion: Sprint D declarado cerrado de producto (`64170c0`); Sprint E implementado (`1e3b083`) con revision Codex/Gemini integrada en alcance B (`a5b483a`); cuentas `claude.e2e.20260712.*` eliminadas de produccion; SMTP configurado y CERRADO con entrega real confirmada por el usuario (CRIT-2 operativo).
- Produccion: health UP; sin cambios de codigo backend en la sesion (solo env SMTP en el VPS con backup `backend.env.bak-20260712`).

Checklist de cierre (protocolo CLAUDE.md):
- Contexto leido en sesion: SI (CLAUDE.md, CONTINUAR.md, fuentes afectadas).
- Agentes IA: Codex y Gemini consultados via bloques IDE para Sprint E; sus hallazgos se verificaron contra codigo antes de integrar (severidad de Codex-1 rebajada con evidencia; guard inicial por familyId corregido a userId gracias al test E2E).
- Seguridad: VibeSec invocado 2 veces (diff Sprint E y diff integracion), 0 hallazgos criticos. `security-review` NO APLICA: no se toco codigo backend (solo clientes y operacion). Plugin security-guidance activo por hooks.
- Tests: Android `testDebugUnitTest assembleDebug` 46 tests 0 fallos; Desktop `mvn test` 21 tests 0 fallos. iOS no aplica (sin cambios iOS; runtime bloqueado sin macOS).
- Trazabilidad: este archivo actualizado en cada hito y pusheado.

PUNTO EXACTO PARA RETOMAR (siguiente accion, ~10 min, sin decision pendiente):
- Regenerar binarios: los distribuibles actuales NO llevan Sprint E (wipe de cache, single-flight ya iba, dialogo logout, boton Perfil Desktop).
  - Android: `cd android; .\gradlew.bat clean testDebugUnitTest assembleDebug` -> APK en `app/build/outputs/apk/debug/`.
  - Desktop: cerrar procesos `RecetasFamiliares.exe` abiertos; `pwsh -NoProfile -ExecutionPolicy Bypass -File desktop/build-installer.ps1` (PowerShell 7, no PS5).
  - Documentar hashes SHA-256 nuevos aqui.

Orden de sprints siguientes (acordado con el usuario):
1. Regenerar binarios (arriba). Micro-tarea, hacerla antes de cualquier sprint.
2. Sprint (3) completo: OWNER/ADMIN edita datos/password de otro miembro. ANTES de codificar, presentar al usuario la decision de seguridad: admin-reset de password ajena (opciones: solo disparar email de reset al miembro — recomendada, minimo riesgo; o set directo de password temporal con cambio forzado — mas soporte pero mas riesgo). Toca backend (endpoint nuevo + ownership) -> VibeSec + security-review obligatorios y bloques Codex/Gemini al cierre.
3. Sprint (22): scroll/responsive Desktop — CERRADO en seccion 2026-07-12 noche.
4. Sprint (10): creador de receta visible — CERRADO en seccion 2026-07-12 noche.
5. Siguiente recomendado: Sprint (11) ranking de usuarios por recetas y calificaciones (depende de 9+10, ya cumplidos).
6. Sprint (20) presencia online + avisos, y (14) chat 1:1 — tras chat fase 4 (push notifications).

Riesgos vivos que hereda la proxima sesion:
- Binarios distribuibles desactualizados (punto 1).
- iOS: runtime sin validar (sin macOS); NUEVO-4 pendiente (`ios/.../sync/SyncRepository.kt` traga CancellationException); URL horneada iOS pendiente de sprint clientes.
- Desktop: caches en memoria no se limpian al cambiar de usuario en el mismo proceso (el pull del login siguiente las reemplaza; evaluar).
- Correo de verificacion de email sin prueba de buzon real (mismo canal SMTP verificado).
- Fotos huerfanas en `uploads/` del VPS (limpieza de huerfanos futura).
- APK debug sin firma release.

### Regeneracion de binarios post Sprint E 2026-07-12 noche - Codex

- Punto de partida: rama `main` alineada con `origin/main`, commit `7b0a462`; worktree limpio salvo `paraImplementar.txt` sin trackear.
- Objetivo: regenerar los distribuibles para que incluyan Sprint E (wipe de cache Android, dialogo logout, boton Perfil Desktop y fixes de carrera integrados).
- Android:
  - Comando: `android\gradlew.bat clean testDebugUnitTest assembleDebug` -> `BUILD SUCCESSFUL`.
  - APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
  - Tamano: 24.006.767 bytes.
  - SHA-256: `963665A71C44B924C250392F468D176B4B6B2004C0D8B96AEB8BE6D5B2806C34`.
  - Warnings observados: trust store `NUL` no legible en configuracion Gradle, `stripDebugDebugSymbols`, safe call innecesaria, APIs Compose deprecadas y condicion siempre true preexistente en `RecetasViewModel`.
- Desktop:
  - Comando: `mvn test` en `desktop/` -> 21 tests, 0 fallos, `BUILD SUCCESS`.
  - Comando: `pwsh -NoProfile -ExecutionPolicy Bypass -File desktop/build-installer.ps1` -> build completado con JDK 21.0.11 LTS, Maven NetBeans y NSIS.
  - Instalador: `desktop/output/RecetasFamiliares-Instalador-v1.1.exe`.
  - Tamano instalador: 52.764.730 bytes.
  - SHA-256 instalador: `28E290641700A57745AC2002188CC144C79A3416F2C997669E49FF773AC5A192`.
  - App-image exe: `desktop/output/RecetasFamiliares/RecetasFamiliares.exe`.
  - Tamano app-image exe: 458.752 bytes.
  - SHA-256 app-image exe: `BFB9F6FBC3E692CAAE672BC8AC3E58C6F682706716367959599186C64B8D6713`.
  - API por defecto embebida: `https://recetas.167.233.213.242.sslip.io/`.
- Seguridad/agentes: no se modifico codigo; VibeSec/security-review no aplican a esta micro-tarea de build. No se consulto Codex/Gemini externos.
- Estado: binarios distribuibles actualizados con Sprint E. Siguiente sprint funcional pendiente: Sprint (3) completo, previa decision de seguridad sobre reset de password de miembros.

### Sprint (3) editar miembros y password - implementado 2026-07-12 noche - Codex

- Decision de producto recibida del usuario: para password de miembros son validas ambas opciones, a elegir por OWNER/ADMIN: enviar email de recuperacion al miembro o imponer una password temporal.
- Backend:
  - Nuevo contrato `PUT /api/v1/families/{familyId}/members/{userId}` con `UpdateFamilyMemberRequest` (`displayName`, `email`, `passwordAction`, `temporaryPassword`).
  - Autorizacion server-side: solo OWNER/ADMIN de la familia; bloquea editarse a uno mismo desde administracion; bloquea editar cuentas OWNER; exige que el objetivo sea miembro activo de esa familia.
  - Edicion de email: normaliza email, rechaza duplicados, deja el email sin verificar, revoca refresh tokens y emite email de verificacion si SMTP esta activo.
  - Password temporal: valida minimo 12 caracteres, guarda hash con `PasswordEncoder` y revoca refresh tokens del miembro.
  - Email de reset: falla cerrado con 503 si SMTP no esta activo; si esta activo, emite token `PASSWORD_RESET` y envia correo. No revoca sesiones hasta que el miembro confirme el reset, igual que el flujo normal.
  - Robustez transaccional: guarda usuario y emite todos los tokens antes de enviar correos, para no enviar enlaces de tokens que pudieran quedar revertidos.
- Android:
  - Perfil > Miembros: tocar una fila editable o usar menu `...` abre `Editar`.
  - Dialogo con nombre, email y selector de password (`No cambiar`, `Enviar email de recuperacion`, `Definir temporal`).
  - DTO/API/repositorio/ViewModel conectados; la lista local se actualiza con la respuesta del servidor.
- Desktop:
  - `FamilyMembersView`: boton `Editar` y doble clic sobre fila editable.
  - Dialogo con nombre, email, accion de password y campo temporal activado solo cuando procede.
  - DTO/API/repositorio conectados al mismo endpoint backend.
- Tests/validacion:
  - `mvn -f backend/pom.xml -DskipTests compile` OK.
  - `mvn -f backend/pom.xml "-Dtest=FamilyServiceTest" test` OK: 7 tests, 0 fallos.
  - `FamilyMemberControllerTest` ampliado con cobertura HTTP para password temporal, permisos, self/OWNER, email duplicado y reset fail-closed con SMTP desactivado; ejecucion local bloqueada por entorno: `DB_TEST_PASSWORD` vacio en `application-test.yml` y Postgres pidio password.
  - Android `gradlew.bat testDebugUnitTest` OK; Android `gradlew.bat assembleDebug` OK.
  - Desktop `mvn test` OK: 21 tests, 0 fallos.
  - `git diff --check` OK; solo avisos CRLF esperados en Windows.
- Seguridad/herramientas:
  - Skill VibeSec usado para guiar el cambio (auth/ownership/password).
  - `security-review` dedicado no esta disponible como herramienta callable en esta sesion; se compensa con revision local VibeSec + tests de permiso/fail-closed.
  - Multiagente integrado existe via herramienta de sub-agentes, pero sus reglas solo permiten usarlo si el usuario pide delegacion/trabajo paralelo explicito; no se uso en este sprint.
- Herramientas confirmadas en esta shell:
  - En PATH: `codex`, `gemini`, `claude`, `mvn`, `pwsh`.
  - No en PATH: `claude-mem`, `dependency-check`, `dependency-check.bat`, `adb`.
  - `adb` disponible por ruta absoluta: `C:\Users\GipsyDavy\AndroidSDK\platform-tools\adb.exe`.
  - OWASP Dependency-Check disponible como perfil Maven bajo demanda en backend/desktop: `mvn verify -P security-audit` (plugin Maven, no CLI global).
- Estado de producto: Sprint (3) queda implementado en backend, Android y Desktop. iOS no se toco en este sprint; el endpoint es aditivo y no rompe clientes existentes.
- Siguiente sprint en ese momento: Sprint (22) scroll/responsive Desktop, cerrado en la seccion siguiente.

### Sprint (22) scroll/responsive Desktop - implementado 2026-07-12 noche - Codex

- Objetivo: evitar que al reducir la ventana de Desktop queden campos, tablas o botones fuera del area visible.
- Alcance aplicado:
  - Nueva utilidad `desktop/.../ui/DesktopScroll.java` para configurar paginas JavaFX desplazables: ancho ajustado, sin scroll horizontal y contenido con altura minima igual al viewport.
  - `WeeklyMenuView`: convertida a `ScrollPane`; toolbar superior en `FlowPane` para que los botones se envuelvan en ancho reducido.
  - `StockView`: convertida a `ScrollPane`; tabla conserva `VBox.setVgrow`; toolbar en `FlowPane`.
  - `FamilyMembersView`: convertida a `ScrollPane`; conserva tabla expansible y toolbar de admin en `FlowPane`.
  - `NotesView`: convertida a `ScrollPane`; `SplitPane` de lista/detalle conserva crecimiento vertical.
  - `RecipeListView`: ahora expone el mismo API publico pero su contenido real es un `SplitPane` dentro de `ScrollPane`, manteniendo lista/detalle.
  - `LoginView`: convertida a `ScrollPane`; en modo registro los campos/botones ya se pueden desplazar si la ventana queda baja.
  - `CookingView`: scroll en la zona central del modo cocina, manteniendo fija la barra superior.
- Validacion:
  - `desktop/mvn test` OK: 21 tests, 0 fallos.
  - `git diff --check` OK; solo avisos CRLF normales de Windows.
- Seguridad/agentes: no toca backend, auth ni datos; VibeSec/security-review no aplican a este sprint UI-only. No se uso multiagente.
- Estado: Sprint (22) implementado en Desktop. No se regeneraron instaladores en este paso.
- Siguiente sprint recomendado en ese momento: Sprint (10) creador de receta visible, cerrado en la seccion siguiente.

### Sprint (10) creador de receta visible - implementado 2026-07-12 noche - Codex

- Objetivo: dejar constancia visible de que usuario crea cada receta.
- Decision de seguridad aplicada: el cliente NO puede enviar ni modificar autor. Backend deriva el creador desde el usuario autenticado en create/copy y en sync/push de recetas nuevas offline; las actualizaciones y deletes conservan el autor existente.
- Backend:
  - Nueva migracion Flyway `V18__add_recipe_creator.sql`: `recipes.created_by_user_id` nullable, FK a `users(id)` e indice.
  - `RecipeEntity` incorpora `createdByUser`; `RecipeResponse` expone `createdByUserId` y `createdByDisplayName`.
  - `RecipeService.createRecipe` y `copyRecipe` sellan el creador con el usuario autenticado.
  - `SyncService.push` atribuye recetas nuevas subidas offline al usuario autenticado que hace el push y no acepta campos de autor desde `SyncRecipePushItem`.
  - `RecipeRepository` usa `@EntityGraph` para traer el creador al listar/get/sync y evitar N+1 basico.
  - Seed demo de desarrollo (`DevDataSeeder`) marca la receta demo con el usuario dev; recetas starter automaticas quedan sin autor humano para no atribuir falsamente.
- Android:
  - `RecipeDto` y `RecipeEntity` incorporan `createdByUserId`/`createdByDisplayName`.
  - Room sube a version 3 con migracion `MIGRATION_2_3`; widgets registran tambien la migracion.
  - Nuevo schema versionado `android/app/schemas/.../3.json`.
  - Tarjetas, detalle y texto compartido muestran `Por {nombre}` / `Creada por {nombre}` cuando el servidor lo devuelve.
- Desktop:
  - `RecipeDtos.RecipeDto` incorpora los dos campos de autor.
  - Lista, detalle y dashboard muestran `Por {nombre}` en metadatos si existe.
- iOS/KMP:
  - `RecipeDto` incorpora autor nullable con defaults.
  - SQLDelight local guarda `createdByUserId` y `createdByDisplayName`.
  - Pull/sync cachea esos campos; lista, detalle y compartir los muestran cuando existen.
- Tests/validacion:
  - Backend `mvn -f backend/pom.xml -DskipTests compile` OK.
  - Backend `mvn -f backend/pom.xml "-Dtest=RecipeServiceTest,SyncServiceTest" test` OK: 7 tests, 0 fallos.
  - `SyncServiceTest` cubre que una receta offline nueva se atribuye al usuario autenticado del push y que una receta existente conserva su creador original.
  - Android `gradlew.bat testDebugUnitTest assembleDebug` OK.
  - Desktop `mvn test` OK: 21 tests, 0 fallos.
  - iOS/KMP `gradlew.bat :composeApp:compileKotlinMetadata` OK; SQLDelight genero interfaz common. Runtime iOS sin validar por falta de macOS/dispositivo.
  - `git diff --check` OK; solo avisos CRLF normales de Windows.
- Seguridad/herramientas:
  - Skill VibeSec usado por tocar backend/auth ownership y contrato sync.
  - `security-review` dedicado no esta disponible como herramienta callable en esta sesion; revision compensada con regla fail-closed de no aceptar autor cliente + tests unitarios.
  - No se uso multiagente externo; no fue solicitado explicitamente.
- Estado: Sprint (10) implementado en backend, Android, Desktop e iOS/KMP. No se regeneraron instaladores/APK release despues de este sprint.
- Siguiente sprint recomendado: Sprint (11) ranking de usuarios por recetas y calificaciones, ahora que existen ratings (9) y autor de receta (10).

### Sprint (11) ranking de usuarios por recetas y calificaciones - implementado 2026-07-12 noche - Codex

- Objetivo: ranking familiar de usuarios segun recetas creadas y calificaciones recibidas.
- Decision de seguridad aplicada:
  - Ranking siempre calculado en backend y acotado por familia; el cliente no envia agregados ni puntuaciones.
  - Acceso exige pertenencia activa a la familia (`existsByFamily_IdAndUser_IdAndDeletedFalse`).
  - Las autovaloraciones no cuentan en el ranking para evitar inflar la puntuacion propia.
  - Formula actual: `score = recetas creadas activas + suma de estrellas recibidas de otros usuarios`; orden estable por score, media, valoraciones recibidas, recetas creadas y nombre.
- Backend:
  - Nuevo contrato `GET /api/v1/families/{familyId}/recipe-rankings/users`.
  - Nuevo paquete `rankings` con `RecipeRankingController`, `RecipeRankingService` y `UserRecipeRankingResponse`.
  - `RecipeRepository.countActiveRecipesByCreator()` agrega recetas activas por creador.
  - `RecipeRatingJpaRepository.aggregateReceivedRatingsByRecipeCreator()` agrega valoraciones recibidas por creador de receta, excluyendo self-ratings.
  - Incluye miembros activos aunque tengan 0 recetas/valoraciones, para que el ranking sea familiar completo.
- Android:
  - DTO/API/repositorio/ViewModel conectados.
  - Perfil carga `userRecipeRankings`, lo limpia al cambiar de familia/logout y lo refresca al crear/actualizar/borrar valoraciones.
  - Nueva seccion "Ranking de recetas" en Perfil con top 10, recetas, valoraciones, media y puntos.
- Desktop:
  - DTO y `FamilyRepository.loadRecipeRanking()` conectados al endpoint.
  - `ProfileView` muestra el ranking dentro de "Mi familia" tras las estadisticas, con fallback "no disponible" si no hay red/API.
- iOS/KMP:
  - DTO `UserRecipeRankingDto` y `FamilyMemberRepository.recipeRanking()` conectados.
  - `SettingsScreen` muestra top 5 compacto si hay familia activa y repositorio disponible.
- Tests/validacion:
  - Backend `mvn -f backend/pom.xml -DskipTests compile` OK.
  - Backend `mvn -f backend/pom.xml -Dtest=RecipeRankingServiceTest test` OK: unit tests de autorizacion, ranking, miembros con cero y desempate estable.
  - `RecipeRankingControllerTest` anadido y ejecutado contra la BD real de test cargando variables desde `herztner/recetas_app.env` sin exponer secretos: valida endpoint real, ranking con propietario/admin, bloqueo cross-family y exclusion de self-rating. El test usa emails unicos por ejecucion para no chocar con una BD de test persistente.
  - Backend `mvn -f backend/pom.xml "-Dtest=RecipeRankingServiceTest,RecipeRankingControllerTest" test` OK cargando `DB_TEST_URL`, `DB_TEST_USERNAME` y `DB_TEST_PASSWORD` desde `herztner/recetas_app.env`. Flyway dejo `recetas_familiares_test` en version v18.
  - Android `gradlew.bat testDebugUnitTest assembleDebug` OK.
  - Desktop `mvn test` OK: 21 tests, 0 fallos.
  - iOS/KMP `gradlew.bat :composeApp:compileKotlinMetadata` OK.
  - `git diff --check` OK; solo avisos CRLF normales de Windows.
- Seguridad/herramientas:
  - Skill VibeSec usado por tocar endpoint familiar y agregados de datos.
  - `security-review` dedicado no esta disponible como herramienta callable; se compensa con autorizacion server-side, exclusion de self-rating y tests unitarios.
  - No se uso multiagente externo; no fue solicitado explicitamente.
- Estado: Sprint (11) implementado en backend, Android, Desktop e iOS/KMP. No se regeneraron instaladores/APK release despues de este sprint.
- Siguiente sprint recomendado: Sprint (12) exportar/copiar recetas entre grupos familiares, ya que depende de usuario en varias familias y el backend ya tiene `copyRecipe`.

### Punto exacto para retomar despues de Sprint (11) - 2026-07-12 noche - Codex

- Estado al cerrar:
  - Sprint (3), Sprint (22), Sprint (10) y Sprint (11) implementados en el worktree actual.
  - Worktree NO limpio: hay cambios acumulados de esos sprints sin commit. No revertir nada sin autorizacion explicita.
  - `paraImplementar.txt` sigue sin trackear; no tocarlo salvo orden explicita.
  - BD real de test `recetas_familiares_test` accesible cargando variables desde `herztner/recetas_app.env`; no imprimir secretos.
  - Flyway en test quedo en version v18 tras ejecutar `RecipeRankingControllerTest`.
- Validacion mas reciente:
  - Backend compile OK.
  - Backend ranking real OK: `mvn -f backend/pom.xml "-Dtest=RecipeRankingServiceTest,RecipeRankingControllerTest" test` con variables cargadas desde `herztner/recetas_app.env`.
  - Android `testDebugUnitTest assembleDebug` OK.
  - Desktop `mvn test` OK.
  - iOS/KMP `:composeApp:compileKotlinMetadata` OK.
  - `git diff --check` OK; solo avisos CRLF normales de Windows.
- Herramientas utiles confirmadas:
  - Skill VibeSec disponible y usado cuando se toca backend/auth/ownership.
  - `codex`, `gemini`, `claude`, `mvn`, `pwsh` en PATH.
  - OWASP Dependency-Check no esta como CLI global; si hace falta, usar perfil Maven `mvn verify -P security-audit` en backend/desktop.
  - `adb` por ruta absoluta: `C:\Users\GipsyDavy\AndroidSDK\platform-tools\adb.exe`.

#### Siguiente sprint obligatorio recomendado: Sprint (12) exportar/copiar recetas entre grupos familiares

Objetivo:
- Permitir que un usuario que pertenece a varias familias copie una receta de una familia origen a otra familia destino sin reescribirla.
- Debe copiar receta, ingredientes, pasos y fotos cuando existan.
- Debe respetar autorizacion backend aunque el cliente oculte botones.

Antes de editar:
- Leer `CLAUDE.md`, `CONTINUAR.md`, `Interfaz.md` si se toca UI, y fuentes afectadas.
- Usar VibeSec porque el sprint toca ownership familiar, copia entre familias y datos de receta.
- Revisar estado actual de `RecipeService.copyRecipe`, `RecipeController`, clientes Android/Desktop/iOS y pruebas existentes antes de asumir alcance.
- Confirmar si hay cambios previos sin commit que afecten los mismos archivos; trabajar con ellos, no revertir.

Backend esperado:
- Revisar endpoint existente `POST /api/v1/families/{sourceFamilyId}/recipes/{recipeId}/copy`.
- Reglas minimas:
  - Usuario debe ser miembro activo de la familia origen para leer.
  - Usuario debe ser OWNER o ADMIN de la familia destino para escribir.
  - Familia destino debe ser distinta de origen.
  - No aceptar autor desde cliente; la copia debe quedar creada por el usuario autenticado, ya cubierto por Sprint (10).
  - Copiar ingredientes, pasos y fotos conservando orden y metadatos necesarios.
  - No copiar valoraciones, favoritos, notas privadas ni ranking; son datos dependientes del contexto familiar.
- Tests backend recomendados:
  - Copia completa receta+ingredientes+pasos+fotos.
  - Bloquea si no es miembro de origen.
  - Bloquea si no es OWNER/ADMIN en destino.
  - Bloquea misma familia como destino.
  - Autor de copia = usuario autenticado.
  - No copia ratings/favoritos/notas.

Android esperado:
- Detectar familias del usuario desde `FamilyMemberRepository.families()`.
- En detalle de receta, mostrar accion "Copiar a otra familia" solo si hay mas de una familia y hay destinos posibles.
- Abrir selector de familia destino; excluir familia activa.
- Llamar `RecipeRepository.copyToFamily(recipeId, targetFamilyId)`.
- Mostrar confirmacion con nombre de familia destino.
- Si se copia a la familia activa por error o destino invalido, bloquear antes de llamar.
- Tras copiar, no cambiar automaticamente de familia salvo decision explicita del usuario.

Desktop esperado:
- Revisar si ya existe UI parcial en detalle/listado por cambios anteriores.
- Anadir accion clara en detalle de receta o menu contextual: "Copiar a familia".
- Selector de destino con familias distintas a la activa.
- Usar `FamilyRepository.loadMyFamilies()` y repositorio de recetas/API existente.
- Mantener scroll/responsive de Sprint (22).

iOS/KMP esperado:
- Al menos contrato DTO/repo si la UI completa es pequena y compila.
- Si se toca UI, mantenerlo compacto por la deuda de layout de Ajustes/listas y validar `:composeApp:compileKotlinMetadata`.

Validaciones minimas para cerrar Sprint (12):
- Backend: compile y tests especificos de copia. Si se usan tests Spring, cargar env desde `herztner/recetas_app.env` sin imprimir secretos:
```powershell
Get-Content -Path 'herztner/recetas_app.env' | ForEach-Object {
  if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.*)\s*$') {
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
  }
}
mvn -f backend/pom.xml "-Dtest=RecipeServiceTest,RecipeControllerTest" test
```
- Android: `cd android; .\gradlew.bat testDebugUnitTest assembleDebug`.
- Desktop: `cd desktop; mvn test`.
- iOS/KMP si se toca: `cd ios; .\gradlew.bat :composeApp:compileKotlinMetadata`.
- `git diff --check`.
- Actualizar este `CONTINUAR.md` con resultado, tests y siguiente sprint.

#### Orden recomendado despues de Sprint (12)

1. Regenerar binarios debug/desktop si el usuario va a probar Sprint (10)-(12) en dispositivos reales.
2. Sprint (20): presencia online, icono de miembros activos y avisos de nuevas recetas/notas/stock. Depende de definir polling/WS y alcance de notificaciones.
3. Sprint (14): chat privado 1:1 y chat familiar avanzado. Conviene hacerlo despues de presencia/notificaciones para reutilizar infraestructura.
4. Sprint (8): busqueda de recetas/documentacion/alimentos en internet. Requiere diseno de seguridad: fuentes permitidas, scraping/API, atribucion y moderacion de contenido.
5. Sprint (16): comparar recetas con internet y sugerir mejoras. Debe ir despues de Sprint (8), porque depende de busqueda externa y evaluacion de contenido.

No iniciar Sprint (20), (14), (8) o (16) hasta cerrar Sprint (12) o recibir cambio explicito de prioridad del usuario.

### Regeneracion de binarios post Sprint (11) 2026-07-12 noche - Codex

- Objetivo: reescribir los binarios con los cambios acumulados de Sprint (3), Sprint (22), Sprint (10) y Sprint (11).
- Punto de partida: worktree con cambios sin commit; no se modifico codigo fuente para esta tarea, solo se regeneraron artefactos y se actualizo esta documentacion.
- Android:
  - Comando: `cd android; .\gradlew.bat clean testDebugUnitTest assembleDebug`.
  - Resultado: `BUILD SUCCESSFUL`.
  - APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
  - Tamano APK: 24.023.151 bytes.
  - SHA-256 APK: `69516EA2FE2DD467032B704401A75156A72A928A7B612D11DFA2E561020E6EE2`.
  - Warnings observados: librerias nativas no strippeables empaquetadas tal cual, safe call innecesaria, APIs Compose deprecadas y condicion siempre true preexistente en `RecetasViewModel`.
- Desktop:
  - Comando principal: `pwsh -NoProfile -ExecutionPolicy Bypass -File desktop/build-installer.ps1`.
  - Resultado: build completado con JDK 21.0.11 LTS, Maven NetBeans, jpackage y NSIS.
  - El script de empaquetado ejecuta Maven package con tests saltados; despues se ejecuto `cd desktop; mvn -q test` y paso OK.
  - Instalador: `desktop/output/RecetasFamiliares-Instalador-v1.1.exe`.
  - Tamano instalador: 52.772.077 bytes.
  - SHA-256 instalador: `064E06DE1A2C2E3386DC22AABF6FE2CFD2B5836685279C88730EABB739A6012E`.
  - App-image exe: `desktop/output/RecetasFamiliares/RecetasFamiliares.exe`.
  - Tamano app-image exe: 458.752 bytes.
  - SHA-256 app-image exe: `BFB9F6FBC3E692CAAE672BC8AC3E58C6F682706716367959599186C64B8D6713`.
  - JAR principal dentro del app-image: `desktop/output/RecetasFamiliares/app/RecetasFamiliares.jar`.
  - Tamano JAR principal: 21.411.928 bytes.
  - SHA-256 JAR principal: `593212627FF38AA821D2AD32887851615C51330DF3DEA5F8261D7B9695D93869`.
  - Nota: el hash del `.exe` del app-image puede no cambiar porque es el lanzador generado por jpackage; el contenido actualizado queda reflejado en el instalador y en el JAR principal.
  - API por defecto embebida: `https://recetas.167.233.213.242.sslip.io/`.
- Verificacion final:
  - `git diff --check` OK; solo avisos CRLF normales de Windows.
  - Artefactos listos para prueba manual: APK debug Android e instalador Windows v1.1.

### Retoma Claude Code 2026-07-12 noche — hallazgo de trabajo paralelo sin commit y plan de verificacion pendiente de autorizacion

Contexto: al retomar la sesion, `git status` revelo que Codex trabajo en paralelo fuera de esta conversacion (permitido por el protocolo multiagente del proyecto) y dejo implementados Sprint (3), Sprint (22), Sprint (10) y Sprint (11) mas dos regeneraciones de binarios — **todo sin commitear en el worktree**. Mi cierre de sesion anterior (seccion "Cierre de sesion Claude Code 2026-07-12 noche") quedo superado por estos hechos: no hacia falta "regenerar binarios y luego Sprint (3)", ambos y mas ya estaban hechos.

Verificacion propia minima ejecutada en esta sesion (solo lectura, sin build):
- Confirmado que los archivos nuevos son implementaciones reales, no stubs: `V18__add_recipe_creator.sql` (ALTER TABLE + FK + indice), paquete `rankings/` con 3 clases (`RecipeRankingController`, `RecipeRankingService`, `UserRecipeRankingResponse`), `UpdateFamilyMemberRequest.java` (18 lineas), `DesktopScroll.java` (24 lineas).
- `git status --short --branch`: rama `main` alineada con `origin/main` en `7b0a462`; ~45 archivos modificados y varios nuevos sin trackear (backend/Android/Desktop/iOS), ademas de `paraImplementar.txt` (preexistente, no tocar).

HALLAZGO CRITICO sin resolver, encontrado leyendo la propia trazabilidad que dejo Codex: `FamilyMemberControllerTest` — la suite que cubre password reset/temporal por OWNER/ADMIN, permisos, self/OWNER y fail-closed sin SMTP — **nunca se ejecuto con exito**. Bloqueada localmente por `DB_TEST_PASSWORD` vacio. Codex encontro despues (para el ranking, Sprint 11) el truco de cargar `DB_TEST_URL/USERNAME/PASSWORD` desde `herztner/recetas_app.env`, pero no volvio atras a rehacer el test de miembros con ese mismo metodo. Es decir: el codigo que permite resetear la password de otro usuario esta sin su gate de tests confirmado en ejecucion real.

Regla aplicada (`CLAUDE.md`, honestidad operativa): las validaciones que reporta Codex en su propia trazabilidad cuentan como **sesion anterior** para Claude Code como agente lider de esta sesion. No se declara nada de esto `cerrado`, `validado` ni `PASS` hasta verificarlo en esta sesion.

PLAN PROPUESTO AL USUARIO (comunicado en la conversacion, EN ESPERA DE AUTORIZACION — no ejecutado todavia, ni build ni tests ni commits de codigo en esta seccion):

1. Verificacion propia en esta sesion: recompilar/testear backend (incluyendo destrabar `FamilyMemberControllerTest` con el mismo metodo de env que uso Codex para el ranking), Android (`testDebugUnitTest assembleDebug`), Desktop (`mvn test`), iOS (`:composeApp:compileKotlinMetadata`).
2. `/VibeSec` sobre el diff completo — obligatorio: toca auth, ownership y password de miembros.
3. `/security-review` sobre el diff backend — confirmado disponible como skill invocable en esta sesion (a diferencia de las sesiones de Codex, que no lo tenian). Aplica directamente: Sprint (3) es el caso exacto que `CLAUDE.md` marca como obligatorio (endpoint que cambia password ajena).
4. Preparar bloques de auditoria en solo lectura para Codex y Gemini sobre estos 4 sprints antes de commitear (segunda opinion externa real, no autoevaluacion de Codex).
5. Si todo verifica: commits separados por sprint (no mezclar seguridad+UI+datos en uno solo) y consolidar esta seccion de `CONTINUAR.md` en una entrada unica (evitar historial completo, regla propia del archivo).
6. Despues: confirmar con el usuario si Sprint (12) copiar/exportar recetas entre familias (recomendado por Codex, backend ya tiene `copyRecipe` parcial) sigue siendo el siguiente, o si hay cambio de prioridad.

Opciones presentadas al usuario para autorizar el paso 1 en adelante:
- A: plan completo (1 a 5).
- B: solo verificacion + seguridad (1 a 3), sin commitear todavia.
- C: otra cosa, a definir por el usuario.

AUTORIZACION RECIBIDA (2026-07-12 noche): el usuario eligio **opcion A, plan completo (pasos 1 a 6)**. Registrado para el siguiente sprint; NADA ejecutado todavia por instruccion explicita del usuario ("solo dejalo registrado... no hagas nada todavia").

PUNTO EXACTO PARA RETOMAR: empezar por el paso 1 del plan (verificacion propia: backend compile+tests incluyendo destrabar `FamilyMemberControllerTest` con el metodo de env de `herztner/recetas_app.env`; Android `testDebugUnitTest assembleDebug`; Desktop `mvn test`; iOS `:composeApp:compileKotlinMetadata`), seguido de `/VibeSec`, `/security-review`, bloques de auditoria Codex/Gemini, y solo si todo verifica, commits separados por sprint. No tocar codigo, no hacer build, no commitear cambios de Codex hasta la proxima sesion/instruccion de arranque. `paraImplementar.txt` sigue sin trackear y no se toca.
- Siguiente accion funcional sigue siendo Sprint (12) exportar/copiar recetas entre grupos familiares, salvo que el usuario quiera primero instalar/probar estos binarios.

### Ejecucion del plan (pasos 1-6) y cierre de Sprint (3)/(22)/(10)/(11) - 2026-07-13 - Claude Code

Agente lider: Claude Code, en la misma sesion que dejo el plan registrado arriba. IDE se cerro inesperadamente mientras se esperaba respuesta de Codex/Gemini; los bloques de auditoria se reconstruyeron desde `CLAUDE.md`/`CONTINUAR.md` y se reenviaron; el usuario pego las respuestas ya generadas.

**Paso 1 - Verificacion propia:**
- Backend compile OK.
- Al ejecutar `FamilyMemberControllerTest` con env de `herztner/recetas_app.env`: 16/16 fallos, todos `register()` -> 409. Investigado: no es bug de `FamilyMemberControllerTest`, es sistemico. `mvn test` completo del backend dio **100/154 fallos**, mismo patron, en ~15 clases de test no relacionadas (Chat, User, Recipe, Sync, Stock...). Causa raiz: ningun test backend usa `@Transactional`/rollback ni limpia despues de si mismo; la BD compartida `recetas_familiares_test` (Hetzner) acumula usuarios/emails de ejecuciones anteriores (propias y de Codex) y los `register()` con email fijo chocan.
- Con autorizacion explicita del usuario, se purgo la BD de test (TRUNCATE de las 19 tablas de aplicacion, `flyway_schema_history` intacto) via utilidad JDBC ad-hoc (`PurgeTestDb.java`, con guard que rechaza cualquier URL que no contenga `recetas_familiares_test`). Tras la purga: **155/155 tests backend OK** (incluye integracion HTTP real contra Postgres). Se repurgo la BD al final de la sesion para dejarla limpia para la proxima.
- Android `gradlew.bat testDebugUnitTest assembleDebug`: OK (sin cambios en Android en este paso, build UP-TO-DATE).
- Desktop `mvn test`: OK, 21/21.
- iOS/KMP `:composeApp:compileKotlinMetadata`: OK.
- `git diff --check`: OK, solo avisos CRLF de Windows.
- Deuda de infraestructura de test detectada y NO resuelta (fuera de alcance de este sprint): la suite de integracion backend sigue siendo de un solo uso contra la BD compartida salvo que alguien la purgue antes de correrla. Recomendado para un sprint futuro: `@Transactional` en tests de integracion o `uniqueEmail()` (patron ya usado en `RecipeRankingControllerTest`) generalizado a todas las clases.

**Auditoria Codex + Gemini (solo lectura, sobre Sprint 3/22/10/11 sin commitear):**
- Gemini: Sprint 3 no toco iOS (ya documentado, no es hallazgo nuevo); ranking top10 Android/Desktop vs top5 iOS (decision de producto); recomienda confirmacion explicita en UI para "Definir temporal".
- Codex, hallazgo **CRITICO verificado por Claude Code contra el codigo real** (no autoevaluacion): `FamilyService.updateMember` (linea ~161) solo validaba que el objetivo fuera miembro activo de la familia del que llama, pero mutaba el `UserEntity` global (email/password). Un OWNER/ADMIN de familia A podia fijar password temporal + cambiar email de un usuario que tambien pertenece a familia B, y tomar esa cuenta -> acceso completo a familia B. Confirmado leyendo el codigo linea por linea, no solo por el reporte de Codex.
- Codex, hallazgos MEDIOS verificados: (1) enumeracion de email via 409 en `updateMember` (linea ~175) — cualquier OWNER/ADMIN puede probar emails arbitrarios; (2) ranking cuenta valoraciones de raters expulsados/borrados de la familia (`RecipeRatingJpaRepository` no filtra membresia activa del votante); (3) `AppDatabase.sq` (iOS) anadio columnas sin migracion `.sqm` — riesgo solo si hay install iOS previo en campo (hoy no lo hay); (4) `FamilyMemberRepository.kt` (iOS) `invite()` espera body JSON pero el backend devuelve 201 vacio — invitar miembro en iOS siempre falla (capturado por `runCatching`, no crashea), bug pre-existente no introducido por estos sprints.
- Codex, hallazgo BAJO verificado: Android no refresca el ranking al crear/borrar receta (si al crear/borrar valoracion).
- Security-review propio (Claude Code): 1 hallazgo adicional confirmado, mismo que el "Medio" de enumeracion de email de Codex (independiente, misma conclusion).

**Fix del critico (TDD, RED-GREEN verificado):**
- `FamilyService.updateMember`: si el usuario objetivo tiene mas de una familia activa (`familyMemberRepository.findByUser_IdAndDeletedFalse(targetUserId).size() > 1`), bloquea cambio directo de email y `SET_TEMPORARY` (400); `SEND_RESET` sigue permitido (no da acceso directo al admin, solo dispara email a la cuenta real).
- Tests nuevos: `FamilyServiceTest` (3 tests unitarios, mocks) + `FamilyMemberControllerTest` (1 test de integracion HTTP nuevo + 2 tests existentes corregidos porque su fixture — `register()` + invite — dejaba al objetivo en 2 familias por construccion, exactamente el escenario vulnerable que el fix bloquea).
- VibeSec: mensaje de error ajustado para no confirmar explicitamente al admin que el objetivo "pertenece a multiples familias" (minimizacion de informacion).
- Verificacion final: backend 155/155 (incluye los 17/17 de `FamilyMemberControllerTest` con el fix), Android/Desktop/iOS OK, `git diff --check` OK.
- **No corregido en esta sesion** (alcance explicito del usuario: "solo el critico"): enumeracion de email (Medio), rater inactivo en ranking (Medio), migracion SQLDelight iOS (Medio), `invite()` roto en iOS (Medio), refresh de ranking en Android tras crear/borrar receta (Bajo), paridad Sprint 3 en iOS (decision de producto pendiente).

**Commits (5, separados por sprint):**
- `80e81ae` — Sprint 3 backend (editar miembros/password) + fix critico de seguridad + tests TDD.
- `eec39c3` — Sprint 22 (scroll/responsive Desktop).
- `2cabbb2` — Sprint 10 (autor de receta visible, todas las plataformas).
- `2102e88` — Sprint 11 (ranking familiar).
- `f490c82` — capas cliente compartidas (Android/Desktop/iOS) que mezclan Sprint 3+10+11 en los mismos archivos/metodos (`ProfileScreen.kt`, `RecetasViewModel.kt`, `Repositories.kt`, `ApiDtos.kt` x2, `FamilyDtos.java`, `FamilyRepository.java`, `FamilyMembersView.java`, `ProfileView.java`, `RecipeListView.java`) — no separables por sprint sin partir hunks linea a linea dentro del mismo metodo/clase; decision explicita del usuario de agruparlos en vez de partir hunks a mano.
- Limitacion honesta: los commits de backend son independientemente compilables/testeables en el orden dado (3, 22, 10, 11 — 11 depende de 10 por `RecipeEntity.createdByUser`). Los commits de Android/Desktop/iOS **no se re-compilaron de forma independiente por commit intermedio** (solo se verifico el estado final con todo aplicado); si algun dia se hace `git bisect` o checkout de un commit intermedio de cliente, no hay garantia adicional mas alla de la logica de dependencia ya razonada.
- `CLAUDE.md` (modificado, ajeno a estos sprints) y `paraImplementar.txt` (sin trackear) quedan sin tocar, fuera de alcance.

**Seguridad/herramientas usadas:** VibeSec (skill, aplico el ajuste de mensaje), `/security-review` (skill, 1 hallazgo confirmado), Codex + Gemini (bloques IDE, solo lectura, hallazgos verificados contra codigo antes de integrar cualquier cambio), TDD (`superpowers:test-driven-development`) para el fix critico, `superpowers:executing-plans` para retomar el plan de la sesion anterior.

**Riesgo residual:** los hallazgos Medios/Bajos listados arriba quedan pendientes, documentados, sin fecha de sprint asignada todavia. La deuda de infraestructura de tests (BD compartida sin aislamiento) puede volver a bloquear la suite si se corre sin purgar `recetas_familiares_test` primero.

**Siguiente sprint recomendado:** decidir con el usuario si se ataca la deuda Medio/Bajo de esta auditoria antes de Sprint (12), o si Sprint (12) (copiar recetas entre familias) sigue siendo prioridad.
