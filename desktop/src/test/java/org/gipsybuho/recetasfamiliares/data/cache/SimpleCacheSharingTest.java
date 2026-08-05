package org.gipsybuho.recetasfamiliares.data.cache;

import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La cache de recetas es compartida: RecipeListView escribe en ella un replaceAll
 * de su pagina, y WeeklyMenuView repuebla la suya con mergeById en cada apertura
 * del menu. Estos tests fijan que convivir en la misma cache no destruya datos,
 * que es exactamente lo que fallo en el sprint del 02/08: el menu solo repoblaba
 * si la cache estaba vacia, asi que tras pasar por "Recetas" quedaba parcial pero
 * no vacia, la repoblacion se saltaba, y toda receta fuera de esa pagina aparecia
 * sin portada.
 */
class SimpleCacheSharingTest {

    @Test
    void elMenuNoPisaLaPaginacionQueElListadoYaHabiaAcumulado() {
        SimpleCache<RecipeDtos.RecipeDto> cache = new SimpleCache<>();

        // RecipeListView tras varias pulsaciones de "Cargar mas": 120 recetas,
        // mas de las 100 que carga la pagina del menu.
        cache.replaceAll(recipes(0, 120));

        // WeeklyMenuView al abrirse repuebla con su propia pagina de 100. Si esto
        // fuese destructivo, las 20 ultimas del listado desapareceran: es la carrera
        // que el mergeById del 02/08 vino a cerrar.
        cache.mergeById(recipes(0, 100), RecipeDtos.RecipeDto::id, RecipeDtos.RecipeDto::deleted);

        assertEquals(120, cache.getItems().size(),
                "El menu no debe recortar lo que el listado ya tenia paginado");
        assertTrue(ids(cache).containsAll(ids(recipes(100, 120))),
                "Las recetas fuera de la pagina del menu deben sobrevivir");
    }

    @Test
    void mergeTrasReplaceAllParcialNoPierdeNingunaReceta() {
        SimpleCache<RecipeDtos.RecipeDto> cache = new SimpleCache<>();

        // RecipeListView: primera pagina de 30 al navegar a "Recetas".
        cache.replaceAll(recipes(0, 30));

        // WeeklyMenuView: su propia pagina de 100 al abrir el menu.
        cache.mergeById(recipes(0, 100), RecipeDtos.RecipeDto::id, RecipeDtos.RecipeDto::deleted);

        assertEquals(100, cache.getItems().size());
        assertTrue(ids(cache).containsAll(ids(recipes(0, 100))),
                "El merge debe conservar tanto la pagina del listado como la del menu");
    }

    @Test
    void replaceAllPosteriorNoDejaLaCacheInconsistente() {
        SimpleCache<RecipeDtos.RecipeDto> cache = new SimpleCache<>();
        cache.mergeById(recipes(0, 100), RecipeDtos.RecipeDto::id, RecipeDtos.RecipeDto::deleted);

        // Volver a "Recetas" reduce la cache a su pagina: comportamiento actual,
        // documentado como residual. Reabrir el menu vuelve a completarla.
        cache.replaceAll(recipes(0, 30));
        assertEquals(30, cache.getItems().size());

        cache.mergeById(recipes(0, 100), RecipeDtos.RecipeDto::id, RecipeDtos.RecipeDto::deleted);
        assertEquals(100, cache.getItems().size());
    }

    @Test
    void mergeEsIdempotente() {
        SimpleCache<RecipeDtos.RecipeDto> cache = new SimpleCache<>();
        List<RecipeDtos.RecipeDto> page = recipes(0, 5);

        cache.mergeById(page, RecipeDtos.RecipeDto::id, RecipeDtos.RecipeDto::deleted);
        List<String> trasElPrimero = ids(cache);
        cache.mergeById(page, RecipeDtos.RecipeDto::id, RecipeDtos.RecipeDto::deleted);

        assertEquals(trasElPrimero, ids(cache), "Repetir el merge no debe duplicar ni reordenar");
    }

    @Test
    void mergeSobreCacheVaciaEquivaleAReplaceAll() {
        SimpleCache<RecipeDtos.RecipeDto> conMerge = new SimpleCache<>();
        SimpleCache<RecipeDtos.RecipeDto> conReplace = new SimpleCache<>();
        List<RecipeDtos.RecipeDto> page = recipes(0, 10);

        conMerge.mergeById(page, RecipeDtos.RecipeDto::id, RecipeDtos.RecipeDto::deleted);
        conReplace.replaceAll(page);

        assertEquals(ids(conReplace), ids(conMerge));
    }

    @Test
    void mergeRetiraLasRecetasBorradasYConservaElResto() {
        SimpleCache<RecipeDtos.RecipeDto> cache = new SimpleCache<>();
        cache.replaceAll(recipes(0, 3));

        cache.mergeById(List.of(recipe(1, true)), RecipeDtos.RecipeDto::id, RecipeDtos.RecipeDto::deleted);

        assertEquals(List.of("r0", "r2"), ids(cache));
    }

    @Test
    void elMergeConservaElOrdenPrevioYAnadeLoNuevoAlFinal() {
        SimpleCache<RecipeDtos.RecipeDto> cache = new SimpleCache<>();
        cache.replaceAll(List.of(recipe(2, false), recipe(0, false)));

        cache.mergeById(List.of(recipe(0, false), recipe(1, false)),
                RecipeDtos.RecipeDto::id, RecipeDtos.RecipeDto::deleted);

        // r2 y r0 mantienen su posicion aunque r0 venga tambien en el delta;
        // r1, que es nueva, se anade detras.
        assertEquals(List.of("r2", "r0", "r1"), ids(cache));
    }

    private static List<String> ids(SimpleCache<RecipeDtos.RecipeDto> cache) {
        return cache.getItems().stream().map(RecipeDtos.RecipeDto::id).toList();
    }

    private static List<String> ids(List<RecipeDtos.RecipeDto> recipes) {
        return recipes.stream().map(RecipeDtos.RecipeDto::id).toList();
    }

    private static List<RecipeDtos.RecipeDto> recipes(int fromInclusive, int toExclusive) {
        return IntStream.range(fromInclusive, toExclusive)
                .mapToObj(i -> recipe(i, false))
                .toList();
    }

    private static RecipeDtos.RecipeDto recipe(int index, boolean deleted) {
        return new RecipeDtos.RecipeDto(
                "r" + index, "fam-1", "Receta " + index, null,
                null, null, null, null,
                "2026-08-05T10:00:00Z", "2026-08-05T10:00:00Z", 1L, deleted,
                null, null, "https://api.example.test/uploads/recipe_thumbnails/" + index + ".jpg");
    }
}
