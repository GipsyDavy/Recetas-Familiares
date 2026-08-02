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
