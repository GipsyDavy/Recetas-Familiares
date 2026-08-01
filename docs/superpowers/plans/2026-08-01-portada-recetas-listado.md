# Portada de receta en los listados — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que las cards de listado de recetas muestren la foto de portada en Android y en Desktop, que hoy solo se ve en el detalle.

**Architecture:** El backend calcula la portada y la publica como campo aditivo `coverThumbnailUrl` en `RecipeResponse`, con una consulta por lotes que evita N+1. Desktop consume ese campo y lo pinta con un cargador de imagen autenticado nuevo. Android **no** usa el campo: deriva la portada de las fotos que Room ya tiene sincronizadas, para funcionar offline sin subir versión de esquema.

**Tech Stack:** Spring Boot 3.5 + JPA + PostgreSQL (backend), Kotlin + Compose + Coil 3 + Room (Android), JavaFX + OkHttp (Desktop). Tests: JUnit 5 + MockMvc (backend), JUnit 4 + mockk (Android), JUnit 5 + MockWebServer (Desktop).

**Spec:** `docs/superpowers/specs/2026-08-01-portada-recetas-listado-design.md`

## Global Constraints

- **Regla de portada, idéntica en todos los sitios:** `thumbnailUrl` de la foto activa (`deleted = false`) de menor `position`; si esa foto no tiene `thumbnailUrl` (null o en blanco), se usa su `url`; si no hay fotos activas, `null`.
- **Sin migración de base de datos.** La consulta usa el índice existente `ix_recipe_photos_recipe_active (recipe_id, deleted, position)`.
- **Sin migración de esquema Room.** Android no persiste el campo nuevo.
- **Cero N+1:** una consulta de portadas por página, nunca una por receta.
- **Toda consulta de portadas filtra por `familyId` además de por ids de receta.** No es redundante: impide que un id de otra familia devuelva una URL ajena.
- **Cambio de contrato aditivo.** Ningún campo existente cambia de nombre, tipo ni nulabilidad.
- **Animaciones (`CLAUDE.md` §17):** aparición de imagen con `Crossfade` en Compose y `FadeTransition` de 150ms en JavaFX. Ninguna animación bloquea el hilo de UI.
- **iOS fuera de alcance.** No se toca `ios/`.
- **Antes del commit de cierre del sprint:** `/VibeSec`, `/security-review` y `pwsh -NoProfile -File scripts/security/run-security-scan.ps1`.

**Cargar credenciales antes de cualquier `mvn test` de backend** (el fichero tiene finales CRLF; sin el `tr` la contraseña se carga con un `\r` y la autenticación falla):

```bash
cd "C:/Users/GipsyDavy/MAVEN/Recetas Familiares"
set -a && . <(tr -d '\r' < herztner/recetas_app.env) && set +a
```

---

### Task 1: Backend — `coverThumbnailUrl` en los endpoints REST de recetas

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/recipes/RecipeResponse.java`
- Create: `backend/src/main/java/org/gipsybuho/recetasfamiliares/photos/RecipeCoverProjection.java`
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/photos/RecipePhotoRepository.java`
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/recipes/RecipeService.java:56-67` (listRecipes) y `:224-241` (toResponse)
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/recipes/RecipeControllerTest.java`

**Interfaces:**
- Consumes: nada de tareas anteriores.
- Produces:
  - `RecipeResponse.coverThumbnailUrl()` → `String` (nullable), último componente del record.
  - `RecipeCoverProjection` con `String getRecipeId()`, `String getThumbnailUrl()`, `String getUrl()`.
  - `RecipePhotoRepository.findCoverCandidates(String familyId, Collection<String> recipeIds)` → `List<RecipeCoverProjection>`, ordenada por `recipeId` y `position` ascendente.
  - `RecipeService` privado: `Map<String,String> coverUrlsByRecipeId(String familyId, List<String> recipeIds)` y `String coverUrlOf(RecipeEntity recipe)`.

- [ ] **Step 1: Escribir el test que falla**

Añadir a `RecipeControllerTest`, junto a los tests existentes:

```java
    @Test
    void listAndDetailExposeCoverFromLowestPositionPhoto() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipes-cover"), "Familia Portada");
        MvcResult created = createRecipe(user, "Receta con portada").andReturn();
        String recipeId = read(created, "id");

        // Se crean en orden inverso a proposito: la portada es la de position menor,
        // no la primera insertada.
        addPhotoMetadata(user, recipeId, 2, "https://cdn.test/segunda.jpg", "https://cdn.test/segunda-thumb.jpg");
        addPhotoMetadata(user, recipeId, 1, "https://cdn.test/primera.jpg", "https://cdn.test/primera-thumb.jpg");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].coverThumbnailUrl").value("https://cdn.test/primera-thumb.jpg"));

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverThumbnailUrl").value("https://cdn.test/primera-thumb.jpg"));
    }

    @Test
    void coverFallsBackToFullUrlAndIsNullWithoutPhotos() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipes-cover-fallback"), "Familia Fallback");

        MvcResult withoutPhotos = createRecipe(user, "Receta sin fotos").andReturn();
        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}",
                        user.familyId(), read(withoutPhotos, "id"))
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverThumbnailUrl").doesNotExist());

        MvcResult withoutThumb = createRecipe(user, "Receta sin thumbnail").andReturn();
        String recipeId = read(withoutThumb, "id");
        addPhotoMetadata(user, recipeId, 1, "https://cdn.test/solo-original.jpg", null);

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverThumbnailUrl").value("https://cdn.test/solo-original.jpg"));
    }

    @Test
    void coverIsNotLeakedAcrossFamilies() throws Exception {
        RegisteredUser owner = register(uniqueEmail("cover-owner"), "Familia Duena");
        RegisteredUser outsider = register(uniqueEmail("cover-outsider"), "Familia Ajena");

        MvcResult created = createRecipe(owner, "Receta privada").andReturn();
        String recipeId = read(created, "id");
        addPhotoMetadata(owner, recipeId, 1, "https://cdn.test/privada.jpg", "https://cdn.test/privada-thumb.jpg");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes", outsider.familyId())
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private void addPhotoMetadata(
            RegisteredUser user,
            String recipeId,
            int position,
            String url,
            String thumbnailUrl
    ) throws Exception {
        String thumbnailJson = thumbnailUrl == null ? "null" : "\"" + thumbnailUrl + "\"";
        mockMvc.perform(post("/api/v1/families/{familyId}/recipes/{recipeId}/photos",
                        user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"position": %d, "url": "%s", "thumbnailUrl": %s}
                                """.formatted(position, url, thumbnailJson)))
                .andExpect(status().isCreated());
    }
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

```bash
set -a && . <(tr -d '\r' < herztner/recetas_app.env) && set +a
mvn -f backend/pom.xml test -Dtest=RecipeControllerTest
```

Esperado: FALLA al compilar el test, porque `coverThumbnailUrl` no existe todavía en el JSON. Si compilara, fallaría el `jsonPath` con "No value at JSON path".

- [ ] **Step 3: Añadir el campo al record**

En `RecipeResponse.java`, añadir como **último** componente (el orden importa: hay llamadas posicionales al constructor):

```java
public record RecipeResponse(
        String id,
        String familyId,
        String title,
        String description,
        Integer servings,
        Integer prepMinutes,
        Integer cookMinutes,
        RecipeDifficulty difficulty,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted,
        String createdByUserId,
        String createdByDisplayName,
        String coverThumbnailUrl
) {
}
```

- [ ] **Step 4: Crear la proyección**

`backend/src/main/java/org/gipsybuho/recetasfamiliares/photos/RecipeCoverProjection.java`:

```java
package org.gipsybuho.recetasfamiliares.photos;

/** Proyeccion ligera para calcular portadas sin cargar entidades completas. */
public interface RecipeCoverProjection {

    String getRecipeId();

    String getThumbnailUrl();

    String getUrl();
}
```

- [ ] **Step 5: Añadir la consulta por lotes al repositorio**

En `RecipePhotoRepository.java`, añadir el import `java.util.Collection` y el método:

```java
    /**
     * Candidatas a portada de varias recetas en una sola consulta. El filtro por familia
     * no es redundante con el IN: impide que un id de receta ajena devuelva una URL de
     * otra familia. Va sobre ix_recipe_photos_recipe_active (recipe_id, deleted, position).
     */
    @Query("""
            SELECT p.recipe.id AS recipeId,
                   p.thumbnailUrl AS thumbnailUrl,
                   p.url AS url
            FROM RecipePhotoEntity p
            WHERE p.recipe.family.id = :familyId
              AND p.recipe.id IN :recipeIds
              AND p.deleted = false
            ORDER BY p.recipe.id ASC, p.position ASC
            """)
    List<RecipeCoverProjection> findCoverCandidates(
            @Param("familyId") String familyId,
            @Param("recipeIds") Collection<String> recipeIds
    );
```

- [ ] **Step 6: Calcular la portada en el servicio**

En `RecipeService.java`, añadir imports `java.util.Collection`, `java.util.LinkedHashMap`, `java.util.Map` y `org.gipsybuho.recetasfamiliares.photos.RecipeCoverProjection`.

Sustituir `listRecipes` (líneas 56-67) por:

```java
    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> listRecipes(String familyId, String userId, int page, int size) {
        requireMembership(familyId, userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<RecipeEntity> recipes = recipeRepository.findByFamily_IdAndDeletedFalse(familyId, pageable);
        Map<String, String> covers = coverUrlsByRecipeId(
                familyId,
                recipes.getContent().stream().map(RecipeEntity::getId).toList()
        );
        return new PageResponse<>(
                recipes.getContent().stream()
                        .map(recipe -> toResponse(recipe, covers.get(recipe.getId())))
                        .toList(),
                recipes.getNumber(),
                recipes.getSize(),
                recipes.getTotalElements(),
                recipes.getTotalPages()
        );
    }
```

Sustituir `toResponse` (líneas 224-241) por estos cuatro métodos:

```java
    private RecipeResponse toResponse(RecipeEntity recipe) {
        return toResponse(recipe, coverUrlOf(recipe));
    }

    private RecipeResponse toResponse(RecipeEntity recipe, String coverThumbnailUrl) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getFamilyId(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.getServings(),
                recipe.getPrepMinutes(),
                recipe.getCookMinutes(),
                recipe.getDifficulty(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt(),
                recipe.getSyncVersion(),
                recipe.isDeleted(),
                recipe.getCreatedByUserId(),
                recipe.getCreatedByDisplayName(),
                coverThumbnailUrl
        );
    }

    /** Portada de una sola receta. Para listas usar coverUrlsByRecipeId: esto seria N+1. */
    private String coverUrlOf(RecipeEntity recipe) {
        return photoRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipe.getId()).stream()
                .findFirst()
                .map(RecipeService::preferredCoverUrl)
                .orElse(null);
    }

    private Map<String, String> coverUrlsByRecipeId(String familyId, Collection<String> recipeIds) {
        if (recipeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> covers = new LinkedHashMap<>();
        for (RecipeCoverProjection candidate : photoRepository.findCoverCandidates(familyId, recipeIds)) {
            // La consulta llega ordenada por position ascendente, asi que la primera fila
            // de cada receta es su portada. putIfAbsent conserva esa y descarta el resto.
            covers.putIfAbsent(candidate.getRecipeId(), preferredCoverUrl(candidate));
        }
        return covers;
    }

    private static String preferredCoverUrl(RecipeCoverProjection photo) {
        return photo.getThumbnailUrl() != null && !photo.getThumbnailUrl().isBlank()
                ? photo.getThumbnailUrl()
                : photo.getUrl();
    }

    private static String preferredCoverUrl(RecipePhotoEntity photo) {
        return photo.getThumbnailUrl() != null && !photo.getThumbnailUrl().isBlank()
                ? photo.getThumbnailUrl()
                : photo.getUrl();
    }
```

- [ ] **Step 7: Ejecutar los tests y verificar que pasan**

```bash
mvn -f backend/pom.xml test -Dtest=RecipeControllerTest
```

Esperado: PASS. Si falla la compilación de `SyncService`, es lo esperado — lo arregla la Task 2. En ese caso, ejecutar antes la Task 2 y volver aquí.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/recipes/RecipeResponse.java \
        backend/src/main/java/org/gipsybuho/recetasfamiliares/recipes/RecipeService.java \
        backend/src/main/java/org/gipsybuho/recetasfamiliares/photos/RecipeCoverProjection.java \
        backend/src/main/java/org/gipsybuho/recetasfamiliares/photos/RecipePhotoRepository.java \
        backend/src/test/java/org/gipsybuho/recetasfamiliares/recipes/RecipeControllerTest.java
git commit -m "feat(backend): expone la portada de receta en los endpoints de recetas"
```

---

### Task 2: Backend — portada también en `sync/pull` y `sync/push`

**Files:**
- Modify: `backend/src/main/java/org/gipsybuho/recetasfamiliares/sync/SyncService.java:121-124` (pagedPull), `:244` (push) y `:465` (toRecipeResponse)
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/sync/SyncControllerTest.java`

**Interfaces:**
- Consumes: `RecipeResponse` con 15 componentes y `RecipePhotoRepository.findCoverCandidates(...)` de la Task 1.
- Produces: nada nuevo hacia tareas posteriores.

**Nota de diseño que debe quedar en el código:** el pull entrega recetas cuyo `updatedAt` cambió. Si solo cambia una *foto*, la fila de receta no cambia y su `coverThumbnailUrl` no se refresca hasta que la receta se toque. Es aceptable porque ningún cliente depende de esa vía: Android usa sus propias fotos y Desktop reconstruye su caché desde el listado REST.

- [ ] **Step 1: Escribir el test que falla**

Añadir a `SyncControllerTest`, adaptando los helpers de registro que ya use esa clase:

```java
    @Test
    void pullExposesRecipeCoverThumbnail() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-cover"), "Familia Sync Portada");
        MvcResult created = createRecipe(user, "Receta sincronizada").andReturn();
        String recipeId = read(created, "id");

        mockMvc.perform(post("/api/v1/families/{familyId}/recipes/{recipeId}/photos",
                        user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"position": 1, "url": "https://cdn.test/sync.jpg",
                                 "thumbnailUrl": "https://cdn.test/sync-thumb.jpg"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes[0].coverThumbnailUrl").value("https://cdn.test/sync-thumb.jpg"));
    }
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

```bash
mvn -f backend/pom.xml test -Dtest=SyncControllerTest
```

Esperado: FALLA con "No value at JSON path \"$.recipes[0].coverThumbnailUrl\"", o error de compilación si la Task 1 ya cambió el record.

- [ ] **Step 3: Batch de portadas en el pull**

En `SyncService.pagedPull`, sustituir las líneas 122-124 por:

```java
        Slice<RecipeEntity> recipeEntities = fetchSlice(
                p -> recipeRepository.findByFamily_IdAndUpdatedAtAfter(familyId, since, p),
                RecipeEntity::getUpdatedAt, Function.identity(), limit);
        Map<String, String> recipeCovers = coverUrlsByRecipeId(
                familyId,
                recipeEntities.items().stream().map(RecipeEntity::getId).toList()
        );
        Slice<RecipeResponse> recipes = new Slice<>(
                recipeEntities.items().stream()
                        .map(recipe -> toRecipeResponse(recipe, recipeCovers.get(recipe.getId())))
                        .toList(),
                recipeEntities.nextSince()
        );
```

Añadir los imports que falten: `java.util.Map`, `java.util.LinkedHashMap`, `java.util.Collection`, `java.util.function.Function` y `org.gipsybuho.recetasfamiliares.photos.RecipeCoverProjection`.

- [ ] **Step 4: Adaptar `toRecipeResponse` y el push**

Sustituir `toRecipeResponse` (línea 465) por la pareja de métodos, más el helper de lotes:

```java
    private RecipeResponse toRecipeResponse(RecipeEntity recipe) {
        String cover = photoRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipe.getId()).stream()
                .findFirst()
                .map(photo -> photo.getThumbnailUrl() != null && !photo.getThumbnailUrl().isBlank()
                        ? photo.getThumbnailUrl()
                        : photo.getUrl())
                .orElse(null);
        return toRecipeResponse(recipe, cover);
    }

    private RecipeResponse toRecipeResponse(RecipeEntity recipe, String coverThumbnailUrl) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getFamilyId(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.getServings(),
                recipe.getPrepMinutes(),
                recipe.getCookMinutes(),
                recipe.getDifficulty(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt(),
                recipe.getSyncVersion(),
                recipe.isDeleted(),
                recipe.getCreatedByUserId(),
                recipe.getCreatedByDisplayName(),
                coverThumbnailUrl
        );
    }

    private Map<String, String> coverUrlsByRecipeId(String familyId, Collection<String> recipeIds) {
        if (recipeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> covers = new LinkedHashMap<>();
        for (RecipeCoverProjection candidate : photoRepository.findCoverCandidates(familyId, recipeIds)) {
            String url = candidate.getThumbnailUrl() != null && !candidate.getThumbnailUrl().isBlank()
                    ? candidate.getThumbnailUrl()
                    : candidate.getUrl();
            covers.putIfAbsent(candidate.getRecipeId(), url);
        }
        return covers;
    }
```

El bucle de push (línea 244) no cambia: sigue llamando a `toRecipeResponse(recipe)`, que ahora resuelve la portada con una consulta por receta. Es aceptable porque el push está acotado por el número de recetas que el cliente empuja, no por el catálogo entero.

- [ ] **Step 5: Ejecutar la suite de sync y verificar que pasa**

```bash
mvn -f backend/pom.xml test -Dtest='SyncControllerTest,SyncServiceTest'
```

Esperado: PASS en ambas clases.

- [ ] **Step 6: Ejecutar la suite completa del backend**

```bash
mvn -f backend/pom.xml test
```

Esperado: `BUILD SUCCESS`. Cualquier clase que construya un `RecipeResponse` posicionalmente y no compile hay que arreglarla aquí.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/sync/SyncService.java \
        backend/src/test/java/org/gipsybuho/recetasfamiliares/sync/SyncControllerTest.java
git commit -m "feat(backend): la portada de receta viaja tambien por sync"
```

---

### Task 3: Android — portada en `RecipeCard`

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/local/Daos.kt:234-254` (RecipePhotoDao)
- Create: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeCovers.kt`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt:81-82`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeScreens.kt:277-296` y `:338-402`
- Test: `android/app/src/test/java/org/gipsybuho/recetasfamiliares/ui/RecipeCoversTest.kt`

**Interfaces:**
- Consumes: nada del backend. Android usa las fotos que Room ya tiene.
- Produces:
  - `preferredCoverUrl(thumbnailUrl: String?, url: String?): String?` en `ui/RecipeCovers.kt`.
  - `RecipePhotoDao.observeCovers(familyId: String): Flow<List<RecipePhotoEntity>>`.
  - `RecetasViewModel.recipeCovers: StateFlow<Map<String, String>>`.
  - `RecipeCard(recipe, coverUrl, modifier, onClick)` — firma con un parámetro nuevo.

**Limitación aceptada:** este proyecto no tiene infraestructura de test de UI Compose ni Robolectric. El test cubre la regla de selección de portada como función pura; el composable y la consulta Room se validan por compilación (Room verifica el SQL en tiempo de compilación) y por prueba manual en el AVD.

- [ ] **Step 1: Escribir el test que falla**

`android/app/src/test/java/org/gipsybuho/recetasfamiliares/ui/RecipeCoversTest.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeCoversTest {

    @Test
    fun `prefiere el thumbnail cuando existe`() {
        assertEquals(
            "https://cdn.test/thumb.jpg",
            preferredCoverUrl("https://cdn.test/thumb.jpg", "https://cdn.test/original.jpg")
        )
    }

    @Test
    fun `cae al original cuando el thumbnail es nulo o esta en blanco`() {
        assertEquals("https://cdn.test/original.jpg", preferredCoverUrl(null, "https://cdn.test/original.jpg"))
        assertEquals("https://cdn.test/original.jpg", preferredCoverUrl("   ", "https://cdn.test/original.jpg"))
    }

    @Test
    fun `devuelve null cuando no hay ninguna url utilizable`() {
        assertNull(preferredCoverUrl(null, null))
        assertNull(preferredCoverUrl("  ", "  "))
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

```bash
cd android && gradle test --tests '*RecipeCoversTest*'
```

Esperado: FALLA a compilar, "Unresolved reference: preferredCoverUrl".

- [ ] **Step 3: Escribir la función pura**

`android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeCovers.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.ui

/**
 * Regla de portada, identica a la del backend: se prefiere el thumbnail y se cae a la
 * imagen original si no lo hay. Null cuando ninguna de las dos es utilizable.
 */
fun preferredCoverUrl(thumbnailUrl: String?, url: String?): String? =
    thumbnailUrl?.takeIf { it.isNotBlank() } ?: url?.takeIf { it.isNotBlank() }
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

```bash
cd android && gradle test --tests '*RecipeCoversTest*'
```

Esperado: PASS, 3 tests.

- [ ] **Step 5: Añadir la consulta de portadas al DAO**

En `Daos.kt`, dentro de `interface RecipePhotoDao`, añadir:

```kotlin
    @Query("""
        SELECT rp.* FROM recipe_photos rp
        INNER JOIN recipes r ON r.id = rp.recipeId
        WHERE r.familyId = :familyId AND rp.deleted = 0 AND r.deleted = 0
        ORDER BY rp.recipeId ASC, rp.position ASC
    """)
    fun observeCovers(familyId: String): Flow<List<RecipePhotoEntity>>
```

- [ ] **Step 6: Exponer el mapa de portadas en el ViewModel**

En `RecetasViewModel.kt`, justo después de la declaración de `recipes` (línea 81), añadir:

```kotlin
    val recipeCovers: StateFlow<Map<String, String>> = container.sessionStore.familyIdFlow
        .flatMapLatest { familyId ->
            if (familyId == null) flowOf(emptyList())
            else container.database.recipePhotoDao().observeCovers(familyId)
        }
        .map { photos ->
            // La consulta llega ordenada por position ascendente: la primera foto de cada
            // receta es su portada. associateBy conservaria la ultima, asi que se pliega
            // a mano quedandose con la primera.
            buildMap {
                photos.forEach { photo ->
                    if (!containsKey(photo.recipeId)) {
                        preferredCoverUrl(photo.thumbnailUrl, photo.url)?.let { put(photo.recipeId, it) }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
```

`AppContainer.database` es público (`core/AppContainer.kt:41`), así que `container.database.recipePhotoDao()` compila tal cual. Los imports `flatMapLatest`, `flowOf`, `map` y `stateIn` ya están en el archivo; `flatMapLatest` requiere `@OptIn(ExperimentalCoroutinesApi::class)` — comprobar si la clase ya lo tiene por el uso de la línea 150 y, si no, añadirlo sobre la propiedad.

- [ ] **Step 7: Pintar la portada en la card**

En `RecipeScreens.kt`, cambiar la firma y el cuerpo de `RecipeCard` (línea 338). El `Box` de 152dp ya existe con icono y degradado; solo se le mete la imagen detrás:

```kotlin
@Composable
private fun RecipeCard(
    recipe: RecipeEntity,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Icon(
                Icons.Outlined.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(56.dp).align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.30f)
            )
            Crossfade(targetState = coverUrl, label = "recipeCardCover") { url ->
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.58f)),
                        startY = 64f
                    )
                )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2
                )
                recipe.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1
                    )
                }
            }
        }
        val totalMin = (recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)
        val creator = creatorLabel(recipe)
        val hasMeta = totalMin > 0 || recipe.difficulty != null || recipe.servings != null || creator != null
        if (hasMeta) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (totalMin > 0) MetaChip("⏱ ${totalMin}m")
                recipe.difficulty?.let { MetaChip(difficultyLabel(it)) }
                recipe.servings?.let { MetaChip("$it porciones") }
                creator?.let { MetaChip("Por $it") }
            }
        }
    }
}
```

**Nota sobre `contentDescription`:** se deja en `null` a propósito. La imagen es decorativa; el título ya está en el mismo `Card` como texto y TalkBack lo lee. Añadir una descripción duplicaría el anuncio.

Añadir los imports que falten: `coil3.compose.AsyncImage` y `androidx.compose.ui.layout.ContentScale`.

- [ ] **Step 8: Pasar el mapa desde la pantalla**

En el mismo archivo, donde se recolectan los estados de la pantalla de recetas, añadir junto a los `collectAsState` existentes:

```kotlin
    val recipeCovers by viewModel.recipeCovers.collectAsState()
```

Y en la llamada a `RecipeCard` (línea 280):

```kotlin
                                                    RecipeCard(
                                                        recipe,
                                                        coverUrl = recipeCovers[recipe.id],
                                                        modifier = Modifier.animateItem().let { m ->
                                                            with(this@SharedTransitionLayout) { m.sharedBounds(boundsState, this@AnimatedContent) }
                                                        }
                                                    ) { selectedRecipe = recipe; error = null }
```

- [ ] **Step 9: Compilar y ejecutar los tests de Android**

```bash
cd android && gradle test && gradle assembleDebug
```

Esperado: ambos `BUILD SUCCESSFUL`. Room falla en compilación si el SQL de `observeCovers` está mal.

- [ ] **Step 10: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeCovers.kt \
        android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeScreens.kt \
        android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasViewModel.kt \
        android/app/src/main/java/org/gipsybuho/recetasfamiliares/data/local/Daos.kt \
        android/app/src/test/java/org/gipsybuho/recetasfamiliares/ui/RecipeCoversTest.kt
git commit -m "feat(android): muestra la portada de receta en las cards del listado"
```

---

### Task 4: Desktop — cargador de imagen autenticado

**Files:**
- Create: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/AuthenticatedImageLoader.java`
- Test: `desktop/src/test/java/org/gipsybuho/recetasfamiliares/ui/AuthenticatedImageLoaderTest.java`

**Interfaces:**
- Consumes: `AppSession.getAccessToken()`, ya existente.
- Produces:
  - `AuthenticatedImageLoader(AppSession session, int maxEntries)`.
  - `byte[] fetch(String url)` — bytes de la imagen, o `null` si falla. Cachea aciertos y también los fallos, para no martillear el servidor con URLs rotas.
  - `void shutdown()`.

**Por qué existe:** `/uploads/**` exige JWT y `javafx.scene.image.Image(url)` no admite cabeceras.

Se separa la descarga (esta tarea, testeable sin JavaFX) del pintado (Task 5, no testeable aquí). `fetch` devuelve bytes precisamente para poder testearlo sin arrancar el toolkit de JavaFX.

- [ ] **Step 1: Escribir el test que falla**

`desktop/src/test/java/org/gipsybuho/recetasfamiliares/ui/AuthenticatedImageLoaderTest.java`:

```java
package org.gipsybuho.recetasfamiliares.ui;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthenticatedImageLoaderTest {

    private static final byte[] PAYLOAD = new byte[] { 1, 2, 3, 4, 5 };

    private Preferences prefs;
    private AppSession session;
    private MockWebServer server;
    private AuthenticatedImageLoader loader;

    @BeforeEach
    void setUp() throws Exception {
        prefs = Preferences.userRoot().node("recetas-image-loader-test-" + UUID.randomUUID());
        prefs.clear();
        session = new AppSession(prefs);
        session.setAccessToken("token-de-prueba");
        server = new MockWebServer();
        server.start();
        loader = new AuthenticatedImageLoader(session, 8);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loader != null) loader.shutdown();
        if (server != null) server.shutdown();
        if (prefs != null && prefs.nodeExists("")) prefs.removeNode();
    }

    @Test
    void sendsBearerTokenAndReturnsBytes() throws Exception {
        server.enqueue(new MockResponse().setBody(new Buffer().write(PAYLOAD)));

        byte[] bytes = loader.fetch(server.url("/uploads/foto.jpg").toString());

        assertArrayEquals(PAYLOAD, bytes);
        RecordedRequest request = server.takeRequest();
        assertEquals("Bearer token-de-prueba", request.getHeader("Authorization"));
    }

    @Test
    void cachesSuccessSoTheServerIsHitOnce() throws Exception {
        server.enqueue(new MockResponse().setBody(new Buffer().write(PAYLOAD)));
        String url = server.url("/uploads/foto.jpg").toString();

        assertArrayEquals(PAYLOAD, loader.fetch(url));
        assertArrayEquals(PAYLOAD, loader.fetch(url));

        assertEquals(1, server.getRequestCount());
    }

    @Test
    void returnsNullOnErrorAndDoesNotRetry() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));
        String url = server.url("/uploads/no-existe.jpg").toString();

        assertNull(loader.fetch(url));
        assertNull(loader.fetch(url));

        assertEquals(1, server.getRequestCount());
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

```bash
cd desktop && mvn test -Dtest=AuthenticatedImageLoaderTest
```

Esperado: FALLA a compilar, "cannot find symbol: class AuthenticatedImageLoader".

- [ ] **Step 3: Escribir el cargador**

`desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/AuthenticatedImageLoader.java`:

```java
package org.gipsybuho.recetasfamiliares.ui;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.gipsybuho.recetasfamiliares.core.AppSession;

/**
 * Descarga imagenes de /uploads/** con el JWT de la sesion. Existe porque
 * javafx.scene.image.Image(url) no permite enviar cabeceras.
 *
 * Devuelve bytes, no Image, para poder testearlo sin arrancar el toolkit de JavaFX.
 * NUNCA llamar a fetch desde el JavaFX Application Thread: bloquea en red.
 */
public final class AuthenticatedImageLoader {

    private static final byte[] FAILED = new byte[0];
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    private final AppSession session;
    private final OkHttpClient client;
    private final Map<String, byte[]> cache;

    public AuthenticatedImageLoader(AppSession session, int maxEntries) {
        this.session = session;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build();
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > maxEntries;
            }
        });
    }

    /** Bytes de la imagen, o null si no se pudo descargar. Los fallos tambien se cachean. */
    public byte[] fetch(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        byte[] cached = cache.get(url);
        if (cached != null) {
            return cached == FAILED ? null : cached;
        }

        byte[] result = download(url);
        cache.put(url, result == null ? FAILED : result);
        return result;
    }

    private byte[] download(String url) {
        Request.Builder builder = new Request.Builder().url(url).get();
        String token = session.getAccessToken();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            long declared = body.contentLength();
            if (declared > MAX_IMAGE_BYTES) {
                return null;
            }
            byte[] bytes = body.bytes();
            return bytes.length > MAX_IMAGE_BYTES ? null : bytes;
        } catch (IOException e) {
            return null;
        }
    }

    public void clearCache() {
        cache.clear();
    }

    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

```bash
cd desktop && mvn test -Dtest=AuthenticatedImageLoaderTest
```

Esperado: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/AuthenticatedImageLoader.java \
        desktop/src/test/java/org/gipsybuho/recetasfamiliares/ui/AuthenticatedImageLoaderTest.java
git commit -m "feat(desktop): anade un cargador de imagenes con JWT y cache acotada"
```

---

### Task 5: Desktop — miniatura 56×56 en `RecipeCell`

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/RecipeDtos.java:9-24`
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/core/AppContext.java`
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeListView.java:254-289`
- Test: validación manual (ver Step 5)

**Interfaces:**
- Consumes: `AuthenticatedImageLoader.fetch(String)` de la Task 4, y el campo `coverThumbnailUrl` del JSON que produce la Task 1.
- Produces: `AppContext.getImageLoader()` → `AuthenticatedImageLoader`.

**Limitación aceptada:** Desktop no tiene tests de UI automatizados (`COD-8` sigue parcial). Esta tarea se valida por compilación, por los tests de la Task 4 y por prueba manual con la GUI real.

- [ ] **Step 1: Añadir el campo al DTO**

En `RecipeDtos.java`, añadir `coverThumbnailUrl` como **último** componente de `RecipeDto`:

```java
    public record RecipeDto(
            String id,
            String familyId,
            String title,
            String description,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            String difficulty,
            String createdAt,
            String updatedAt,
            long syncVersion,
            boolean deleted,
            String createdByUserId,
            String createdByDisplayName,
            String coverThumbnailUrl
    ) {}
```

Gson rellena con `null` los campos ausentes, así que un backend antiguo no rompe el cliente.

- [ ] **Step 2: Exponer el cargador en `AppContext`**

En `AppContext.java`: añadir el import `org.gipsybuho.recetasfamiliares.ui.AuthenticatedImageLoader`, el campo, su construcción, el getter y el apagado.

```java
    private final AuthenticatedImageLoader imageLoader;
```

En el constructor, después de `apiClient = new ApiClient(session);`:

```java
        imageLoader = new AuthenticatedImageLoader(session, 200);
```

Getter, junto a los demás:

```java
    public AuthenticatedImageLoader getImageLoader() { return imageLoader; }
```

En `clearFamilyScopedCaches()`, añadir `imageLoader.clearCache();` — al cambiar de familia no deben quedar cacheadas imágenes de la anterior.

En `shutdown()`, añadir `imageLoader.shutdown();`.

- [ ] **Step 3: Reescribir `RecipeCell`**

En `RecipeListView.java`, sustituir la clase interna `RecipeCell` (líneas 254-289). Deja de ser `static` porque necesita el `AppContext`:

```java
    private class RecipeCell extends ListCell<RecipeDtos.RecipeDto> {

        private static final double THUMB_SIZE = 56;

        private final ImageView thumb = new ImageView();
        private final Region placeholder = new Region();
        private final StackPane thumbHolder = new StackPane(placeholder, thumb);
        private final Label title = new Label();
        private final Label meta = new Label();
        private final HBox root;

        /** Identifica la carga en curso: si la celda se recicla, el resultado viejo se descarta. */
        private String pendingUrl;

        RecipeCell() {
            thumb.setFitWidth(THUMB_SIZE);
            thumb.setFitHeight(THUMB_SIZE);
            thumb.setPreserveRatio(true);
            thumb.setSmooth(true);
            Rectangle clip = new Rectangle(THUMB_SIZE, THUMB_SIZE);
            clip.setArcWidth(12);
            clip.setArcHeight(12);
            thumb.setClip(clip);

            placeholder.setPrefSize(THUMB_SIZE, THUMB_SIZE);
            placeholder.setMinSize(THUMB_SIZE, THUMB_SIZE);
            placeholder.setMaxSize(THUMB_SIZE, THUMB_SIZE);
            placeholder.getStyleClass().add("recipe-cell-thumb-placeholder");

            thumbHolder.setPrefSize(THUMB_SIZE, THUMB_SIZE);
            thumbHolder.setMinSize(THUMB_SIZE, THUMB_SIZE);

            title.getStyleClass().add("recipe-cell-title");
            meta.getStyleClass().add("recipe-cell-meta");
            VBox texts = new VBox(3, title, meta);
            root = new HBox(10, thumbHolder, texts);
            root.getStyleClass().add("recipe-cell");
            HBox.setHgrow(texts, Priority.ALWAYS);
        }

        @Override
        protected void updateItem(RecipeDtos.RecipeDto recipe, boolean empty) {
            super.updateItem(recipe, empty);
            if (empty || recipe == null) {
                pendingUrl = null;
                setText(null);
                setGraphic(null);
                return;
            }
            title.setText(recipe.title());
            meta.setText(buildMeta(recipe));
            setGraphic(root);
            setText(null);
            loadCover(recipe.coverThumbnailUrl());
        }

        private void loadCover(String url) {
            pendingUrl = url;
            thumb.setImage(null);
            thumb.setOpacity(0);
            if (url == null || url.isBlank()) {
                return;
            }
            Thread.ofVirtual().start(() -> {
                byte[] bytes = context.getImageLoader().fetch(url);
                if (bytes == null) {
                    return;
                }
                Image image = new Image(new ByteArrayInputStream(bytes),
                        THUMB_SIZE * 2, THUMB_SIZE * 2, true, true);
                Platform.runLater(() -> {
                    // La celda pudo reciclarse mientras bajaba la imagen: si ya no
                    // corresponde a esta url, se descarta en vez de pintar la foto
                    // de otra receta.
                    if (!java.util.Objects.equals(pendingUrl, url) || image.isError()) {
                        return;
                    }
                    thumb.setImage(image);
                    if (MotionPreferences.isReducedMotion()) {
                        thumb.setOpacity(1);
                    } else {
                        FadeTransition fade = new FadeTransition(Duration.millis(150), thumb);
                        fade.setFromValue(0);
                        fade.setToValue(1);
                        fade.play();
                    }
                });
            });
        }

        private String buildMeta(RecipeDtos.RecipeDto r) {
            StringBuilder sb = new StringBuilder();
            if (r.servings() != null) sb.append(r.servings()).append(" pers.  ");
            if (r.prepMinutes() != null) sb.append(r.prepMinutes()).append(" min");
            if (r.difficulty() != null) sb.append("  ·  ").append(r.difficulty());
            String creator = creatorLabel(r);
            if (creator != null) sb.append("  ·  Por ").append(creator);
            return sb.toString().trim();
        }

        private String creatorLabel(RecipeDtos.RecipeDto r) {
            return r.createdByDisplayName() != null && !r.createdByDisplayName().isBlank()
                    ? r.createdByDisplayName()
                    : null;
        }
    }
```

Añadir los imports que falten en `RecipeListView.java`:

```java
import javafx.animation.FadeTransition;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.io.ByteArrayInputStream;
```

(`Rectangle`, `Duration`, `Platform`, `Priority` y los contenedores ya están importados.)

- [ ] **Step 4: Estilo del placeholder**

En `desktop/src/main/resources/style.css`, justo después del bloque `.recipe-cell-title` (línea 298), añadir usando las variables de la paleta que ya define el archivo:

```css
.recipe-cell-thumb-placeholder {
    -fx-background-color: recetas-surface-var;
    -fx-border-color: recetas-surface-border;
    -fx-border-radius: 6;
    -fx-background-radius: 6;
}
```

`recetas-surface-var` y `recetas-surface-border` están definidas en las líneas 10 y 11 del mismo archivo, así que el placeholder sigue el tema en claro y en oscuro sin colores nuevos.

- [ ] **Step 5: Compilar, testear y validar a mano**

```bash
cd desktop && mvn test && mvn -DskipTests compile
```

Esperado: `BUILD SUCCESS` en ambos.

Después, con el backend en marcha, arrancar la GUI:

```bash
cd desktop && mvn javafx:run
```

Comprobar, y anotar el resultado real de cada punto:

1. Una receta con foto muestra su miniatura.
2. Una receta sin foto muestra el placeholder, no un hueco vacío.
3. Al hacer scroll rápido por una lista larga, **ninguna fila muestra la foto de otra receta** (es el fallo de reciclado de `ListCell`).
4. La ventana responde durante la carga: la lista no se congela.
5. Al cambiar de familia, no aparecen miniaturas de la familia anterior.

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/RecipeDtos.java \
        desktop/src/main/java/org/gipsybuho/recetasfamiliares/core/AppContext.java \
        desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeListView.java \
        desktop/src/main/resources
git commit -m "feat(desktop): muestra la portada de receta en el listado"
```

---

### Task 6: Cierre del sprint

**Files:**
- Modify: `CONTINUAR.md`
- Modify: `paraImplementar.txt`

- [ ] **Step 1: Revisión de diseño**

```
/impeccable critique android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeScreens.kt
```

Aplicar solo lo que no contradiga la paleta y el lenguaje visual ya definidos en `CLAUDE.md`. Si contradice, gana `CLAUDE.md`.

- [ ] **Step 2: Seguridad**

```
/VibeSec
/security-review
```

```bash
pwsh -NoProfile -File scripts/security/run-security-scan.ps1
```

Exit 0 = limpio. Exit 1 = bloqueante, no se cierra el sprint sin corregir o justificar por escrito.

- [ ] **Step 3: Validación completa**

```bash
set -a && . <(tr -d '\r' < herztner/recetas_app.env) && set +a
mvn -f backend/pom.xml test
cd desktop && mvn test && cd ..
cd android && gradle test && gradle assembleDebug && cd ..
```

Anotar el número real de tests y fallos de cada uno. No escribir "PASS" sin haberlo ejecutado.

- [ ] **Step 4: Documentar y commitear**

Actualizar en `CONTINUAR.md`: el punto (7) pasa de PARCIAL a COMPLETO para Android y Desktop, con iOS anotado como pendiente. Registrar agentes y skills usados, comandos ejecutados con su resultado, archivos modificados y riesgo residual (sin tests de UI automatizados en Desktop; validación manual del reciclado de celdas).

```bash
git add CONTINUAR.md paraImplementar.txt
git commit -m "docs: cierra el sprint de portada de receta en los listados"
```

---

## Fuera de este plan

- **Deuda de `UploadControllerTest`.** Aislada pasa entera (7/7, verificado el 2026-08-01), así que el fallo depende de la suite completa. Necesita su propio plan, guiado por `superpowers:systematic-debugging`, partiendo de la ejecución de `mvn -f backend/pom.xml test`.
- **iOS**, otros listados (favoritos, menús, búsqueda global) y elegir manualmente qué foto es la portada.
