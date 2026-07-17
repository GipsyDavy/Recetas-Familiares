# iOS copiar receta entre familias + Desktop crear familia — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** iOS puede copiar una receta de la familia activa a otra familia OWNER/ADMIN; Desktop puede crear una familia nueva adicional; iOS oculta "Crear familia" a quien el backend rechazaría.

**Architecture:** Sin cambios de backend. iOS: DTO + método en `RecipeRepository` + UI local en `RecipeDetailScreen` (icono en top bar + `ModalBottomSheet` + snackbar local), destinos calculados por función pura en `families/FamilyPermissions.kt`. Desktop: record DTO + método en `FamilyRepository` + botón en la toolbar admin de `FamilyMembersView` con callback de `MainWindow` para refrescar el selector de familia del sidebar.

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform + Ktor (MockEngine en tests), JavaFX + OkHttp/`ApiClient` propio (MockWebServer en tests), Maven, Gradle.

## Global Constraints

- Idioma de UI: español (textos exactos indicados en cada task).
- Spec: `docs/superpowers/specs/2026-07-17-ios-copy-recipe-desktop-create-family-design.md`.
- Los tests iOS **solo compilan** en Windows: RED = error de compilación, GREEN = `BUILD SUCCESSFUL` de `compileTestKotlinIosX64`. Nunca declarar que un test iOS "pasó".
- Comando compilación iOS (desde `ios/`): `.\gradlew.bat :composeApp:compileTestKotlinIosX64`.
- Comando final iOS (los 3 targets): `.\gradlew.bat :composeApp:compileTestKotlinIosX64 :composeApp:compileTestKotlinIosArm64 :composeApp:compileTestKotlinIosSimulatorArm64`.
- Desktop: `mvn -f desktop/pom.xml test` (surefire ya configura `useModulePath=false`).
- Nunca bloquear el hilo UI (JavaFX: hilos virtuales + `Platform.runLater`; Compose: corrutinas).
- La autoridad de permisos es el backend; la UI solo oculta acciones.
- `ApiClient` iOS NO usa `expectSuccess`: una respuesta 403 NO lanza excepción. `copyToFamily` debe comprobar `response.status.isSuccess()` explícitamente.
- No tocar `paraImplementar.txt` ni archivos no relacionados. Commits pequeños por task.

---

### Task 1: iOS — `CopyRecipeRequestDto` + `RecipeRepository.copyToFamily` (TDD)

**Files:**
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/network/ApiDtos.kt` (tras `CreateFamilyRequestDto`, línea ~39)
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/recipes/RecipeRepository.kt`
- Test (create): `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/recipes/RecipeRepositoryTest.kt`

**Interfaces:**
- Consumes: `ApiClient(session, engine = engine)`, `SessionStore` (var `familyId`, `accessToken`), `DatabaseDriverFactory()` (expect class sin args).
- Produces: `suspend fun RecipeRepository.copyToFamily(recipeId: String, targetFamilyId: String): Boolean` y `CopyRecipeRequestDto(targetFamilyId: String)` — usados por Task 3.

- [ ] **Step 1: Escribir el test que falla (no compila: `copyToFamily` no existe)**

Crear `RecipeRepositoryTest.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.recipes

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.network.ApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeRepositoryTest {

    private fun sessionWithFamily(familyId: String?) = SessionStore().apply {
        accessToken = "token"
        this.familyId = familyId
    }

    private fun repository(engine: MockEngine, session: SessionStore) =
        RecipeRepository(ApiClient(session, engine = engine), session, DatabaseDriverFactory())

    @Test
    fun `copyToFamily posts target family and returns true on 201`() = runTest {
        var body = ""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/families/f1/recipes/r1/copy", request.url.encodedPath)
            body = request.body.toByteArray().decodeToString()
            respond(
                content = """{"id":"r2","familyId":"f2","title":"Tortilla","createdAt":"2026-07-17T00:00:00Z","updatedAt":"2026-07-17T00:00:00Z","syncVersion":1,"deleted":false}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = sessionWithFamily("f1")

        val result = repository(engine, session).copyToFamily("r1", "f2")

        assertTrue(result)
        assertTrue(body.contains(""""targetFamilyId":"f2""""))
    }

    @Test
    fun `copyToFamily returns false on 403`() = runTest {
        val engine = MockEngine { respond("denied", HttpStatusCode.Forbidden) }
        val session = sessionWithFamily("f1")

        assertFalse(repository(engine, session).copyToFamily("r1", "f2"))
    }

    @Test
    fun `copyToFamily returns false without family session and makes no request`() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respond("", HttpStatusCode.OK) }
        val session = sessionWithFamily(null)

        assertFalse(repository(engine, session).copyToFamily("r1", "f2"))
        assertEquals(0, calls)
    }
}
```

- [ ] **Step 2: Verificar RED**

Run (desde `ios/`): `.\gradlew.bat :composeApp:compileTestKotlinIosX64`
Expected: FAIL — `unresolved reference: copyToFamily`.

- [ ] **Step 3: Implementación mínima**

En `ApiDtos.kt`, justo después de `CreateFamilyRequestDto` (línea ~39):

```kotlin
@Serializable
data class CopyRecipeRequestDto(val targetFamilyId: String)
```

En `RecipeRepository.kt`, tras `removeFavorite` (línea ~92). Imports nuevos: `io.ktor.http.isSuccess`, `org.gipsybuho.recetasfamiliares.network.CopyRecipeRequestDto` (el archivo ya usa `io.ktor.client.request.*` y `setBody` vía wildcard):

```kotlin
    /** Copia la receta a otra familia. Nota: ApiClient no usa expectSuccess,
     *  por eso el estado HTTP se comprueba explícitamente. */
    suspend fun copyToFamily(recipeId: String, targetFamilyId: String): Boolean {
        val familyId = session.familyId ?: return false
        return try {
            apiClient.http.post("api/v1/families/$familyId/recipes/$recipeId/copy") {
                setBody(CopyRecipeRequestDto(targetFamilyId))
            }.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }
```

- [ ] **Step 4: Verificar GREEN**

Run: `.\gradlew.bat :composeApp:compileTestKotlinIosX64`
Expected: `BUILD SUCCESSFUL` (solo compilación; no afirmar ejecución).

- [ ] **Step 5: Commit**

```bash
git add ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/network/ApiDtos.kt ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/recipes/RecipeRepository.kt ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/recipes/RecipeRepositoryTest.kt
git commit -m "feat(ios): RecipeRepository.copyToFamily contra el endpoint de copia"
```

---

### Task 2: iOS — funciones puras `copyTargets` y `canCreateFamily` (TDD)

**Files:**
- Create: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyPermissions.kt`
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepository.kt` (añadir `copyTargetFamilies`)
- Test (create): `ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyPermissionsTest.kt`

**Interfaces:**
- Consumes: `FamilyDto(id, name, role, avatarUrl)` de `network/ApiDtos.kt`.
- Produces: `fun copyTargets(families: List<FamilyDto>, activeFamilyId: String?): List<FamilyDto>` (Task 3) y `fun canCreateFamily(families: List<FamilyDto>): Boolean` (Task 4). Además `suspend fun FamilyMemberRepository.copyTargetFamilies(): List<FamilyDto>` (Task 3).

- [ ] **Step 1: Escribir el test que falla**

Crear `FamilyPermissionsTest.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.families

import org.gipsybuho.recetasfamiliares.network.FamilyDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FamilyPermissionsTest {

    private fun family(id: String, role: String?) = FamilyDto(id, "Familia $id", role, null)

    @Test
    fun `copyTargets excludes active family and non editor roles`() {
        val families = listOf(
            family("f1", "OWNER"),   // activa: fuera
            family("f2", "ADMIN"),   // destino válido
            family("f3", "MEMBER"),  // sin permiso de escritura: fuera
            family("f4", "OWNER"),   // destino válido
            family("f5", null)       // sin rol: fuera
        )

        val targets = copyTargets(families, "f1")

        assertEquals(listOf("f2", "f4"), targets.map { it.id })
    }

    @Test
    fun `copyTargets is empty when user only belongs to active family`() {
        assertTrue(copyTargets(listOf(family("f1", "OWNER")), "f1").isEmpty())
    }

    @Test
    fun `canCreateFamily requires editor role in some family`() {
        assertTrue(canCreateFamily(listOf(family("f1", "MEMBER"), family("f2", "ADMIN"))))
        assertFalse(canCreateFamily(listOf(family("f1", "MEMBER"))))
    }

    @Test
    fun `canCreateFamily allows user without families`() {
        assertTrue(canCreateFamily(emptyList()))
    }
}
```

- [ ] **Step 2: Verificar RED**

Run (desde `ios/`): `.\gradlew.bat :composeApp:compileTestKotlinIosX64`
Expected: FAIL — `unresolved reference: copyTargets`.

- [ ] **Step 3: Implementación mínima**

Crear `FamilyPermissions.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.families

import org.gipsybuho.recetasfamiliares.network.FamilyDto

private val EDITOR_ROLES = setOf("OWNER", "ADMIN")

/** Familias destino de una copia de receta: rol editor y distintas de la activa
 *  (mismo filtro que Android; el backend lo valida igualmente). */
fun copyTargets(families: List<FamilyDto>, activeFamilyId: String?): List<FamilyDto> =
    families.filter { it.id != activeFamilyId && it.role?.uppercase() in EDITOR_ROLES }

/** Criterio del backend (FamilyService.createFamily): crear familia exige rol
 *  editor en alguna membresía, o no tener ninguna. */
fun canCreateFamily(families: List<FamilyDto>): Boolean =
    families.isEmpty() || families.any { it.role?.uppercase() in EDITOR_ROLES }
```

En `FamilyMemberRepository.kt`, tras `createFamily` (línea ~37):

```kotlin
    suspend fun copyTargetFamilies(): List<FamilyDto> =
        copyTargets(families(), session.familyId)
```

- [ ] **Step 4: Verificar GREEN**

Run: `.\gradlew.bat :composeApp:compileTestKotlinIosX64`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyPermissions.kt ios/composeApp/src/commonTest/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyPermissionsTest.kt ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyMemberRepository.kt
git commit -m "feat(ios): reglas puras de destino de copia y permiso de crear familia"
```

---

### Task 3: iOS — UI copiar en `RecipeDetailScreen` + inyección del repositorio

**Files:**
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/recipes/RecipeDetailScreen.kt`
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/recipes/RecipeListScreen.kt` (firma ~línea 46 y llamada a `RecipeDetailScreen` ~línea 124)
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/ui/MainTabScreen.kt` (línea ~132)

**Interfaces:**
- Consumes: `RecipeRepository.copyToFamily(recipeId, targetFamilyId): Boolean` (Task 1), `FamilyMemberRepository.copyTargetFamilies(): List<FamilyDto>` (Task 2), `HapticFeedback.success()/error()/selection()`, `FamilyDto`.
- Produces: parámetro nuevo `familyRepository: FamilyMemberRepository? = null` en `RecipeDetailScreen` y `familyRepo: FamilyMemberRepository? = null` en `RecipeListScreen`.

Sin test automatizado de UI (no existe harness de UI iOS); verificación por compilación. La lógica con ramas quedó en Tasks 1-2.

- [ ] **Step 1: Inyección de dependencia**

`MainTabScreen.kt` línea ~132:

```kotlin
Tab.RECIPES  -> RecipeListScreen(repository = recipeRepo, syncRepo = syncRepo, stockRepo = stockRepo, familyRepo = familyMemberRepo)
```

`RecipeListScreen.kt` firma (~línea 46):

```kotlin
fun RecipeListScreen(
    repository: RecipeRepository,
    syncRepo: SyncRepository,
    stockRepo: StockRepository? = null,
    familyRepo: FamilyMemberRepository? = null
) {
```

Import nuevo en `RecipeListScreen.kt`: `org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository`.

Llamada a `RecipeDetailScreen` (~línea 124): añadir `familyRepository = familyRepo,` tras `repository = repository,`.

- [ ] **Step 2: UI en `RecipeDetailScreen.kt`**

Firma: añadir `familyRepository: FamilyMemberRepository? = null` tras `repository: RecipeRepository,`.

Imports nuevos:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ContentCopy
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository
import org.gipsybuho.recetasfamiliares.network.FamilyDto
```

Estado nuevo (junto a `isFavorite`, ~línea 54):

```kotlin
    var copyTargetFamilies by remember { mutableStateOf<List<FamilyDto>>(emptyList()) }
    var showCopySheet      by remember { mutableStateOf(false) }
    val snackbarHostState  = remember { SnackbarHostState() }
```

En el `LaunchedEffect(recipe.id)` existente (~línea 59), añadir al final (fallo silencioso → sin icono):

```kotlin
        familyRepository?.let { repo ->
            runCatching { copyTargetFamilies = repo.copyTargetFamilies() }
        }
```

En la Row del top bar, entre el IconButton de favorito y el de compartir (~línea 108):

```kotlin
            if (copyTargetFamilies.isNotEmpty()) {
                IconButton(onClick = { haptic.selection(); showCopySheet = true }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar a otra familia")
                }
            }
```

Al final del `Box` raíz (tras cerrar el `Column`, antes del cierre del `Box`):

```kotlin
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        if (showCopySheet) {
            ModalBottomSheet(onDismissRequest = { showCopySheet = false }) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Copiar a otra familia", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "La receta y sus fotos se copiarán a la familia elegida.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    copyTargetFamilies.forEach { family ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    showCopySheet = false
                                    scope.launch {
                                        val ok = repository.copyToFamily(recipe.id, family.id)
                                        if (ok) haptic.success() else haptic.error()
                                        snackbarHostState.showSnackbar(
                                            if (ok) "Receta y fotos copiadas a ${family.name}"
                                            else "No se pudo copiar la receta"
                                        )
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(family.name, style = MaterialTheme.typography.bodyLarge)
                                family.role?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
```

Nota: `Icons.Filled.ContentCopy`, `ModalBottomSheet`, `SnackbarHost` y `SnackbarHostState` vienen de `material3`/`material-icons` ya usados en el módulo (`androidx.compose.material3.*` está importado con wildcard en este archivo).

- [ ] **Step 3: Verificar compilación**

Run (desde `ios/`): `.\gradlew.bat :composeApp:compileTestKotlinIosX64`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/recipes/RecipeDetailScreen.kt ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/recipes/RecipeListScreen.kt ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/ui/MainTabScreen.kt
git commit -m "feat(ios): copiar receta a otra familia desde el detalle"
```

---

### Task 4: iOS — ocultar "Crear familia" sin rol editor

**Files:**
- Modify: `ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyListSheet.kt` (botón en líneas ~73-78)

**Interfaces:**
- Consumes: `canCreateFamily(families)` (Task 2), `families` ya coleccionado del `FamilyViewModel` en la línea 45.

La lógica está testeada en Task 2; aquí solo se aplica la condición (verificación por compilación).

- [ ] **Step 1: Aplicar condición**

En `FamilyListSheet.kt`, envolver el bloque del botón (líneas ~73-78):

```kotlin
            if (canCreateFamily(families)) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Crear familia")
                }
            }
```

(`canCreateFamily` es del mismo package `families`: sin import nuevo.)

- [ ] **Step 2: Verificar compilación**

Run (desde `ios/`): `.\gradlew.bat :composeApp:compileTestKotlinIosX64`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add ios/composeApp/src/commonMain/kotlin/org/gipsybuho/recetasfamiliares/families/FamilyListSheet.kt
git commit -m "fix(ios): ocultar crear familia a usuarios sin rol editor"
```

---

### Task 5: Desktop — `FamilyRepository.createFamily` (TDD con MockWebServer)

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/FamilyDtos.java`
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepository.java`
- Test (create): `desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepositoryHttpTest.java`

**Interfaces:**
- Consumes: `ApiClient(session, baseUrl)` + `api.post(path, body, Class)` (patrón de `inviteMember`), `AppSession(Preferences)`, MockWebServer (ya es dependencia de test — ver `ApiClientHttpTest`).
- Produces: `FamilyDtos.FamilyResponse createFamily(String name) throws ApiException` y record `FamilyDtos.CreateFamilyRequest(String name)` — usados por Task 6.

- [ ] **Step 1: Escribir el test que falla**

Crear `FamilyRepositoryHttpTest.java`:

```java
package org.gipsybuho.recetasfamiliares.data.repository;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyRepositoryHttpTest {

    private Preferences prefs;
    private AppSession session;
    private MockWebServer server;
    private ApiClient client;
    private FamilyRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        prefs = Preferences.userRoot().node("recetas-familiares-family-repo-test-" + UUID.randomUUID());
        prefs.clear();
        session = new AppSession(prefs);
        session.setTokens("token", "refresh");
        server = new MockWebServer();
        server.start();
        client = new ApiClient(session, server.url("/").toString());
        repository = new FamilyRepository(client, session);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) client.shutdown();
        if (server != null) server.shutdown();
        if (prefs != null && prefs.nodeExists("")) {
            prefs.removeNode();
            prefs.flush();
        }
    }

    @Test
    void createFamilyEnviaNombreYDevuelveFamiliaCreada() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"f9","name":"Nueva","role":"OWNER","avatarUrl":null}
                        """));

        FamilyDtos.FamilyResponse created = repository.createFamily("Nueva");

        assertEquals("f9", created.id());
        assertEquals("OWNER", created.role());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/v1/families", request.getPath());
        assertTrue(request.getBody().readUtf8().contains("\"name\":\"Nueva\""));
    }

    @Test
    void createFamilySinPermisoPropagaApiException403() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("denied"));

        ApiException error = assertThrows(ApiException.class, () -> repository.createFamily("Nueva"));

        assertEquals(403, error.getHttpStatus());
    }
}
```

- [ ] **Step 2: Verificar RED**

Run: `mvn -f desktop/pom.xml test -Dtest=FamilyRepositoryHttpTest`
Expected: FAIL de compilación — `cannot find symbol: method createFamily` / `CreateFamilyRequest`.

- [ ] **Step 3: Implementación mínima**

En `FamilyDtos.java`, tras el record `FamilyResponse` (línea ~8):

```java
    /** Matches backend CreateFamilyRequest: {name}. */
    public record CreateFamilyRequest(String name) {}
```

En `FamilyRepository.java`, tras `loadMyFamilies` (línea ~23):

```java
    /** Creates an additional family; the caller becomes its OWNER (backend enforces role and limits). */
    public FamilyDtos.FamilyResponse createFamily(String name) throws ApiException {
        return api.post("api/v1/families",
                new FamilyDtos.CreateFamilyRequest(name),
                FamilyDtos.FamilyResponse.class);
    }
```

- [ ] **Step 4: Verificar GREEN**

Run: `mvn -f desktop/pom.xml test -Dtest=FamilyRepositoryHttpTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/FamilyDtos.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepository.java desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/FamilyRepositoryHttpTest.java
git commit -m "feat(desktop): FamilyRepository.createFamily contra POST /families"
```

---

### Task 6: Desktop — botón "Crear familia" en miembros + refresco del sidebar

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/FamilyMembersView.java`
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java` (campo `familySelector` ~línea 284, `buildFamilySelector` ~línea 279, construcción `new FamilyMembersView(context)` línea ~156)

**Interfaces:**
- Consumes: `FamilyRepository.createFamily(String)` (Task 5), `ApiException.getHttpStatus()`, `DialogStyler.apply(dialog)` (patrón existente en `onChangeRole`), `loadFamilyChoices(selector, status)` existente en `MainWindow`.
- Produces: constructor `FamilyMembersView(AppContext context, Runnable onFamiliesChanged)`; método `MainWindow.reloadFamilyChoices()`.

Sin test de UI automatizado (no hay harness JavaFX); verificación: `mvn test` completo compila las vistas + prueba manual del usuario. La lógica de red quedó testeada en Task 5.

- [ ] **Step 1: `MainWindow` — status del selector como campo + método de recarga + callback**

En los campos (junto a `familySelector`):

```java
    private Label familySelectorStatus;
```

En `buildFamilySelector()` (~línea 290), tras crear el `Label status`:

```java
        familySelectorStatus = status;
```

Método nuevo (tras `loadFamilyChoices`, ~línea 348):

```java
    /** Recarga el selector de familias del sidebar (p.ej. tras crear una familia). */
    private void reloadFamilyChoices() {
        if (familySelector != null && familySelectorStatus != null) {
            loadFamilyChoices(familySelector, familySelectorStatus);
        }
    }
```

Línea ~156:

```java
            familyMembersView = new FamilyMembersView(context, this::reloadFamilyChoices);
```

- [ ] **Step 2: `FamilyMembersView` — constructor, botón y diálogo**

Constructor y campos:

```java
    private final Runnable onFamiliesChanged;
    private final Button createFamilyBtn = new Button("Crear familia");

    public FamilyMembersView(AppContext context, Runnable onFamiliesChanged) {
        this.context = context;
        this.onFamiliesChanged = onFamiliesChanged;
        build();
        refresh();
    }
```

En `build()`, junto a la configuración de los otros botones de la toolbar (~línea 90):

```java
        createFamilyBtn.getStyleClass().add("action-button-secondary");
        createFamilyBtn.setOnAction(e -> onCreateFamily());
        Tooltip.install(createFamilyBtn, new Tooltip("Crear una nueva familia; serás su propietario"));
```

Y añadirlo a la toolbar (línea ~95):

```java
        FlowPane toolbar = new FlowPane(8, 8, addBtn, editBtn, changeRoleBtn, removeBtn, createFamilyBtn);
```

Método nuevo (junto a `onChangeRole`):

```java
    private void onCreateFamily() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Crear familia");
        dialog.setHeaderText("Crear una nueva familia");
        dialog.setContentText("Nombre de la nueva familia:");
        DialogStyler.apply(dialog);

        Optional<String> result = dialog.showAndWait();
        result.map(String::trim).filter(name -> !name.isEmpty()).ifPresent(name -> {
            statusLabel.setText("Creando familia...");
            createFamilyBtn.setDisable(true);
            Thread.ofVirtual().start(() -> {
                try {
                    FamilyDtos.FamilyResponse created = context.getFamilyRepository().createFamily(name);
                    Platform.runLater(() -> {
                        createFamilyBtn.setDisable(false);
                        statusLabel.setText("Familia creada: " + created.name()
                                + ". Cámbiala desde el selector del menú lateral.");
                        if (onFamiliesChanged != null) onFamiliesChanged.run();
                    });
                } catch (ApiException ex) {
                    Platform.runLater(() -> {
                        createFamilyBtn.setDisable(false);
                        statusLabel.setText(createFamilyErrorMessage(ex));
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        createFamilyBtn.setDisable(false);
                        statusLabel.setText("No se pudo crear la familia.");
                    });
                }
            });
        });
    }

    private String createFamilyErrorMessage(ApiException ex) {
        return switch (ex.getHttpStatus()) {
            case 403 -> "Necesitas ser propietario o administrador para crear familias.";
            case 400 -> "No se pudo crear: revisa el nombre o has alcanzado el límite de familias.";
            default -> "No se pudo crear la familia.";
        };
    }
```

(`TextInputDialog` viene de `javafx.scene.control.*`, ya importado con wildcard.)

- [ ] **Step 3: Verificar compilación y suite**

Run: `mvn -f desktop/pom.xml test`
Expected: `BUILD SUCCESS`, 0 fallos (29 tests: 27 previos + 2 de Task 5).

- [ ] **Step 4: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/FamilyMembersView.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java
git commit -m "feat(desktop): crear familia desde la pantalla de miembros"
```

---

### Task 7: Validación integral + seguridad + documentación

**Files:**
- Modify: `CONTINUAR.md` (nueva entrada de trazabilidad)

- [ ] **Step 1: Compilación iOS de los 3 targets de test**

Run (desde `ios/`): `.\gradlew.bat :composeApp:compileTestKotlinIosX64 :composeApp:compileTestKotlinIosArm64 :composeApp:compileTestKotlinIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`. Documentar que el runtime de los tests queda SKIPPED en Windows (gate macOS pendiente).

- [ ] **Step 2: Suite Desktop completa**

Run: `mvn -f desktop/pom.xml test`
Expected: `BUILD SUCCESS`, 0 fallos.

- [ ] **Step 3: Seguridad**

Invocar `/VibeSec` sobre el diff del sprint (ownership multi-familia, sin secretos, mensajes de error sin datos sensibles). Registrar resultado. `security-review` si el análisis detecta superficie backend tocada (no debería: sprint solo clientes).

- [ ] **Step 4: Actualizar `CONTINUAR.md`**

Añadir entrada de trazabilidad: alcance, archivos, comandos y resultados reales, limitación iOS (tests compilados, no ejecutados), gate macOS pendiente (runtime de `RecipeRepositoryTest`, `FamilyPermissionsTest` y smoke de copia), prueba manual Desktop pendiente del usuario (diálogo crear familia + refresco selector).

- [ ] **Step 5: Commit de cierre**

```bash
git add CONTINUAR.md
git commit -m "docs: registra sprint copiar receta iOS y crear familia Desktop"
```

Push solo con autorización explícita del usuario (el push a `main` con cambios backend dispara deploy; este sprint no toca `backend/**`, pero la política de autorización se mantiene).
