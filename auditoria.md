# AUDITORÍA CONSOLIDADA — RECETAS FAMILIARES
**Fecha de auditoría:** 2026-05-31  
**Sprint auditado:** hasta Sprint 41 (HEAD: v1.1, commit 32d69b5)  
**Agentes participantes:** Claude Code · Codex · Gemini  

---

## 1. METODOLOGÍA Y ALCANCE COMPARATIVO

Esta auditoría consolida los hallazgos independientes de tres agentes IA (Claude, Codex, Gemini), cada uno operando con contexto completo del repositorio sin modificar ningún archivo. Se presenta la síntesis, la divergencia entre agentes y el plan de acción unificado.

**Leyenda de convergencia:**
- `[3/3]` Los tres agentes detectaron el problema
- `[2/3]` Dos agentes coincidieron
- `[1/3]` Hallazgo exclusivo de un solo agente (pero validado en síntesis)

---

## 2. ESTADO GENERAL DEL PROYECTO

Los tres agentes coinciden en la valoración global:

> El proyecto está en **estado MVP avanzado / beta técnica sólida**. La arquitectura es correcta, el backend es la parte más madura (76 tests, 0 fallos), Android está avanzado y funcional, Desktop es funcional con deuda técnica conocida de seguridad, e iOS está por detrás en paridad offline y funcionalidades de UI.

La cultura de seguridad es explícita y real (herramientas activas, auditorías periódicas, OWASP aplicado). La visión de producto es coherente y bien documentada. El sistema de 10 temas × 2 modos está implementado en las tres plataformas.

**Puntos fuertes unánimes:**
- Backend por capas correcto: DTOs explícitos, `ddl-auto=validate`, Flyway, OWASP aplicado
- JWT + refresh tokens opacos (SHA-256, rotación, BCrypt 12)
- Ownership familiar validado en todos los endpoints sensibles
- Anti-enumeración en invite (soft 201 para emails desconocidos y duplicados)
- Validación de magic bytes para imágenes (JPEG/PNG/WebP)
- EncryptedSharedPreferences (AES256_GCM) en Android
- Keychain (iOS) desde Sprint 18.4+
- Soft delete + syncVersion en todas las entidades sincronizables
- Sistema visual premium bien definido (10 temas, dark/light, skeletons, microanimaciones)

---

## 3. HALLAZGOS DE SEGURIDAD

### 🔴 CRÍTICO

#### SEC-1 [1/3 — Claude] JWT secret con fallback hardcodeado en git
**Archivo:** `backend/src/main/resources/application-dev.yml`
```yaml
secret: ${JWT_SECRET:dev-only-change-this-secret-32-bytes-minimum}
```
Si `JWT_SECRET` no está definida al arrancar en perfil `dev`, el backend usa un secreto conocido y público almacenado en git. Cualquier persona con acceso al repositorio puede forjar tokens JWT válidos.  
**Solución:** Eliminar el valor por defecto. Si `JWT_SECRET` no está definida, el arranque debe fallar con mensaje explícito.

#### SEC-2 [3/3] Desktop tokens en Windows Registry sin cifrar
**Archivo:** `desktop/src/main/java/.../core/AppSession.java`  
Access token y refresh token almacenados en `java.util.prefs` → Windows Registry (`HKCU\Software\JavaSoft\Prefs\recetas-familiares`) en texto plano. Cualquier proceso del usuario o acceso físico al PC expone la sesión.  
**Solución:** Migrar a Windows Credential Manager (DPAPI via JNA) para Windows, Keychain para macOS, libsecret para Linux.

#### SEC-3 [3/3] Imágenes familiares públicas sin autenticación
**Archivo:** `backend/src/main/java/.../security/SecurityConfig.java`, `WebMvcConfig.java`  
```java
.requestMatchers("/uploads/**").permitAll()
```
Todas las fotos subidas (recetas, avatares) son accesibles sin login. Las URLs con UUID dificultan la enumeración pero no la eliminan: los IDs aparecen en respuestas API, logs, y clipboard.  
**Solución (escalonada):**
1. MVP: `HandlerInterceptor` que valide JWT en `/uploads/**` con verificación de ownership de la familia.
2. Producción: S3/MinIO con URLs prefirmadas de corta duración.

---

### 🟠 ALTO

#### SEC-4 [2/3 — Claude + Codex] Rate limiter bypassable detrás de proxy inverso
**Archivo:** `backend/src/main/java/.../security/AuthRateLimitFilter.java`  
```java
String key = request.getRemoteAddr() + ":" + request.getRequestURI();
```
`getRemoteAddr()` devuelve la IP del proxy en despliegues con nginx/Cloudflare/ALB. El rate limit deja de funcionar: todos los usuarios comparten el mismo contador.  
**Solución:** Extracción proxy-aware con `X-Forwarded-For` y lista blanca de proxies de confianza, o `ForwardedHeaderFilter` de Spring.

#### SEC-5 [3/3] Clientes usan HTTP por defecto en configuración dev
- Android: `http://10.0.2.2:8080/` hardcodeado en `build.gradle.kts`
- Desktop: `http://localhost:8080/` en `ApiClient.java` como default
- iOS: `http://localhost:8080/` en `ApiClient.kt`  

**Solución:** Separar configuración `dev/prod` explícitamente. HTTPS obligatorio en cualquier configuración que no sea `localhost`/emulador. Bloquear cleartext fuera de debug.

#### SEC-6 [2/3 — Claude + Codex] iOS sin interceptor de refresh automático de tokens
**Archivo:** `ios/composeApp/src/commonMain/.../network/ApiClient.kt`  
iOS no tiene un interceptor que detecte 401, rote el refresh token y reintente. El usuario queda deslogueado silenciosamente cuando el access token expira.  
**Solución:** Plugin Ktor que intercepte 401, llame a `/auth/refresh`, actualice Keychain y reintente la petición original una vez. Misma lógica que `TokenRefreshAuthenticator.kt` en Android y `authenticate()` en Desktop.

#### SEC-7 [1/3 — Claude] Android TokenRefreshAuthenticator sin timeouts
**Archivo:** `android/app/src/main/java/.../data/remote/TokenRefreshAuthenticator.kt`  
```kotlin
private val client = OkHttpClient()
```
Sin `connectTimeout` ni `readTimeout`. Si el servidor no responde durante un refresh, el hilo queda bloqueado indefinidamente.  
**Solución:**
```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()
```

---

### 🟡 MEDIO

#### SEC-8 [1/3 — Claude] Logout no invalida el JWT de acceso
El logout revoca el refresh token pero el JWT de acceso sigue válido hasta su expiración (15 min). Tras un logout, el token puede reutilizarse por hasta 15 minutos.  
**Estado:** Tradeoff conocido de JWT stateless, mitigado por el TTL corto.  
**Solución (producción):** Blocklist Redis de tokens revocados o reducir TTL a 5 minutos.

#### SEC-9 [1/3 — Claude] JWT carece de claims `iss` y `aud`
Sin `issuer` ni `audience`, un token de staging es aceptado por prod si comparten el mismo secreto.  
**Solución:** `.issuer("recetas-familiares").audience().add("recetas-api")` en el builder y validación en `JwtService`.

#### SEC-10 [1/3 — Codex] Validación de URLs de fotos con regex manual
**Archivo:** `backend/src/main/java/.../photos/RecipePhotoService.java`  
Validación de URLs externas con regex casero para bloquear hosts privados. Aunque el backend no descarga las URLs (no hay SSRF directo), la lógica es frágil y puede evadirse.  
**Solución:** Allowlist de dominios confiables, o solo aceptar URLs generadas por el propio sistema de upload.

#### SEC-11 [1/3 — Gemini] Auditoría de dependencias no automatizada
No hay evidencia de Dependabot, Snyk u otra herramienta de auditoría continua de vulnerabilidades en dependencias de terceros.  
**Solución:** Integrar Dependabot en GitHub para alertas automáticas de CVEs en Maven/Gradle.

#### SEC-12 [1/3 — Gemini] Sin estrategia de logging de seguridad en producción
No se documenta cómo se detectan ataques, intentos de acceso fallidos o anomalías en producción.  
**Solución:** Spring Boot Actuator + logs de auditoría para eventos de autenticación, con integración a un sistema de alertas (ELK, Datadog o similar).

---

## 4. HALLAZGOS DE CODIFICACIÓN Y ARQUITECTURA

### 🟠 ALTO

#### COD-1 [2/3 — Claude + Codex] iOS push sync no implementado
**Archivo:** `ios/composeApp/src/commonMain/.../sync/SyncRepository.kt`  
iOS solo implementa `pullIncremental()`. Cualquier cambio creado en iOS (stock, notas) queda en SQLDelight local pero nunca se envía al servidor. Los demás miembros de la familia no ven los cambios de un miembro iOS.  
**Solución:** Implementar el push sync para iOS contra `/api/v1/families/{id}/sync/push`, replicando los 7 tipos de entidad que Android ya gestiona en `SyncWorker`.

#### COD-2 [3/3] iOS build roto en Windows (SQLDelight + Gradle 9.5.1)
**Archivo:** `ios/composeApp/build.gradle.kts`  
El build falla en Windows por incompatibilidad `DefaultArtifactPublicationSet`. Impide validar el código Kotlin multiplataforma en el entorno de desarrollo principal.  
**Solución:** Actualizar SQLDelight a `2.1.x` (compatible con Gradle 9.x) o fijar Gradle a `8.x` en el proyecto iOS.

#### COD-3 [2/3 — Claude + Codex] Android `baseSyncVersion` enviado como `null`
**Archivo:** `android/app/src/main/java/.../data/repository/Repositories.kt`  
El push offline envía `baseSyncVersion = null` en múltiples tipos de entidad. Esto reduce la capacidad del backend de detectar conflictos reales (servidor siempre gana, no hay Last Write Wins verificado).  
**Solución:** Persistir y enviar el `syncVersion` correcto de la última sincronización conocida para cada entidad.

#### COD-4 [1/3 — Claude] `lastActivityAt` en FamilyStats solo contempla recetas
**Archivo:** `backend/src/main/java/.../families/FamilyService.java`, línea 122  
Solo consulta `RecipeRepository` para calcular `lastActivityAt`. Notas, stock, shopping y menús no cuentan.  
**Solución:** Calcular el máximo entre todos los repositorios de entidades sincronizables, o añadir un campo `lastActivityAt` actualizable directamente en `FamilyEntity`.

#### COD-5 [1/3 — Claude] Endpoint sync/pull sin paginación ni límite de tamaño
En el primer sync (o tras un período largo sin conexión), `/api/v1/families/{id}/sync/pull` puede devolver miles de registros sin límite.  
**Solución:** Añadir paginación incremental (`?since=X&page=0&size=100`) con `hasMore: true/false` en la respuesta.

---

### 🟡 MEDIO

#### COD-6 [1/3 — Claude] Login no determinista para usuarios en múltiples familias
**Archivo:** `backend/src/main/java/.../auth/AuthService.java`  
`findFirstByUser_IdAndDeletedFalse` usa orden de base de datos. Con múltiples familias, el login siempre devuelve la "primera" de forma no predecible.  
**Estado:** Limitación MVP documentada en CONTINUAR.md.  
**Solución producción:** Devolver OWNER first, o implementar selección explícita de familia activa post-login.

#### COD-7 [1/3 — Claude] `CancellationException` swallowed en login Android
**Archivo:** `android/app/src/main/java/.../data/repository/Repositories.kt`, línea 78  
```kotlin
} catch (_: Exception) { }
```
Captura todas las excepciones incluyendo `CancellationException`, que en coroutines de Kotlin no debe capturarse silenciosamente.  
**Solución:** Re-throw `CancellationException` explícitamente dentro del catch.

#### COD-8 [1/3 — Codex] No hay tests de cliente (Android/Desktop/iOS)
El backend tiene 76 tests. Los tres clientes no tienen tests de repositorio, viewmodel ni red documentados.  
**Solución:** Añadir al menos tests unitarios de ViewModel y tests de repositorio con mocks HTTP para los flujos críticos de cada cliente.

#### COD-9 [1/3 — Claude] SyncService con 12 dependencias inyectadas
Constructor con 12 repositorios crea acoplamiento alto y dificultad de mantenimiento.  
**Solución:** Dividir en sub-servicios por dominio (RecipeSyncService, StockSyncService…) que SyncService orqueste.

#### COD-10 [1/3 — Claude] `FamilyService.parseRole` hace echo del input del usuario en el error
```java
throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + roleStr);
```
**Solución:** `"Invalid role value"` sin incluir el input.

#### COD-11 [1/3 — Codex] Inconsistencia documental: README.md vs CONTINUAR.md
`README.md` menciona "62 tests" pero `CONTINUAR.md` y `Resumen.md` reportan 76 tests.  
**Solución:** Actualizar `README.md` para reflejar el estado real del sprint actual.

#### COD-12 [1/3 — Claude] Instalador Desktop empaquetado con JDK-24 (no LTS)
El instalador `v1.1` fue compilado con JDK-24 (soporte 6 meses) en lugar de JDK-21 LTS (soporte hasta 2028).  
**Solución:** Recompilar el instalador con JDK-21 LTS antes del despliegue en producción.

---

## 5. HALLAZGOS DE INTERFAZ Y UX

### 🔴 CRÍTICO (rompe la identidad "premium")

#### UX-1 [1/3 — Gemini] Fuentes Nunito y Lato no implementadas con TTF en Android
Las fuentes premium están definidas en la escala tipográfica pero usan `FontFamily.Default` y `FontFamily.SansSerif` — sin archivos TTF reales. La app se siente genérica, no premium.  
**Solución:** Añadir archivos TTF a `res/font/` y actualizar `FontFamily` en `AppTheme.kt`.

#### UX-2 [1/3 — Gemini] NavigationBar en Android usa siempre iconos Outlined
Material3 exige `selectedIcon = Icons.Filled.*` para el tab activo y `icon = Icons.Outlined.*` para el inactivo. Actualmente ambos estados usan `Outlined`.  
**Solución:** Pasar `selectedIcon` con `Icons.Filled.*` correspondiente a cada tab.

---

### 🟠 ALTO

#### UX-3 [3/3] iOS — Falta paridad de funcionalidades con Android
iOS carece de:
- Buscador y filtros (dificultad, "Con mi stock") en `RecipeListScreen`
- Pista visual para el gesto de deslizamiento en `CookingScreen` (Android tiene un pill animado)
- Skeleton shimmer en `RecipeListScreen` (solo tiene `CircularProgressIndicator`)

**Solución:** Priorizar paridad funcional iOS/Android en Sprint 42. `RecipeListScreen`: `OutlinedTextField` + `FilterChip`. `CookingScreen`: `AnimatedVisibility` con pill centrado. `RecipeListScreen`: `SkeletonRecipeCard` shimmer.

#### UX-4 [1/3 — Gemini] Algoritmo de iniciales de avatar incorrecto
`displayName.take(2).uppercase()` toma los dos primeros caracteres del nombre, no la primera inicial de cada palabra.  
**Solución:**
```kotlin
displayName.split(" ").filter { it.isNotBlank() }.take(2)
    .map { it.first().uppercaseChar() }.joinToString("")
```

#### UX-5 [1/3 — Claude] Desktop sin pantalla de Perfil
Android tiene 6 tabs incluyendo perfil completo con stats, invitar miembro y logout. Desktop solo muestra nombre/avatar en el sidebar. No hay vista de perfil, acceso a estadísticas de familia ni botón de "Invitar miembro" visible para ADMIN/OWNER.  
**Solución:** Crear `ProfileView` en Desktop con las mismas secciones del `ProfileScreen` Android, integrando el endpoint `/api/v1/families/{id}/stats`.

#### UX-6 [1/3 — Claude] ProfileScreen Android no integra el endpoint `/stats`
La tarjeta de estadísticas muestra `totalRecipes` contado desde Room local y `lastActivityAt` desde Room. El endpoint `GET /api/v1/families/{id}/stats` ya devuelve `totalRecipes + totalMembers + totalStockItems + lastActivityAt` correctamente. `totalMembers` y `totalStockItems` no se muestran.  
**Estado:** Candidato Sprint 42 en Resumen.md.  
**Solución:** Llamar al endpoint `/stats` en el `ProfileViewModel` y mostrar las tres métricas.

#### UX-7 [1/3 — Gemini] Desktop sidebar sin información del usuario logueado
El sidebar no muestra quién está logueado (nombre, email, avatar), salvo una referencia puntual en la cabecera del sidebar sin diseño consistente.  
**Solución:** Añadir `HBox` en cabecera del sidebar con avatar circular (iniciales o foto), `displayName` y `email`.

---

### 🟡 MEDIO

#### UX-8 [1/3 — Gemini] Páginas de onboarding con emoji sobre fondo plano
Primera impresión de la app usa emoji sobre `Box` plano, sin gradiente ni imagen. Contrasta con la filosofía premium del producto.  
**Solución:** Añadir `Brush.verticalGradient` como fondo de cada página del onboarding.

#### UX-9 [1/3 — Gemini] Hápticos sin toggle global en preferencias
Los hápticos están activos siempre. El CLAUDE.md exige que sean desactivables en preferencias.  
**Solución:** Añadir `hapticsEnabled: Boolean` en `ThemePreference` / `OnboardingPreference` y un `Switch` en `SettingsScreen` (iOS) y `ThemePickerDialog` (Android).

#### UX-10 [1/3 — Gemini] iOS StockScreen muestra fechas de caducidad raw
Las fechas se muestran como `"YYYY-MM-DD"` sin indicador de urgencia visual.  
**Solución:** Calcular `daysLeft` y mostrar `"Caduca en N días"` con colores contextuales (verde > 7 días, amarillo 1–7, rojo < 1).

#### UX-11 [1/3 — Claude] CookingScreen Desktop sin shortcuts de teclado completos
`Escape` no cierra el modo cocina, `Space` no hace play/pause del temporizador, no hay ayuda visual de atajos.  
**Solución:** Añadir `Escape → stage.close()`, `Space → toggleTimer()` y una capa de ayuda visual con los atajos disponibles.

#### UX-12 [1/3 — Claude] Duración de FadeTransition inconsistente en Desktop
Tres valores distintos para el mismo tipo de transición de sidebar: 180ms, 200ms y 250ms (el CLAUDE.md especifica 250ms).  
**Solución:** Centralizar en una constante `SIDEBAR_FADE_DURATION_MS = 250` y aplicarla en todos los puntos.

#### UX-13 [1/3 — Claude + Gemini] Desktop sin onboarding de primera vez
Android e iOS tienen onboarding (Sprint 27). Desktop lleva directamente al login sin contexto.  
**Solución:** Pantalla de bienvenida simple con las 4–5 funciones principales al primer arranque.

---

## 6. DEUDA TÉCNICA DOCUMENTADA Y CONFIRMADA

Hallazgos ya conocidos en los `.md` del proyecto, confirmados como reales en la auditoría:

| ID | Descripción | Plataforma | Prioridad |
|----|-------------|------------|-----------|
| DT-1 | Desktop tokens en Windows Registry sin cifrar | Desktop | Alta |
| DT-2 | iOS push sync no implementado (read-only) | iOS | Alta |
| DT-3 | iOS build roto en Windows (SQLDelight + Gradle) | iOS | Alta |
| DT-4 | Sync pull sin paginación | Backend | Media |
| DT-5 | Login devuelve primera familia (no determinista) | Backend | Media |
| DT-6 | `lastActivityAt` solo contempla recetas | Backend | Media |
| DT-7 | `security-crypto 1.1.0-alpha06` en Android | Android | Baja |
| DT-8 | `compressImage` lógica en ViewModel Android | Android | Baja |
| DT-9 | Logout no invalida JWT de acceso (TTL 15m mitiga) | Backend | Baja |
| DT-10 | README.md muestra "62 tests" en lugar de 76 | Docs | Baja |

---

## 7. ANÁLISIS COMPARATIVO DE AGENTES

### Convergencia alta (3/3 agentes)
- Desktop tokens sin cifrar
- Imágenes públicas sin auth
- HTTP por defecto en dev
- iOS sync incompleto (no push)
- iOS build roto en Windows
- iOS paridad de UI inferior a Android

### Convergencia media (2/3 agentes)
- Rate limiter bypassable detrás de proxy (Claude + Codex)
- iOS sin interceptor de refresh 401 (Claude + Codex)
- baseSyncVersion null en Android push (Claude + Codex)
- iOS pista visual en CookingScreen (Claude + Gemini)
- Desktop sin onboarding (Claude + Gemini)

### Hallazgos únicos más relevantes
| Agente | Hallazgo exclusivo clave |
|--------|--------------------------|
| Claude | JWT secret fallback hardcodeado en git (crítico) |
| Claude | Rate limiter bypassable (alto) |
| Claude | `lastActivityAt` solo recetas (medio) |
| Claude | `CancellationException` swallowed en Android |
| Claude | Instalador con JDK-24 no LTS |
| Codex | baseSyncVersion null en push Android |
| Codex | Sin tests en clientes Android/Desktop/iOS |
| Codex | Inconsistencia documental README (62 vs 76 tests) |
| Codex | SSRF URL validation concern |
| Gemini | Fuentes TTF no implementadas (crítico visual) |
| Gemini | NavigationBar icons siempre Outlined |
| Gemini | Algoritmo iniciales de avatar incorrecto |
| Gemini | Hápticos sin toggle en preferencias |
| Gemini | Dependency auditing (Snyk/Dependabot) ausente |
| Gemini | Sin logging de seguridad en producción |

---

## 8. PLAN DE ACCIÓN PRIORIZADO

### Antes de cualquier despliegue en producción real (BLOQUEANTES)

| # | ID | Descripción | Esfuerzo estimado |
|---|-----|-------------|-------------------|
| 1 | SEC-1 | Eliminar JWT secret fallback hardcodeado en `application-dev.yml` | 15 min |
| 2 | SEC-3 | Autenticar `/uploads/**` con verificación de ownership | 2–3h |
| 3 | SEC-2 | Desktop: migrar tokens a Windows Credential Manager / Keychain | 3–4h |
| 4 | SEC-4 | Rate limiter: extracción IP proxy-aware con X-Forwarded-For | 1h |
| 5 | SEC-7 | Android TokenRefreshAuthenticator: añadir timeouts | 15 min |
| 6 | COD-12 | Recompilar instalador Desktop con JDK-21 LTS | 30 min |

### Sprint 42 (próximo sprint recomendado)

| # | ID | Descripción |
|---|-----|-------------|
| 7 | UX-1 | Implementar fuentes TTF Nunito/Lato en Android |
| 8 | UX-2 | Corregir iconos NavigationBar (Filled/Outlined por estado) |
| 9 | UX-6 | ProfileScreen Android: integrar endpoint `/stats` |
| 10 | UX-3 | iOS RecipeListScreen: buscador + filtros + skeleton shimmer |
| 11 | SEC-6 | iOS: interceptor Ktor para refresh automático en 401 |
| 12 | COD-3 | Android: corregir baseSyncVersion en push offline |
| 13 | COD-7 | Android: re-throw CancellationException en login |
| 14 | COD-4 | Backend: lastActivityAt multi-entidad en FamilyStats |

### Sprint 43 (deuda técnica media)

| # | ID | Descripción |
|---|-----|-------------|
| 15 | COD-1 | iOS: implementar push sync (mismos 7 tipos que Android) |
| 16 | COD-2 | iOS: resolver SQLDelight + Gradle 9.5.1 |
| 17 | UX-5 | Desktop: crear pantalla de Perfil completa |
| 18 | UX-9 | Hápticos: toggle global en preferencias de todas las plataformas |
| 19 | COD-5 | Backend: paginación en endpoint sync/pull |
| 20 | SEC-9 | JWT: añadir claims `iss` y `aud` |
| 21 | SEC-11 | Integrar Dependabot para auditoría continua de dependencias |
| 22 | COD-8 | Añadir tests de ViewModel y Repository en Android/Desktop/iOS |

### Backlog (bajo impacto)

| # | ID | Descripción |
|---|-----|-------------|
| 23 | UX-4 | Corregir algoritmo de iniciales de avatar |
| 24 | UX-7 | Desktop sidebar: añadir info de usuario logueado |
| 25 | UX-10 | iOS StockScreen: fechas con indicador de urgencia visual |
| 26 | UX-11 | CookingScreen Desktop: shortcuts Escape y Space |
| 27 | UX-12 | Desktop: centralizar duración FadeTransition en constante |
| 28 | UX-13 | Desktop: añadir onboarding de primera vez |
| 29 | UX-8 | Onboarding: añadir gradiente de fondo (más premium) |
| 30 | SEC-8 | Blocklist Redis para tokens revocados en logout |
| 31 | COD-6 | Login: selección de familia activa (más allá del MVP) |
| 32 | COD-9 | Refactorizar SyncService en sub-servicios por dominio |
| 33 | COD-11 | Actualizar README.md con número correcto de tests (76) |
| 34 | SEC-12 | Logging de seguridad: eventos de auth y anomalías |

---

## 9. CONCLUSIÓN FINAL

El proyecto "Recetas Familiares" está bien construido para ser un MVP familiar privado. La base de código es limpia, la arquitectura es correcta y la cultura de seguridad es real y activa. Los tres agentes auditores coinciden en que **no hay puertas traseras ni secretos de producción críticos hardcodeados** (excepto el fallback del JWT secret en dev, identificado únicamente por Claude).

Los problemas se concentran en **seis áreas de mejora**:

1. **Seguridad crítica de transición a producción:** JWT secret fallback, imágenes sin auth, tokens Desktop en claro. Estos tres puntos son **bloqueantes para producción pública**.
2. **Paridad iOS:** Sync push, interceptor de refresh, búsqueda/filtros en RecipeListScreen, skeleton shimmer. iOS es significativamente menos completo que Android.
3. **Identidad visual premium:** Las fuentes Nunito/Lato no están realmente implementadas con TTF en Android. La percepción "premium" que el producto busca depende de este detalle.
4. **Funcionalidad Desktop:** Sin pantalla de perfil, sin onboarding, con tokens en Windows Registry.
5. **Exactitud de datos:** `lastActivityAt` incompleto, `baseSyncVersion` null en push, sync pull sin paginación.
6. **Calidad y robustez:** Sin tests de cliente, `CancellationException` swallowed, `CancellationException` en refresh authenticator sin timeouts.

Abordando los 6 puntos bloqueantes listados en "Antes de producción" se puede alcanzar un estado de despliegue seguro. El Sprint 42 cubre la mayoría de los puntos de mayor impacto visual y funcional. El Sprint 43 cierra la paridad iOS y la deuda técnica restante.

---

*Documento generado por síntesis de auditorías independientes de Claude Code, Codex y Gemini — 2026-05-31*
