package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica la actualizacion de caches desde sync/pull: filtrado de tombstones
 * (deleted=true) y semantica incremental (lista null no toca la cache previa).
 */
class UpdateFromSyncTest {

    @Test
    void updateFromSyncFiltraRecetasBorradas() {
        RecipeRepository repo = new RecipeRepository(null, null);

        repo.updateFromSync(
                List.of(recipe("r1", false), recipe("r2", true)),
                List.of(ingredient("i1", "r1", false), ingredient("i2", "r1", true)),
                List.of(step("s1", "r1", true))
        );

        assertEquals(List.of("r1"), repo.getCache().getItems().stream().map(RecipeDtos.RecipeDto::id).toList());
        assertEquals(List.of("i1"), repo.getIngredientCache().getItems().stream().map(RecipeDtos.RecipeIngredientDto::id).toList());
        assertTrue(repo.getStepCache().isEmpty());
    }

    @Test
    void updateFromSyncConListaNullConservaCachePrevia() {
        RecipeRepository repo = new RecipeRepository(null, null);
        repo.updateFromSync(List.of(recipe("r1", false)), null, null);

        repo.updateFromSync(null, null, null);

        assertEquals(1, repo.getCache().getItems().size());
        assertEquals("r1", repo.getCache().getItems().get(0).id());
    }

    @Test
    void updateFromSyncStockFiltraTombstonesYRespetaNull() {
        StockRepository repo = new StockRepository(null, null);

        repo.updateFromSync(List.of(stock("s1", false), stock("s2", true)));
        assertEquals(List.of("s1"), repo.getCache().getItems().stream().map(StockDtos.StockItemDto::id).toList());

        repo.updateFromSync(null);
        assertEquals(1, repo.getCache().getItems().size());
    }

    @Test
    void cacheExpuestaEsInmutableParaLaUi() {
        StockRepository repo = new StockRepository(null, null);
        repo.updateFromSync(List.of(stock("s1", false)));

        assertThrows(UnsupportedOperationException.class,
                () -> repo.getCache().getItems().add(stock("hack", false)));
    }

    private static RecipeDtos.RecipeDto recipe(String id, boolean deleted) {
        return new RecipeDtos.RecipeDto(id, "fam-1", "t", null, null, null, null, null,
                "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", 1L, deleted);
    }

    private static RecipeDtos.RecipeIngredientDto ingredient(String id, String recipeId, boolean deleted) {
        return new RecipeDtos.RecipeIngredientDto(id, recipeId, 1, "n", null, null, null,
                "2026-01-01T00:00:00Z", 1L, deleted);
    }

    private static RecipeDtos.RecipeStepDto step(String id, String recipeId, boolean deleted) {
        return new RecipeDtos.RecipeStepDto(id, recipeId, 1, "hacer", null,
                "2026-01-01T00:00:00Z", 1L, deleted);
    }

    private static StockDtos.StockItemDto stock(String id, boolean deleted) {
        return new StockDtos.StockItemDto(id, "fam-1", "Harina", 1.0, "kg", null, null, null,
                "2026-01-01T00:00:00Z", 1L, deleted);
    }
}
