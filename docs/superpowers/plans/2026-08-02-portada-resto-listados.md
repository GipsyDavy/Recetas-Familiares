# Portada de receta en el resto de listados — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Llevar la miniatura de portada de receta a la búsqueda global (Android y Desktop), a "Recetas recientes" del dashboard de Desktop y al menú semanal de ambas plataformas.

**Architecture:** Sprint 100 % cliente. Android ya tiene el dato: `viewModel.recipeCovers` es un `StateFlow<Map<recipeId, url>>` alimentado por Room; las pantallas nuevas solo lo consumen. Desktop extrae el guard de reciclado que hoy vive dentro de `RecipeCell` a un componente reutilizable `RecipeThumbnail`, y resuelve la portada del menú cruzando `MenuItemDto.recipeId` contra la caché de `RecipeRepository`.

**Tech Stack:** Kotlin + Jetpack Compose (Android), Java 21 + JavaFX (Desktop), JUnit 5 + Maven (tests Desktop), JUnit 4/Gradle (tests Android).

**Spec:** `docs/superpowers/specs/2026-08-02-portada-resto-listados-design.md`

## Global Constraints

- **No se toca backend, contrato de API, base de datos, sincronización ni iOS.** Ni un archivo bajo `backend/`, `ios/` o `database/`.
- **Ningún cliente HTTP nuevo.** Todas las descargas de imagen pasan por `ImageCache.fetch` → `ApiClient.fetchImage`, que restringe el JWT al origen del backend. Las URLs salen de la base de datos; crear un `OkHttpClient` propio filtraría el token a un host arbitrario.
- **Nunca bloquear el JavaFX Application Thread.** `ImageCache.fetch` hace red: siempre dentro de `Thread.ofVirtual()`, y el pintado siempre en `Platform.runLater`.
- **Nunca bloquear el hilo principal de Android.**
- **Tamaños de miniatura**: Desktop 56 px en listado, búsqueda y dashboard; 40 px en la celda del menú semanal. Android 56 dp en búsqueda global, 48 dp en `MealRow`.
- **Placeholder siempre**, también en recetas sin foto: nunca un hueco vacío ni un salto de layout.
- **Respetar `MotionPreferences.isReducedMotion()`** (Desktop): sin fade cuando esté activo.
- **Sin logs de URLs de imagen**: identifican una foto familiar concreta (criterio ya aplicado en `ImageCache`).
- **Vista mensual del menú de Desktop fuera de alcance.** No tocar `refreshMonth`, `buildMonthStructure` ni `populateMonthCells`.
- **Selectores de receta fuera de alcance**: `AssignMenuDialog` (Android) y `showRecipePicker` (Desktop) no se tocan.

## Estructura de archivos

| Archivo | Responsabilidad |
|---|---|
| `desktop/src/main/java/.../data/repository/RecipeRepository.java` | **Modificar.** Añade `coverUrlFor(String)`: única fuente de portada por `recipeId` para las vistas que no manejan `RecipeDto` |
| `desktop/src/test/java/.../data/repository/RecipeCoverLookupTest.java` | **Crear.** Tests de `coverUrlFor` |
| `desktop/src/main/java/.../ui/RecipeThumbnail.java` | **Crear.** Nodo de miniatura reutilizable: placeholder, descarga, guard de url, fade |
| `desktop/src/main/java/.../ui/RecipeListView.java` | **Modificar.** `RecipeCell` migra al componente |
| `desktop/src/main/java/.../ui/GlobalSearchView.java` | **Modificar.** Miniatura en las filas de receta |
| `desktop/src/main/java/.../ui/DashboardView.java` | **Modificar.** Miniatura en "Recetas recientes" |
| `desktop/src/main/java/.../ui/WeeklyMenuView.java` | **Modificar.** Miniatura en la celda semanal + repoblado de la caché |
| `android/app/src/main/java/.../ui/RecipeCovers.kt` | **Modificar.** Añade el composable `RecipeThumb` junto a la lógica de portada que ya vive aquí |
| `android/app/src/main/java/.../ui/GlobalSearchScreen.kt` | **Modificar.** Miniatura de 56 dp en la sección Recetas |
| `android/app/src/main/java/.../ui/MenuScreen.kt` | **Modificar.** Miniatura de 48 dp en `MealRow` |
| `android/app/src/main/java/.../ui/RecetasApp.kt` | **Modificar.** Pasa `recipeCovers` a ambas pantallas |
| `CONTINUAR.md` | **Modificar.** Cierre del sprint |

## Nota sobre tests

Solo la Task 1 admite TDD real: es la única lógica pura del sprint. El resto son nodos de JavaFX y composables, y **este repositorio no tiene infraestructura para testear UI en ninguna de las dos plataformas** (no hay TestFX, ni Robolectric, ni Compose UI Test). Las tasks 2-6 se verifican por compilación, por las suites existentes y por la validación visual de la Task 7. No inventes tests de UI: fallarían por falta de toolkit, no por el código.

---

### Task 1: `coverUrlFor` en `RecipeRepository`

Las vistas del menú manejan `MenuItemDto`, que trae `recipeId` pero no portada. Esta función es el único punto donde se resuelve esa portada, y la única lógica testeable del sprint.

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/RecipeRepository.java`
- Test: `desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/RecipeCoverLookupTest.java`

**Interfaces:**
- Consumes: `RecipeDtos.RecipeDto` (record de 15 componentes, ya existente), `SimpleCache.getItems()`
- Produces: `public String coverUrlFor(String recipeId)` — devuelve la url de portada, o `null` si la receta no está cacheada, está borrada, o no tiene portada. La usa la Task 4.

- [ ] **Step 1: Escribir el test que falla**

Crear `desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/RecipeCoverLookupTest.java`:

```java
package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Portada resuelta por recipeId para las vistas que manejan MenuItemDto y no
 * RecipeDto. Todo lo que no sea una portada utilizable devuelve null: el
 * llamante pinta el placeholder y nunca un hueco vacio.
 */
class RecipeCoverLookupTest {

    @Test
    void devuelveLaPortadaDeLaRecetaCacheada() {
        RecipeRepository repo = new RecipeRepository(null, null);
        repo.updateFromSync(List.of(
                recipe("r1", "https://api.example.test/uploads/recipe_thumbnails/a.jpg", false),
                recipe("r2", "https://api.example.test/uploads/recipe_thumbnails/b.jpg", false)
        ), null, null);

        assertEquals("https://api.example.test/uploads/recipe_thumbnails/b.jpg", repo.coverUrlFor("r2"));
    }

    @Test
    void devuelveNullSiLaRecetaNoTienePortada() {
        RecipeRepository repo = new RecipeRepository(null, null);
        repo.updateFromSync(List.of(recipe("r1", null, false), recipe("r2", "   ", false)), null, null);

        assertNull(repo.coverUrlFor("r1"));
        assertNull(repo.coverUrlFor("r2"));
    }

    @Test
    void devuelveNullSiLaRecetaEstaBorrada() {
        RecipeRepository repo = new RecipeRepository(null, null);
        // updateFromSync ya descarta tombstones; esto protege ademas el camino
        // replaceAll, que la usan las vistas al cargar una pagina.
        repo.getCache().replaceAll(List.of(recipe("r1", "https://api.example.test/uploads/a.jpg", true)));

        assertNull(repo.coverUrlFor("r1"));
    }

    @Test
    void devuelveNullSiElIdNoEstaEnCache() {
        RecipeRepository repo = new RecipeRepository(null, null);
        repo.updateFromSync(List.of(recipe("r1", "https://api.example.test/uploads/a.jpg", false)), null, null);

        assertNull(repo.coverUrlFor("desconocida"));
    }

    @Test
    void devuelveNullConIdNuloOVacio() {
        RecipeRepository repo = new RecipeRepository(null, null);
        repo.updateFromSync(List.of(recipe("r1", "https://api.example.test/uploads/a.jpg", false)), null, null);

        assertNull(repo.coverUrlFor(null));
        assertNull(repo.coverUrlFor(""));
        assertNull(repo.coverUrlFor("   "));
    }

    private static RecipeDtos.RecipeDto recipe(String id, String coverThumbnailUrl, boolean deleted) {
        return new RecipeDtos.RecipeDto(
                id, "fam-1", "Receta " + id, null,
                null, null, null, null,
                "2026-08-02T10:00:00Z", "2026-08-02T10:00:00Z", 1L, deleted,
                null, null, coverThumbnailUrl);
    }
}
```

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```powershell
mvn -f desktop/pom.xml test -Dtest=RecipeCoverLookupTest
```

Esperado: FAIL de compilación, `cannot find symbol: method coverUrlFor(String)`.

- [ ] **Step 3: Implementar `coverUrlFor`**

En `RecipeRepository.java`, justo después de `getStepCache()` (línea 47):

```java
    /**
     * Portada de una receta ya cargada en la cache local, por id. Existe para las
     * vistas que manejan MenuItemDto y no tienen el RecipeDto a mano.
     *
     * Devuelve null si la receta no esta cacheada, esta borrada o no tiene portada:
     * el llamante pinta el placeholder. No hace red: si la cache esta vacia hay que
     * repoblarla antes (ver WeeklyMenuView.ensureRecipeCacheLoaded).
     */
    public String coverUrlFor(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        for (RecipeDtos.RecipeDto recipe : cache.getItems()) {
            if (recipeId.equals(recipe.id())) {
                if (recipe.deleted()) {
                    return null;
                }
                String url = recipe.coverThumbnailUrl();
                return url != null && !url.isBlank() ? url : null;
            }
        }
        return null;
    }
```

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```powershell
mvn -f desktop/pom.xml test -Dtest=RecipeCoverLookupTest
```

Esperado: 5 tests, 0 fallos.

- [ ] **Step 5: Ejecutar la suite completa de Desktop**

```powershell
mvn -f desktop/pom.xml test
```

Esperado: 60 tests, 0 fallos (55 previos + 5 nuevos). Si el número previo no es 55, anótalo tal cual: lo que importa es que no haya fallos y que crezca en 5.

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/RecipeRepository.java desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/RecipeCoverLookupTest.java
git commit -m "feat(desktop): resuelve la portada de una receta por su id"
```

---

### Task 2: Componente `RecipeThumbnail` y migración de `RecipeCell`

Extrae a un nodo reutilizable el guard de reciclado, la descarga y el fade que hoy viven dentro de `RecipeCell`. Al terminar esta task, el listado principal de recetas debe verse y comportarse **exactamente igual** que antes.

**Files:**
- Create: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeThumbnail.java`
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeListView.java:259-347`

**Interfaces:**
- Consumes: `AppContext.getImageCache()`, `MotionPreferences.isReducedMotion()`, clase CSS `recipe-cell-thumb-placeholder` (ya existe en `style.css:306`)
- Produces: `RecipeThumbnail(AppContext context, double size)`, `void show(String url)`, `void clear()`. Los usan las tasks 3 y 4.

- [ ] **Step 1: Crear el componente**

Crear `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeThumbnail.java`:

```java
package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.io.ByteArrayInputStream;
import java.util.Objects;

/**
 * Miniatura de portada de receta, reutilizable en cualquier vista.
 *
 * Nace de RecipeCell, que era el unico sitio con esta logica: al llevarla a mas
 * listados hacia falta una sola implementacion del guard de url, del presupuesto
 * de imagen y del fade, en vez de una copia por vista.
 *
 * La descarga la hace ImageCache, que a su vez usa ApiClient.fetchImage: NUNCA
 * construir aqui un cliente HTTP propio, porque las urls salen de la base de datos
 * y el token solo debe viajar al origen del backend.
 */
public final class RecipeThumbnail extends StackPane {

    private static final double CORNER_RADIUS = 12;
    private static final double FADE_MILLIS = 150;

    private final AppContext context;
    private final double size;
    private final ImageView thumb = new ImageView();
    private final Region placeholder = new Region();

    /**
     * Url de la carga en curso. Si el nodo se reutiliza -- celda reciclada de una
     * ListView, o vista que se vuelve a pintar -- el resultado viejo se descarta en
     * vez de pintar la foto de otra receta.
     */
    private String pendingUrl;

    public RecipeThumbnail(AppContext context, double size) {
        this.context = context;
        this.size = size;

        thumb.setFitWidth(size);
        thumb.setFitHeight(size);
        thumb.setPreserveRatio(true);
        thumb.setSmooth(true);
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(CORNER_RADIUS);
        clip.setArcHeight(CORNER_RADIUS);
        thumb.setClip(clip);

        placeholder.setPrefSize(size, size);
        placeholder.setMinSize(size, size);
        placeholder.setMaxSize(size, size);
        placeholder.getStyleClass().add("recipe-cell-thumb-placeholder");

        setPrefSize(size, size);
        setMinSize(size, size);
        getChildren().addAll(placeholder, thumb);
    }

    /**
     * Pinta la portada de esa url. Con null o vacio deja el placeholder visible, que
     * es lo que corresponde a una receta sin fotos. Llamar en el JavaFX Application
     * Thread: la red se hace dentro, en un hilo virtual.
     */
    public void show(String url) {
        pendingUrl = url;
        thumb.setImage(null);
        thumb.setOpacity(0);
        if (url == null || url.isBlank()) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            byte[] bytes = context.getImageCache().fetch(url);
            if (bytes == null) {
                return;
            }
            Image image = new Image(new ByteArrayInputStream(bytes), size * 2, size * 2, true, true);
            Platform.runLater(() -> {
                if (!Objects.equals(pendingUrl, url) || image.isError()) {
                    return;
                }
                thumb.setImage(image);
                if (MotionPreferences.isReducedMotion()) {
                    thumb.setOpacity(1);
                } else {
                    FadeTransition fade = new FadeTransition(Duration.millis(FADE_MILLIS), thumb);
                    fade.setFromValue(0);
                    fade.setToValue(1);
                    fade.play();
                }
            });
        });
    }

    /** Descarta la carga en vuelo y vuelve al placeholder. */
    public void clear() {
        show(null);
    }
}
```

- [ ] **Step 2: Migrar `RecipeCell`**

En `RecipeListView.java`, sustituir la clase interna `RecipeCell` completa (líneas 258-347, desde el comentario `/** Deja de ser static...` hasta el cierre de `loadCover`) por esta versión, **conservando intactos** `buildMeta` y `creatorLabel`:

```java
    /** Deja de ser static: necesita el AppContext para descargar las portadas. */
    private class RecipeCell extends ListCell<RecipeDtos.RecipeDto> {

        private static final double THUMB_SIZE = 56;

        private final RecipeThumbnail thumb = new RecipeThumbnail(context, THUMB_SIZE);
        private final Label title = new Label();
        private final Label meta = new Label();
        private final HBox root;

        RecipeCell() {
            title.getStyleClass().add("recipe-cell-title");
            meta.getStyleClass().add("recipe-cell-meta");
            VBox texts = new VBox(3, title, meta);
            root = new HBox(10, thumb, texts);
            root.getStyleClass().add("recipe-cell");
            HBox.setHgrow(texts, Priority.ALWAYS);
        }

        @Override
        protected void updateItem(RecipeDtos.RecipeDto recipe, boolean empty) {
            super.updateItem(recipe, empty);
            if (empty || recipe == null) {
                thumb.clear();
                setText(null);
                setGraphic(null);
                return;
            }
            title.setText(recipe.title());
            meta.setText(buildMeta(recipe));
            setGraphic(root);
            setText(null);
            thumb.show(recipe.coverThumbnailUrl());
        }
```

- [ ] **Step 3: Limpiar los imports que quedan sin uso**

`RecipeListView` usaba `ImageView`, `Region`, `StackPane`, `FadeTransition`, `Duration`, `ByteArrayInputStream` y `Image` **solo** para la miniatura. Comprueba cuáles siguen usándose antes de borrarlos: `Rectangle` y `Color` los usa también `buildSkeletonPane` (líneas 142-152), y `Platform` lo usa `refresh`. Borra únicamente los que ya no aparezcan en el archivo.

- [ ] **Step 4: Compilar y ejecutar la suite de Desktop**

```powershell
mvn -f desktop/pom.xml test
```

Esperado: BUILD SUCCESS, mismo número de tests que al final de la Task 1, 0 fallos. Si algún import sobrante quedó sin borrar el compilador no falla, pero si borraste uno de más sí: el error dirá exactamente cuál.

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeThumbnail.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeListView.java
git commit -m "refactor(desktop): extrae la miniatura de portada a un componente reutilizable"
```

---

### Task 3: Miniatura en la búsqueda global y en el dashboard de Desktop

Las dos vistas más baratas: ambas ya manejan `RecipeDto`, que trae `coverThumbnailUrl` desde el sprint anterior. No hace falta cruzar nada.

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/GlobalSearchView.java:60-71,101-121`
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/DashboardView.java:325-354`

**Interfaces:**
- Consumes: `RecipeThumbnail(AppContext, double)` y `show(String)` de la Task 2; `RecipeDto.coverThumbnailUrl()`
- Produces: nada que consuman otras tasks

- [ ] **Step 1: Añadir la fila de receta con miniatura en `GlobalSearchView`**

En `GlobalSearchView.java`, añadir la constante junto al campo `context` (línea 17) y el método nuevo justo después de `resultRow` (línea 121):

```java
    private static final double THUMB_SIZE = 56;
```

```java
    /**
     * Fila de resultado de receta: identica a las demas, con la miniatura de portada
     * delante. Se construye sobre resultRow para no duplicar estilos ni navegacion.
     */
    private Button recipeResultRow(String title, String meta, String coverUrl) {
        Button btn = resultRow(title, meta, "recipes");
        Node texts = btn.getGraphic();
        RecipeThumbnail thumb = new RecipeThumbnail(context, THUMB_SIZE);
        thumb.show(coverUrl);
        HBox row = new HBox(12, thumb, texts);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(texts, Priority.ALWAYS);
        btn.setGraphic(row);
        return btn;
    }
```

- [ ] **Step 2: Usarla en la sección de recetas**

En `GlobalSearchView.java:69`, cambiar:

```java
                results.getChildren().add(resultRow(r.title(), meta.toString(), "recipes"));
```

por:

```java
                results.getChildren().add(recipeResultRow(r.title(), meta.toString(), r.coverThumbnailUrl()));
```

Las secciones de stock y notas siguen llamando a `resultRow` sin cambios.

- [ ] **Step 3: Añadir el import que falta en `GlobalSearchView`**

El archivo importa `javafx.scene.layout.*` (línea 6), así que `HBox` y `Priority` ya están cubiertos, y `Pos` está en la línea 4. Solo falta:

```java
import javafx.scene.Node;
```

- [ ] **Step 4: Añadir la miniatura en `DashboardView`**

En `DashboardView.java`, añadir la constante junto al campo `context` (línea 30):

```java
    private static final double THUMB_SIZE = 56;
```

y en `buildRecipeCard`, sustituir la línea 352 (`card.getChildren().add(info);`) por:

```java
        RecipeThumbnail thumb = new RecipeThumbnail(context, THUMB_SIZE);
        thumb.show(recipe.coverThumbnailUrl());
        card.getChildren().addAll(thumb, info);
```

El `HBox` ya tiene separación de 16 px y la miniatura entra antes que el bloque de textos, igual que en el listado.

- [ ] **Step 5: Comprobar los imports de `DashboardView`**

No hay que añadir ninguno: el archivo importa `javafx.scene.layout.*` (línea 7), que cubre `HBox`, `VBox` y `Priority`, e `Insets` está en la línea 5. `RecipeThumbnail` vive en el mismo paquete `ui`, así que tampoco necesita import.

- [ ] **Step 6: Compilar y ejecutar la suite**

```powershell
mvn -f desktop/pom.xml test
```

Esperado: BUILD SUCCESS, 0 fallos.

- [ ] **Step 7: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/GlobalSearchView.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/DashboardView.java
git commit -m "feat(desktop): muestra la portada en la busqueda global y en el dashboard"
```

---

### Task 4: Miniatura en el menú semanal de Desktop

La única vista que no tiene el `RecipeDto` a mano. Además hay que repoblar la caché de recetas, porque `AppContext.clearFamilyScopedCaches()` (`AppContext.java:84`) la vacía junto con la de imágenes: sin esto, abrir el menú sin pasar antes por "Recetas" no mostraría ni una miniatura.

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/WeeklyMenuView.java:107-129,324-337`

**Interfaces:**
- Consumes: `RecipeRepository.coverUrlFor(String)` de la Task 1; `RecipeThumbnail` de la Task 2
- Produces: nada que consuman otras tasks

- [ ] **Step 1: Añadir el repoblado de la caché de recetas**

En `WeeklyMenuView.java`, añadir la constante junto al campo `context` (línea 31):

```java
    private static final double MENU_THUMB_SIZE = 40;
    private static final int RECIPE_CACHE_PAGE_SIZE = 100;
```

y el método nuevo justo antes de `refreshMonth` (línea 131):

```java
    /**
     * La cache de recetas y la de imagenes se vacian juntas en
     * AppContext.clearFamilyScopedCaches(), asi que abrir el menu sin pasar antes por
     * "Recetas" dejaria todas las celdas sin miniatura. Se repuebla una sola vez, y
     * solo cuando hace falta.
     *
     * NUNCA llamar desde el JavaFX Application Thread: hace red. Un fallo aqui no debe
     * impedir que el menu se pinte, asi que la excepcion se traga: el usuario ve los
     * titulos con placeholder, que es la degradacion prevista.
     */
    private void ensureRecipeCacheLoaded() {
        if (!context.getRecipeRepository().getCache().isEmpty()) {
            return;
        }
        String familyAtStart = context.getSession().getFamilyId();
        try {
            var page = context.getRecipeRepository().loadPage(familyAtStart, 0, RECIPE_CACHE_PAGE_SIZE);
            Platform.runLater(() -> {
                if (!java.util.Objects.equals(familyAtStart, context.getSession().getFamilyId())) {
                    return;
                }
                context.getRecipeRepository().getCache().replaceAll(
                        page.items().stream().filter(r -> !r.deleted()).toList());
            });
        } catch (Exception ignored) {
            // Sin miniaturas, pero el menu se pinta igual.
        }
    }
```

El guard de familia es el mismo de `RecipeListView.refresh` (`RecipeListView.java:116-125`): si el usuario cambia de familia mientras la petición está en vuelo, el resultado se descarta en vez de contaminar la caché de la familia nueva.

- [ ] **Step 2: Llamarlo antes de cargar el menú de la semana**

En `WeeklyMenuView.refresh()`, dentro del `Thread.ofVirtual()` (línea 117), añadir la llamada como primera sentencia del `try`:

```java
        Thread.ofVirtual().start(() -> {
            try {
                ensureRecipeCacheLoaded();
                List<SyncDtos.MenuDtos.MenuItemDto> items =
                        context.getMenuRepository().loadForWeek(weekStart);
```

El resto del método no cambia. El orden importa: `Platform.runLater` respeta el orden de encolado, así que la caché queda poblada antes de que `populateCells` pinte las celdas.

- [ ] **Step 3: Pintar la miniatura en la celda semanal**

En `filledCell` (línea 324), sustituir el bloque que añade el título:

```java
        String title = item.recipeTitle() != null ? item.recipeTitle() : "Sin título";
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("menu-cell-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        cell.getChildren().add(titleLabel);
```

por:

```java
        String title = item.recipeTitle() != null ? item.recipeTitle() : "Sin título";
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("menu-cell-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        RecipeThumbnail thumb = new RecipeThumbnail(context, MENU_THUMB_SIZE);
        thumb.show(context.getRecipeRepository().coverUrlFor(item.recipeId()));
        HBox titleRow = new HBox(8, thumb, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        cell.getChildren().add(titleRow);
```

**No tocar `populateMonthCells`** (líneas 288-306), que hace algo parecido para la vista mensual: queda fuera de alcance.

- [ ] **Step 4: Añadir los imports que faltan en `WeeklyMenuView`**

`Pos` ya está importado (línea 5), igual que `Insets`, `VBox`, `Platform` y `Label`. Faltan los dos que usa el bloque nuevo:

```java
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
```

- [ ] **Step 5: Compilar y ejecutar la suite**

```powershell
mvn -f desktop/pom.xml test
```

Esperado: BUILD SUCCESS, 0 fallos.

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/WeeklyMenuView.java
git commit -m "feat(desktop): muestra la portada en el menu semanal"
```

---

### Task 5: Composable `RecipeThumb` y búsqueda global de Android

Android ya tiene el mapa de portadas: `viewModel.recipeCovers` (`RecetasViewModel.kt:84`) sale de Room y funciona offline. Solo hay que consumirlo.

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeCovers.kt`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/GlobalSearchScreen.kt`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt:565-577`

**Interfaces:**
- Consumes: `viewModel.recipeCovers: StateFlow<Map<String, String>>`
- Produces: `@Composable fun RecipeThumb(coverUrl: String?, size: Dp, modifier: Modifier = Modifier)`. La usa la Task 6.

- [ ] **Step 1: Añadir el composable a `RecipeCovers.kt`**

Añadir al final de `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeCovers.kt`, y añadir arriba los imports correspondientes:

```kotlin
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
```

```kotlin
/**
 * Miniatura cuadrada de portada, para los listados donde la card grande no cabe:
 * busqueda global y filas de comida del menu. Misma receta visual que RecipeCard,
 * en pequeno.
 *
 * Con coverUrl nulo deja el placeholder: nunca un hueco vacio, porque los titulos
 * quedarian en dos margenes distintos y el ojo pierde la columna al hacer scroll.
 */
@Composable
fun RecipeThumb(coverUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Icon(
            Icons.Outlined.Restaurant,
            contentDescription = null,
            modifier = Modifier.size(size / 2).align(Alignment.Center),
            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.30f)
        )
        Crossfade(targetState = coverUrl, label = "recipeThumbCover") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
```

El import es `coil3.compose.AsyncImage`, el mismo que ya usa `RecipeScreens.kt:112`. No añadas ninguna dependencia nueva.

- [ ] **Step 2: Aceptar el mapa de portadas en `GlobalSearchScreen`**

En `GlobalSearchScreen.kt`, añadir el parámetro a la firma (después de `recipes`):

```kotlin
internal fun GlobalSearchScreen(
    query: String,
    recipes: List<RecipeEntity>,
    recipeCovers: Map<String, String>,
    stockItems: List<StockItemEntity>,
    notes: List<FamilyNoteEntity>,
    modifier: Modifier,
    onNavigate: (MainTab) -> Unit
) {
```

- [ ] **Step 3: Pintar la miniatura en las filas de receta**

En el mismo archivo, dar a `SearchResultRow` un `leading` opcional:

```kotlin
@Composable
private fun SearchResultRow(
    title: String,
    meta: String,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (meta.isNotBlank()) {
            { Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        leadingContent = leading,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}
```

y usarlo solo en la sección de recetas (líneas 59-68):

```kotlin
            items(filteredRecipes, key = { "r_${it.id}" }) { recipe ->
                SearchResultRow(
                    title = recipe.title,
                    meta = listOfNotNull(
                        recipe.prepMinutes?.let { "$it min" },
                        recipe.difficulty
                    ).joinToString("  ·  "),
                    leading = { RecipeThumb(coverUrl = recipeCovers[recipe.id], size = 56.dp) },
                    onClick = { onNavigate(MainTab.RECIPES) }
                )
            }
```

Las llamadas de stock y notas se quedan igual: no pasan `leading`, así que siguen sin miniatura.

Añade el import `androidx.compose.ui.unit.dp` si no está.

- [ ] **Step 4: Pasar el mapa desde `RecetasApp`**

En `RecetasApp.kt`, colectar el flujo junto al resto de estados de la pantalla y pasarlo en la llamada de la línea 566:

```kotlin
    val recipeCovers by viewModel.recipeCovers.collectAsState()
```

```kotlin
            GlobalSearchScreen(
                query = searchQuery.trim(),
                recipes = recipes,
                recipeCovers = recipeCovers,
                stockItems = stockItems,
                notes = notes,
                modifier = Modifier.padding(padding),
                onNavigate = { selectedTab ->
                    searchActive = false
                    searchQuery = ""
                    tab = selectedTab
                }
```

Coloca el `collectAsState` en el mismo composable donde ya se colectan `recipes`, `stockItems` y `notes`, no dentro del `if (searchActive ...)`.

- [ ] **Step 5: Compilar y ejecutar los tests de Android**

```powershell
cd android
.\gradlew testDebugUnitTest
.\gradlew assembleDebug
cd ..
```

Esperado: BUILD SUCCESSFUL en ambos, 0 fallos. El número de tests no cambia: no hay lógica nueva que testear, `coversByRecipeId` ya está cubierta por `RecipeCoversTest`.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecipeCovers.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/GlobalSearchScreen.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt
git commit -m "feat(android): muestra la portada en la busqueda global"
```

---

### Task 6: Miniatura en el menú semanal de Android

**Files:**
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/MenuScreen.kt:62-71,139-148,200-277`
- Modify: `android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt:598-608`

**Interfaces:**
- Consumes: `RecipeThumb(coverUrl, size, modifier)` de la Task 5; `viewModel.recipeCovers`
- Produces: nada que consuman otras tasks

- [ ] **Step 1: Aceptar el mapa en `MenuScreen`**

En `MenuScreen.kt`, añadir el parámetro después de `recipes` (línea 64):

```kotlin
fun MenuScreen(
    menuItems: List<MenuItemEntity>,
    recipes: List<RecipeEntity>,
    recipeCovers: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onAssignToMenu: (date: String, recipeId: String, mealType: String) -> Unit = { _, _, _ -> },
    onRemoveFromMenu: (MenuItemEntity) -> Unit = {},
    onNavigateToRecipe: (String) -> Unit = {}
) {
```

- [ ] **Step 2: Propagarlo hasta `MealRow`**

En la llamada a `DayMenuCard` (línea 139), añadir el argumento:

```kotlin
                DayMenuCard(
                    date = "$dayName, ${date.format(dateFmt)}",
                    dayItems = byDay[dateKey] ?: emptyList(),
                    recipeCovers = recipeCovers,
                    isToday = date == today,
                    onAssign = {
                        assignToDate = dateKey
                        assignToDateLabel = "$dayName, ${date.format(dateFmt)}"
                    },
                    onMealTap = { optionsForItem = it }
                )
```

En la firma de `DayMenuCard` (línea 201), añadir el parámetro después de `dayItems`:

```kotlin
private fun DayMenuCard(
    date: String,
    dayItems: List<MenuItemEntity>,
    recipeCovers: Map<String, String>,
    isToday: Boolean,
    onAssign: () -> Unit,
    onMealTap: (MenuItemEntity) -> Unit
) {
```

y en la línea 245, pasarlo a `MealRow`:

```kotlin
                dayItems.forEach { item ->
                    MealRow(item, recipeCovers[item.recipeId], onClick = { onMealTap(item) })
                }
```

- [ ] **Step 3: Pintar la miniatura en `MealRow`**

Sustituir `MealRow` (líneas 251-277) por:

```kotlin
@Composable
private fun MealRow(item: MenuItemEntity, coverUrl: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (item.recipeId != null) {
            RecipeThumb(coverUrl = coverUrl, size = 48.dp)
        }
        Text(
            text = mealTypeLabel(item.mealType),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = item.recipeTitle ?: "(sin título)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (item.recipeId != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}
```

Una entrada de menú sin `recipeId` es texto libre, no una receta: no lleva miniatura ni chevron, igual que hoy.

Añade el import `androidx.compose.ui.unit.dp` si no está en el archivo.

- [ ] **Step 4: Pasar el mapa desde `RecetasApp`**

En la llamada a `MenuScreen` (`RecetasApp.kt:598`), añadir el argumento. `recipeCovers` ya está colectado por la Task 5:

```kotlin
                    MainTab.MENU     -> MenuScreen(
                        menuItems = menuItems, recipes = recipes,
                        recipeCovers = recipeCovers,
                        modifier = Modifier.padding(padding),
```

- [ ] **Step 5: Compilar y ejecutar los tests de Android**

```powershell
cd android
.\gradlew testDebugUnitTest
.\gradlew assembleDebug
cd ..
```

Esperado: BUILD SUCCESSFUL en ambos, 0 fallos.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/MenuScreen.kt android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/RecetasApp.kt
git commit -m "feat(android): muestra la portada en el menu semanal"
```

---

### Task 7: Validación integral y cierre

Ninguna de las dos plataformas tiene tests de UI, así que esta task **es** la verificación real del sprint. El bug que caza es el de la foto en la fila equivocada, que ningún test automático del repositorio detecta.

**Files:**
- Modify: `CONTINUAR.md`

**Interfaces:**
- Consumes: todo lo anterior
- Produces: nada

- [ ] **Step 1: Ejecutar las suites completas**

```powershell
mvn -f desktop/pom.xml test
cd android; .\gradlew testDebugUnitTest; .\gradlew assembleDebug; cd ..
```

Anota los números reales. Backend no se toca en este sprint, así que su suite no es obligatoria; si la ejecutas, dilo explícitamente.

- [ ] **Step 2: Ejecutar el escaneo de seguridad**

```powershell
pwsh -NoProfile -File scripts/security/run-security-scan.ps1 -Mode sprint
```

Esperado: exit 0. Los dos hallazgos conocidos de TruffleHog (`ServerUrlConfigTest.kt` y `ServerConfigTest.java`, credenciales inventadas en tests de parsing de URL) son falsos positivos ya documentados.

- [ ] **Step 3: Levantar el backend local contra la base de datos de test**

Desde `backend/`, **nunca contra `recetas_familiares`**:

```
DB_URL=$DB_TEST_URL DB_USERNAME=$DB_TEST_USERNAME DB_PASSWORD=$DB_TEST_PASSWORD JWT_SECRET=<32+ bytes> UPLOAD_DIR=./uploads-manual mvn spring-boot:run
```

Sembrar por API una cuenta de prueba con 24 recetas cuya portada sea una imagen de color distinto **con su número en grande**, una de cada seis deliberadamente sin foto, y un menú semanal con al menos dos comidas por día apuntando a recetas con y sin foto. Así una foto en la fila equivocada se ve al instante.

- [ ] **Step 4: Validar la GUI de Desktop**

Pilotar con `user32.dll` desde PowerShell (`SetCursorPos` + `mouse_event` para clic y rueda, `MoveWindow` para redimensionar) y capturar con `CopyFromScreen`. Cinco comprobaciones:

1. Listado principal de recetas: cada número coincide con su fila (verifica que la migración de `RecipeCell` no rompió nada).
2. Búsqueda global y "Recetas recientes" del dashboard: número correcto por fila, placeholder donde toca.
3. Menú semanal **abierto directamente al arrancar, sin pasar por "Recetas"**: las miniaturas aparecen. Este es el caso que cubre `ensureRecipeCacheLoaded`.
4. Scroll rápido del listado con la caché de imágenes fría (reiniciar la app la vacía): ninguna fila con foto ajena.
5. Cambio de familia: la familia B solo muestra sus propias fotos.

Trampas conocidas: el popup de un `ComboBox` de JavaFX es una ventana propia y solo se ve capturando la pantalla entera; con la ventana estrecha el sidebar colapsa y desaparece la entrada "Recetas"; `0xFFFFFFFF` en PowerShell es `Int32` = -1, hace falta `0xFFFFFFFFL` para la máscara de la rueda.

- [ ] **Step 5: Validar la GUI de Android**

Emulador contra el mismo backend local, pilotado con `adb input tap` / `adb input text` y capturas con `adb exec-out screencap`. Tres comprobaciones: búsqueda global con miniatura correcta por fila, menú semanal con miniatura por comida, y una entrada de menú sin receta que no debe mostrar miniatura ni chevron.

- [ ] **Step 6: Limpiar los datos de prueba**

Borrar la cuenta de prueba por API (`DELETE /auth/account`), borrar `backend/uploads-manual/` y restaurar la URL de producción en las preferencias de la aplicación de Desktop.

- [ ] **Step 7: Actualizar `CONTINUAR.md`**

Añadir la sección de cierre del sprint con: qué se implementó por plataforma, la corrección al backlog (no existe pantalla de favoritos), el defecto de la caché compartida que obligó a `ensureRecipeCacheLoaded`, los números reales de las validaciones ejecutadas, y el riesgo residual — sin tests de UI automatizados, y en el menú de Desktop las recetas más allá de la primera página de 100 no tendrán miniatura hasta que el listado las cargue.

El bloque "Punto de retoma — cierre de sesión del 2026-08-01 (noche)" ya está en el archivo sin commitear desde la sesión anterior: entra en este mismo commit.

- [ ] **Step 8: Commit de cierre**

```bash
git add CONTINUAR.md
git commit -m "docs: cierra el sprint de portada en el resto de listados"
```

---

## Autorrevisión del plan

**Cobertura de la spec.** Las cinco pantallas del alcance tienen task: búsqueda global de Desktop y dashboard (Task 3), menú de Desktop (Task 4), búsqueda global de Android (Task 5), menú de Android (Task 6). El componente compartido y la migración de `RecipeCell` son la Task 2, la lógica de cruce la Task 1, y el repoblado de caché descrito en la spec está en la Task 4, Steps 1-2. Los tamaños (56/48 dp en Android, 56/40 px en Desktop), el placeholder obligatorio, el respeto a `MotionPreferences` y la prohibición de clientes HTTP nuevos están en las restricciones globales y en el código de cada task. Los no-objetivos —selectores de receta, vista mensual, backend, iOS— están marcados como intocables.

**Consistencia de tipos.** `coverUrlFor(String): String` (Task 1) se consume en la Task 4 con ese nombre exacto. `RecipeThumbnail(AppContext, double)` + `show(String)` + `clear()` (Task 2) se consumen con esas firmas en las tasks 2, 3 y 4. `RecipeThumb(coverUrl: String?, size: Dp, modifier: Modifier)` (Task 5) se consume igual en la Task 6. `recipeCovers: Map<String, String>` viaja con el mismo tipo desde `RecetasViewModel` hasta `MealRow`.
