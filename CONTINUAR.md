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
- Backend: 76 tests, 0 fallos en la ultima validacion documentada.
- Android: funcional, con offline-first y UI avanzada.
- Desktop: funcional, instalador Windows v1.1 generado, ajustes como vista central.
- iOS: funcional parcialmente, pero con deuda de paridad y build en Windows.

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

Riesgos pendientes a verificar:
- Paginacion de `sync/pull` (COD-5).
- `lastActivityAt` multi-entidad (COD-4).
- Ownership por familia en `/uploads/**` (servir via controller con lookup).

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

Riesgos pendientes a verificar:
- Consumo completo del endpoint `/stats` en perfil (UX-6).
- Fuentes TTF reales si se exige identidad premium completa (UX-1).

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

Riesgos pendientes a verificar:
- Perfil completo y stats familiares.
- Onboarding de primer arranque.
- Shortcuts completos en modo cocina.
- Recompilar instalador con JDK 21 LTS antes de produccion (incluira DLLs de JNA).

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

Riesgos pendientes a verificar:
- Build en Windows por SQLDelight/Gradle.
- Push sync completo.
- Paridad con Android: busqueda, filtros, skeletons y UX de listas.

Funcionalidad futura documentada:
- Chat familiar despues de estabilizar backend y al menos un cliente.
- Evitar abrir este frente antes de resolver refresh 401, push sync y paridad basica.
- Tiempo real y adjuntos en iOS deben llegar despues de tener contrato backend estable, previews definidos y estrategia de push notifications.

---

## 8. Bloqueantes Recomendados Para Sprint Siguiente

Sprint 42 (2026-07-02) cerro los puntos 1-8 de la lista anterior (ver seccion 10).

Prioridad propuesta para Sprint 43:

1. Backend: paginar `sync/pull` (COD-5).
2. Backend: ownership por familia en `/uploads/**` (hoy: solo autenticado; URLs UUID no adivinables).
3. Backend: `lastActivityAt` multi-entidad (COD-4).
4. UX: completar stats familiares en Android/Desktop (UX-6).
5. iOS: validar en macOS interceptor 401 y Coil autenticado (implementados sin compilar).
6. Producto: especificar chat familiar por fases sin implementarlo todavia: texto/emojis en tiempo real, imagenes, videos y push notifications.

Antes de arrancar sprint, revisar `auditoria.md` para IDs `SEC-*`, `COD-*`, `UX-*` y comprobar vigencia en codigo.

---

## 9. Validacion Esperada

Ajustar comandos al modulo tocado y al build real del repositorio.

Backend:

```bash
./gradlew test
./gradlew build
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
