# iOS Multi-Familia (listar, cambiar, crear) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar a iOS lo que Android/Desktop ya tienen — pertenecer a varias familias, cambiar cuál está activa, y crear una familia nueva — sin tocar backend.

**Architecture:** Los repositorios iOS ya leen `session.familyId` dinámicamente en cada llamada (20 sitios en 7 archivos), así que cambiar ese valor ya redirige el resto de la app sin tocarlos. Se añade: 2 campos a un DTO existente + 1 DTO nuevo, 3 métodos nuevos en un repositorio ya existente (`FamilyMemberRepository`), un `FamilyViewModel` nuevo (primera clase de este tipo en iOS — plain class con `CoroutineScope` inyectado, sin librería de lifecycle nueva), y una hoja modal (`ModalBottomSheet`, patrón iOS) accesible desde Ajustes.

**Tech Stack:** Kotlin Multiplatform (iOS targets), Compose Multiplatform, Ktor Client (+ `ktor-client-mock` para tests), kotlinx.coroutines (+ `kotlinx-coroutines-test`), kotlin.test.

---

## ⚠️ Limitación de entorno — leer antes de ejecutar

Esta máquina (Windows, sin macOS/Xcode) **solo puede compilar la metadata común de
iOS** (`./gradlew :composeApp:compileKotlinMetadata`). No puede compilar ni ejecutar
código para los targets reales (`iosX64`/`iosArm64`/`iosSimulatorArm64`), lo que
incluye **ejecutar los tests**. Esto es así para todo el proyecto, no solo este plan
("iOS bloqueado" es una limitación conocida y documentada desde sprints anteriores).

Decisión explícita del usuario (2026-07-13): los tests de este plan se **escriben
completos y correctos por diseño**, pero **no se ejecutan** en esta sesión. La
verificación de cada paso de test es a nivel de **compilación de metadata**, no de
ejecución:

- "RED" en este plan significa: *el código de producción aún no existe → el test
  referencia un símbolo que no compila* (error de compilación real, verificable).
- "GREEN" en este plan significa: *`compileKotlinMetadata` (o el task equivalente de
  metadata para `commonTest`, confirmar nombre exacto en el Task 1) termina
  `BUILD SUCCESSFUL`* — **no** significa que la lógica se haya ejecutado ni que las
  aserciones se hayan comprobado en tiempo de ejecución.

No declarar en ningún informe de cierre que estos tests "pasaron" — di literalmente
"compilan, no ejecutados en esta máquina".

---

## File Structure

- Modificar `ios/gradle/libs.versions.toml` — 2 entradas nuevas de catálogo (`ktor-client-mock`, `kotlinx-coroutines-test`).
- Modificar `ios/composeApp/build.gradle.kts` — añadir bloque `commonTest.dependencies`.
- Modificar `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/network/ApiClient.kt` — parámetro `engine` inyectable.
- Modificar `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/network/ApiDtos.kt` — `FamilyDto.avatarUrl` + `CreateFamilyRequestDto`.
- Modificar `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepository.kt` — `families()`, `createFamily()`, `setActiveFamily()`.
- Crear `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyViewModel.kt`.
- Crear `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyListSheet.kt`.
- Modificar `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/ui/SettingsScreen.kt` — entrada "Familias".
- Modificar `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/ui/MainTabScreen.kt` — instanciar `FamilyViewModel`.
- Crear `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/network/ApiDtosTest.kt`.
- Crear `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepositoryTest.kt`.
- Crear `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyViewModelTest.kt`.

Fuera de alcance (ver spec): copiar receta, fix de `invite()`, `createFamily` en Desktop.

---

### Task 1: Bootstrap de `commonTest` + inyección de engine en `ApiClient`

**Files:**
- Modify: `ios/gradle/libs.versions.toml`
- Modify: `ios/composeApp/build.gradle.kts`
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/network/ApiClient.kt`
- Test: `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/network/ApiClientTest.kt`

- [ ] **Step 1: Añadir catálogo de dependencias de test**

En `ios/gradle/libs.versions.toml`, junto a las líneas existentes de `ktor-*` y
`kotlinx-coroutines-core` (líneas 13-18), añadir:

```toml
ktor-client-mock                   = { module = "io.ktor:ktor-client-mock",                        version.ref = "ktor" }
kotlinx-coroutines-test            = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test",    version.ref = "coroutines" }
```

- [ ] **Step 2: Añadir el source set `commonTest`**

En `ios/composeApp/build.gradle.kts`, dentro del bloque `sourceSets { ... }`, después de
`iosMain.dependencies { ... }` (línea 47):

```kotlin
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
```

- [ ] **Step 3: Escribir el test que exige el parámetro `engine` (RED de compilación)**

Crear `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/network/ApiClientTest.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiClientTest {

    @Test
    fun `http client uses the injected mock engine instead of the platform engine`() = runTest {
        var requestedPath: String? = null
        val engine = MockEngine { request ->
            requestedPath = request.url.encodedPath
            respond(content = "{}", status = HttpStatusCode.OK)
        }
        val session = SessionStore().apply { accessToken = "token" }
        val client = ApiClient(session, engine = engine)

        client.http.get("api/v1/ping")

        assertEquals("/api/v1/ping", requestedPath)
    }
}
```

- [ ] **Step 4: Confirmar el nombre exacto del task de metadata de test y comprobar el fallo de compilación**

`ApiClient` todavía no acepta `engine`, así que este test no debe compilar.

Run: `./gradlew :composeApp:tasks --all` (dentro de `ios/`) y localizar el task de
compilación de metadata para el source set `commonTest` (candidato más probable:
`compileTestKotlinMetadata`; si no existe con ese nombre exacto, usar el que aparezca
listado para `commonTest`).

Run: `./gradlew :composeApp:compileTestKotlinMetadata` (ajustar nombre si el paso
anterior encontró otro)
Expected: FALLA con `Unresolved reference: engine` (o equivalente) — confirma que el
test referencia código que aún no existe.

- [ ] **Step 5: Añadir el parámetro `engine` a `ApiClient`**

Modificar `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/network/ApiClient.kt`.
Añadir import:

```kotlin
import io.ktor.client.engine.HttpClientEngine
```

Reemplazar la clase completa (líneas 20-38 del archivo actual, constructor + `val http`)
por:

```kotlin
class ApiClient(
    private val session: SessionStore,
    private val serverUrlPreference: ServerUrlPreference = ServerUrlPreference(),
    engine: HttpClientEngine? = null
) {

    val baseUrl: String
        get() = serverUrlPreference.baseUrl

    val http: HttpClient = if (engine != null) {
        HttpClient(engine) { configureClient() }
    } else {
        HttpClient { configureClient() }
    }

    private fun HttpClientConfig<*>.configureClient() {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
        // SEC-6: adjunta Bearer y ante 401 refresca el token y reintenta una vez
        install(Auth) {
            bearer {
                sendWithoutRequest { request ->
                    isApiOrigin(request.url)
                }
                loadTokens {
                    session.accessToken?.let { access ->
                        BearerTokens(access, session.refreshToken)
                    }
                }
                refreshTokens {
                    if (!isApiOrigin(response.call.request.url)) {
                        return@refreshTokens null
                    }
                    val refresh = session.refreshToken
                    if (refresh.isNullOrBlank()) {
                        session.clear()
                        return@refreshTokens null
                    }
                    val auth = runCatching {
                        client.post("api/v1/auth/refresh") {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequestDto(refresh))
                        }.body<AuthResponseDto>()
                    }.getOrNull()
                    if (auth == null) {
                        session.clear()
                        null
                    } else {
                        session.accessToken = auth.accessToken
                        session.refreshToken = auth.refreshToken
                        BearerTokens(auth.accessToken, auth.refreshToken)
                    }
                }
            }
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }
```

El resto de la clase (`resetAuthTokens()`, `isApiOrigin(...)` x2, línea 91-103 del
archivo actual) queda igual, sin cambios.

- [ ] **Step 6: Confirmar que ahora compila (GREEN de compilación)**

Run: `./gradlew :composeApp:compileTestKotlinMetadata` (o el nombre confirmado en el Step 4)
Expected: `BUILD SUCCESSFUL`. Ejecutar también `./gradlew :composeApp:compileKotlinMetadata`
para confirmar que el cambio de producción no rompe nada existente.
Expected: `BUILD SUCCESSFUL`.

No se ejecuta el test (ver limitación de entorno arriba) — solo se confirma que
compila.

- [ ] **Step 7: Commit**

```bash
git add ios/gradle/libs.versions.toml ios/composeApp/build.gradle.kts ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/network/ApiClient.kt ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/network/ApiClientTest.kt
git commit -m "feat(ios): bootstrap commonTest y motor Ktor inyectable en ApiClient"
```

---

### Task 2: `FamilyDto.avatarUrl` + `CreateFamilyRequestDto`

**Files:**
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/network/ApiDtos.kt`
- Test: `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/network/ApiDtosTest.kt`

- [ ] **Step 1: Escribir el test (RED de compilación — falta `avatarUrl` y `CreateFamilyRequestDto`)**

Crear `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/network/ApiDtosTest.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `FamilyDto decodes avatarUrl from backend shape`() {
        val raw = """{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":"https://x/avatar.png"}"""
        val dto = json.decodeFromString<FamilyDto>(raw)
        assertEquals("https://x/avatar.png", dto.avatarUrl)
    }

    @Test
    fun `FamilyDto tolerates missing avatarUrl`() {
        val raw = """{"id":"f1","name":"Casa","role":"OWNER"}"""
        val dto = json.decodeFromString<FamilyDto>(raw)
        assertNull(dto.avatarUrl)
    }

    @Test
    fun `CreateFamilyRequestDto encodes name field`() {
        val encoded = json.encodeToString(CreateFamilyRequestDto("Casa Nueva"))
        assertEquals("""{"name":"Casa Nueva"}""", encoded)
    }
}
```

- [ ] **Step 2: Comprobar el fallo de compilación**

Run: `./gradlew :composeApp:compileTestKotlinMetadata`
Expected: FALLA — `avatarUrl` no existe en `FamilyDto` y `CreateFamilyRequestDto` no
existe.

- [ ] **Step 3: Añadir el campo y el DTO**

En `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/network/ApiDtos.kt`,
reemplazar la línea 31:

```kotlin
@Serializable
data class FamilyDto(val id: String, val name: String, val role: String? = null)
```

por:

```kotlin
@Serializable
data class FamilyDto(
    val id: String,
    val name: String,
    val role: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class CreateFamilyRequestDto(val name: String)
```

- [ ] **Step 4: Confirmar que compila**

Run: `./gradlew :composeApp:compileTestKotlinMetadata`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/network/ApiDtos.kt ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/network/ApiDtosTest.kt
git commit -m "feat(ios): avatarUrl en FamilyDto y nuevo CreateFamilyRequestDto"
```

---

### Task 3: `FamilyMemberRepository` — `families()`, `createFamily()`, `setActiveFamily()`

**Files:**
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepository.kt`
- Test: `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepositoryTest.kt`

- [ ] **Step 1: Escribir los tests (RED de compilación — los 3 métodos no existen)**

Crear `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepositoryTest.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.families

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.FamilyDto
import kotlin.test.Test
import kotlin.test.assertEquals

class FamilyMemberRepositoryTest {

    private fun sessionWithFamily(familyId: String?) = SessionStore().apply {
        accessToken = "token"
        this.familyId = familyId
    }

    @Test
    fun `families lists families from backend`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/families", request.url.encodedPath)
            respond(
                content = """[{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":null},{"id":"f2","name":"Casa2","role":"MEMBER","avatarUrl":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = sessionWithFamily("f1")
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)

        val result = repository.families()

        assertEquals(2, result.size)
        assertEquals("f2", result[1].id)
    }

    @Test
    fun `createFamily posts name and returns created family`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/families", request.url.encodedPath)
            respond(
                content = """{"id":"f3","name":"Nueva","role":"OWNER","avatarUrl":null}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = sessionWithFamily("f1")
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)

        val created = repository.createFamily("Nueva")

        assertEquals("f3", created.id)
    }

    @Test
    fun `setActiveFamily persists id and role without any network call`() {
        var calls = 0
        val engine = MockEngine { calls++; respond("", HttpStatusCode.OK) }
        val session = sessionWithFamily("f1")
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)

        repository.setActiveFamily(FamilyDto("f2", "Casa2", "MEMBER", null))

        assertEquals("f2", session.familyId)
        assertEquals("MEMBER", session.familyRole)
        assertEquals(0, calls)
    }
}
```

- [ ] **Step 2: Comprobar el fallo de compilación**

Run: `./gradlew :composeApp:compileTestKotlinMetadata`
Expected: FALLA — `families()`, `createFamily()` y `setActiveFamily()` no existen en
`FamilyMemberRepository`.

- [ ] **Step 3: Implementar los 3 métodos**

En `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepository.kt`,
añadir import:

```kotlin
import org.gipsybuho.recetasfamiliares.network.CreateFamilyRequestDto
import org.gipsybuho.recetasfamiliares.network.FamilyDto
```

Y añadir estos métodos dentro de la clase, después de `invite(...)` (línea 22 del
archivo actual):

```kotlin
    suspend fun families(): List<FamilyDto> =
        apiClient.http.get("api/v1/families").body()

    suspend fun createFamily(name: String): FamilyDto =
        apiClient.http.post("api/v1/families") {
            setBody(CreateFamilyRequestDto(name))
        }.body()

    fun setActiveFamily(family: FamilyDto) {
        session.familyId = family.id
        session.familyRole = family.role
    }
```

- [ ] **Step 4: Confirmar que compila**

Run: `./gradlew :composeApp:compileTestKotlinMetadata`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepository.kt ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepositoryTest.kt
git commit -m "feat(ios): FamilyMemberRepository lista, crea y activa familias"
```

---

### Task 4: `FamilyViewModel`

**Files:**
- Create: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyViewModel.kt`
- Test: `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyViewModelTest.kt`

- [ ] **Step 1: Escribir los tests (RED de compilación — la clase no existe)**

Crear `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyViewModelTest.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.families

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FamilyViewModelTest {

    @Test
    fun `loadFamilies selects the family matching session familyId as active`() = runTest {
        val engine = MockEngine {
            respond(
                content = """[{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":null},{"id":"f2","name":"Casa2","role":"MEMBER","avatarUrl":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = SessionStore().apply { accessToken = "token"; familyId = "f2" }
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)
        val viewModel = FamilyViewModel(repository, session, this)

        viewModel.loadFamilies()
        advanceUntilIdle()

        assertEquals("f2", viewModel.activeFamily.value?.id)
        assertEquals(2, viewModel.families.value.size)
    }

    @Test
    fun `loadFamilies falls back to the first family when session familyId is stale`() = runTest {
        val engine = MockEngine {
            respond(
                content = """[{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = SessionStore().apply { accessToken = "token"; familyId = "familia-ya-no-existe" }
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)
        val viewModel = FamilyViewModel(repository, session, this)

        viewModel.loadFamilies()
        advanceUntilIdle()

        assertEquals("f1", viewModel.activeFamily.value?.id)
    }

    @Test
    fun `switchActiveFamily rejects an id not present in loaded families`() = runTest {
        val engine = MockEngine {
            respond(
                content = """[{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = SessionStore().apply { accessToken = "token"; familyId = "f1" }
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)
        val viewModel = FamilyViewModel(repository, session, this)
        viewModel.loadFamilies()
        advanceUntilIdle()

        viewModel.switchActiveFamily("unknown")

        assertEquals("f1", session.familyId)
        assertEquals("No se pudo cambiar de familia", viewModel.errorMessage.value)
    }

    @Test
    fun `switchActiveFamily updates session and active family for a valid id`() = runTest {
        val engine = MockEngine {
            respond(
                content = """[{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":null},{"id":"f2","name":"Casa2","role":"MEMBER","avatarUrl":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = SessionStore().apply { accessToken = "token"; familyId = "f1" }
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)
        val viewModel = FamilyViewModel(repository, session, this)
        viewModel.loadFamilies()
        advanceUntilIdle()

        viewModel.switchActiveFamily("f2")

        assertEquals("f2", session.familyId)
        assertEquals("MEMBER", session.familyRole)
        assertEquals("f2", viewModel.activeFamily.value?.id)
        assertNull(viewModel.errorMessage.value)
    }
}
```

- [ ] **Step 2: Comprobar el fallo de compilación**

Run: `./gradlew :composeApp:compileTestKotlinMetadata`
Expected: FALLA — `FamilyViewModel` no existe.

- [ ] **Step 3: Implementar `FamilyViewModel`**

Crear `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyViewModel.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.families

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.FamilyDto

class FamilyViewModel(
    private val repository: FamilyMemberRepository,
    private val session: SessionStore,
    private val scope: CoroutineScope
) {
    private val _families = MutableStateFlow<List<FamilyDto>>(emptyList())
    val families: StateFlow<List<FamilyDto>> = _families.asStateFlow()

    private val _activeFamily = MutableStateFlow<FamilyDto?>(null)
    val activeFamily: StateFlow<FamilyDto?> = _activeFamily.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadFamilies() {
        scope.launch {
            runCatching { repository.families() }
                .onSuccess { list ->
                    _families.value = list
                    val currentId = session.familyId
                    _activeFamily.value = list.firstOrNull { it.id == currentId } ?: list.firstOrNull()
                }
                .onFailure { _errorMessage.value = "No se pudieron cargar las familias" }
        }
    }

    fun switchActiveFamily(familyId: String) {
        if (familyId == session.familyId) return
        val target = _families.value.firstOrNull { it.id == familyId }
        if (target == null) {
            _errorMessage.value = "No se pudo cambiar de familia"
            return
        }
        repository.setActiveFamily(target)
        _activeFamily.value = target
        _errorMessage.value = null
    }

    fun createFamily(name: String) {
        scope.launch {
            runCatching { repository.createFamily(name) }
                .onSuccess { loadFamilies() }
                .onFailure { _errorMessage.value = "No se pudo crear la familia" }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
```

- [ ] **Step 4: Confirmar que compila**

Run: `./gradlew :composeApp:compileTestKotlinMetadata`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyViewModel.kt ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyViewModelTest.kt
git commit -m "feat(ios): FamilyViewModel para listar/cambiar/crear familia"
```

---

### Task 5: UI — hoja modal de familias + entrada en Ajustes

Sin tests (UI Compose Multiplatform — la ejecución/inspección visual real también
requiere simulador iOS, no disponible en esta máquina; verificación limitada a
`compileKotlinMetadata` + revisión manual del código).

**Files:**
- Create: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyListSheet.kt`
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/ui/SettingsScreen.kt`
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/ui/MainTabScreen.kt`

- [ ] **Step 1: Crear la hoja modal `FamilyListSheet`**

Crear `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyListSheet.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.families

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gipsybuho.recetasfamiliares.network.FamilyDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyListSheet(
    viewModel: FamilyViewModel,
    onDismiss: () -> Unit
) {
    val families by viewModel.families.collectAsState()
    val activeFamily by viewModel.activeFamily.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadFamilies() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Tus familias", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(families) { family ->
                    FamilyRow(
                        family = family,
                        isActive = family.id == activeFamily?.id,
                        onClick = { viewModel.switchActiveFamily(family.id) }
                    )
                }
            }
            errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Crear familia")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCreateDialog) {
        CreateFamilyDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createFamily(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun FamilyRow(family: FamilyDto, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(family.name, style = MaterialTheme.typography.bodyLarge)
            family.role?.let { role ->
                Text(
                    role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isActive) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Familia activa",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CreateFamilyDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear familia") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Crear")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

- [ ] **Step 2: Añadir la entrada "Familias" en `SettingsScreen`**

En `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/ui/SettingsScreen.kt`:

Añadir import (junto a la línea 54):

```kotlin
import org.gipsybuho.recetasfamiliares.families.FamilyListSheet
import org.gipsybuho.recetasfamiliares.families.FamilyViewModel
```

Añadir parámetro nuevo a la firma de `SettingsScreen` (línea 73, después de
`familyMemberRepository`):

```kotlin
    familyMemberRepository: FamilyMemberRepository? = null,
    familyViewModel: FamilyViewModel? = null
```

Añadir estado nuevo junto a `showInviteDialog` (línea 76):

```kotlin
    var showFamilyList by androidx.compose.runtime.remember { mutableStateOf(false) }
```

Añadir el bloque de la hoja modal junto al bloque `if (showInviteDialog...)` (después
de la línea 119):

```kotlin

    if (showFamilyList && familyViewModel != null) {
        FamilyListSheet(
            viewModel = familyViewModel,
            onDismiss = { showFamilyList = false }
        )
    }
```

Añadir la fila "Familias" justo antes del bloque `if (isAdmin && familyMemberRepository != null)`
(antes de la línea 256), mismo estilo `OutlinedButton` que "Invitar miembro":

```kotlin
        if (familyViewModel != null) {
            OutlinedButton(
                onClick = { showFamilyList = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Familias")
            }
            Spacer(Modifier.height(8.dp))
        }

```

- [ ] **Step 3: Instanciar `FamilyViewModel` en `MainTabScreen` y pasarlo a `SettingsScreen`**

En `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/ui/MainTabScreen.kt`:

Añadir import (junto a la línea 19):

```kotlin
import org.gipsybuho.recetasfamiliares.families.FamilyViewModel
```

Añadir, después de `val familyMemberRepo = remember { FamilyMemberRepository(apiClient, session) }`
(línea 59):

```kotlin
    val scope = rememberCoroutineScope()
    val familyViewModel = remember { FamilyViewModel(familyMemberRepo, session, scope) }
```

Añadir el parámetro a la llamada a `SettingsScreen(...)` (después de
`familyMemberRepository = familyMemberRepo`, línea 123):

```kotlin
                    familyMemberRepository = familyMemberRepo,
                    familyViewModel = familyViewModel
```

- [ ] **Step 4: Confirmar que compila**

Run: `./gradlew :composeApp:compileKotlinMetadata`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyListSheet.kt ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/ui/SettingsScreen.kt ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/ui/MainTabScreen.kt
git commit -m "feat(ios): pantalla para listar, cambiar y crear familia desde Ajustes"
```

---

## Self-Review (hecho al escribir este plan)

**Cobertura del spec:** SessionStore (ya existía, sin tarea — documentado). DTOs → Task 2.
Repositorio → Task 3. ViewModel → Task 4. UI adaptada a iOS (`ModalBottomSheet`, no
`AlertDialog` de pantalla completa) → Task 5. Bootstrap de tests → Task 1. Ningún punto
del spec queda sin tarea.

**Placeholders:** ninguno — todo paso de código trae el código completo real, ninguna
referencia a "similar a la Task N" sin repetir el código.

**Consistencia de tipos:** `FamilyDto(id, name, role, avatarUrl)` usado igual en Task 2/3/4/5.
`FamilyMemberRepository(apiClient, session)` — mismo orden de constructor que el actual
(`FamilyMemberRepository.kt:13-16`) en todos los tests. `FamilyViewModel(repository,
session, scope)` consistente en Task 4 y en su uso en Task 5 Step 3.

**Riesgo residual que no resuelve este plan:** ningún test se ejecuta de verdad en esta
máquina (ver limitación de entorno). Verificación visual/manual de la hoja modal en un
simulador real no es posible sin macOS. Ambos quedan como riesgo aceptado explícito del
usuario, no como deuda oculta.
