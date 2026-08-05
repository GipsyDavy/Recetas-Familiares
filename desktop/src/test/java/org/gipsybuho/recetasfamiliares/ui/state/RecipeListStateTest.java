package org.gipsybuho.recetasfamiliares.ui.state;

import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Estado del listado de recetas extraido de RecipeListView: paginacion, filtro
 * local y textos de estado. Sin JavaFX, para poder fijar los bordes que la vista
 * mezclaba con hilos virtuales y Platform.runLater.
 */
class RecipeListStateTest {

    // --- Paginacion ---

    @Test
    void sinRecetasNoHayPaginaSiguiente() {
        RecipeListState state = new RecipeListState();
        state.onFirstPageLoaded(0);

        assertEquals(0, state.currentPage());
        assertFalse(state.hasMore());
    }

    @Test
    void conUnaSolaPaginaNoHayPaginaSiguiente() {
        RecipeListState state = new RecipeListState();
        state.onFirstPageLoaded(1);

        assertFalse(state.hasMore());
    }

    @Test
    void conDosPaginasSeOfreceLaSegundaYSeAgotaAlCargarla() {
        RecipeListState state = new RecipeListState();
        state.onFirstPageLoaded(2);
        assertTrue(state.hasMore());
        assertEquals(1, state.nextPage());

        state.onNextPageLoaded(1, 2);
        assertEquals(1, state.currentPage());
        assertFalse(state.hasMore(), "Cargada la ultima pagina, no debe ofrecerse otra");
    }

    @Test
    void recorreVariasPaginasHastaAgotarlas() {
        RecipeListState state = new RecipeListState();
        state.onFirstPageLoaded(4);

        state.onNextPageLoaded(1, 4);
        assertTrue(state.hasMore());
        state.onNextPageLoaded(2, 4);
        assertTrue(state.hasMore());
        state.onNextPageLoaded(3, 4);

        assertFalse(state.hasMore());
        assertEquals(3, state.currentPage());
    }

    @Test
    void recargarDevuelveElEstadoAlPrincipio() {
        RecipeListState state = new RecipeListState();
        state.onFirstPageLoaded(3);
        state.onNextPageLoaded(1, 3);

        state.reset();

        assertEquals(0, state.currentPage());
        assertFalse(state.hasMore());
    }

    // --- Contenido de las paginas ---

    @Test
    void laPrimeraPaginaDescartaLasRecetasBorradas() {
        List<RecipeDtos.RecipeDto> page = List.of(recipe("r1", "Cocido", false), recipe("r2", "Sopa", true));

        assertEquals(List.of("r1"), ids(RecipeListState.firstPage(page)));
    }

    @Test
    void anadirPaginaConservaLoAnteriorYDescartaBorradas() {
        List<RecipeDtos.RecipeDto> existing = List.of(recipe("r1", "Cocido", false));
        List<RecipeDtos.RecipeDto> page = List.of(recipe("r2", "Sopa", false), recipe("r3", "Flan", true));

        assertEquals(List.of("r1", "r2"), ids(RecipeListState.appendPage(existing, page)));
    }

    @Test
    void anadirPaginaNoDuplicaLoQueYaEstabaEnLaCache() {
        // La cache es compartida: el menu semanal puede haber traido ya recetas de
        // la pagina que el listado esta a punto de anadir. Sin deduplicar, el
        // usuario veria la misma receta dos veces.
        List<RecipeDtos.RecipeDto> existing = List.of(recipe("r1", "Cocido", false), recipe("r2", "Sopa", false));
        List<RecipeDtos.RecipeDto> page = List.of(recipe("r2", "Sopa", false), recipe("r3", "Flan", false));

        assertEquals(List.of("r1", "r2", "r3"), ids(RecipeListState.appendPage(existing, page)));
    }

    // --- Filtro local ---

    @Test
    void elFiltroIgnoraMayusculasYEncuentraPorFragmento() {
        RecipeDtos.RecipeDto cocido = recipe("r1", "Cocido madrileño", false);

        assertTrue(RecipeListState.matchesQuery(cocido, "cocido"));
        assertTrue(RecipeListState.matchesQuery(cocido, "MADRILEÑO"));
        assertTrue(RecipeListState.matchesQuery(cocido, "dri"));
        assertFalse(RecipeListState.matchesQuery(cocido, "paella"));
    }

    @Test
    void elFiltroVacioAceptaCualquierReceta() {
        RecipeDtos.RecipeDto cocido = recipe("r1", "Cocido", false);

        assertTrue(RecipeListState.matchesQuery(cocido, null));
        assertTrue(RecipeListState.matchesQuery(cocido, "   "));
    }

    @Test
    void unaRecetaSinTituloNuncaCoincide() {
        assertFalse(RecipeListState.matchesQuery(recipe("r1", null, false), "cocido"));
    }

    // --- Interaccion entre filtro y boton de cargar mas ---

    @Test
    void elBotonDeCargarMasSeOcultaMientrasHayFiltroActivo() {
        RecipeListState state = new RecipeListState();
        state.onFirstPageLoaded(5);

        assertTrue(state.shouldShowLoadMore(null));
        assertTrue(state.shouldShowLoadMore("  "));
        assertFalse(state.shouldShowLoadMore("cocido"),
                "Con filtro activo, cargar mas paginas confundiria el recuento");
    }

    @Test
    void elBotonDeCargarMasNoApareceSiNoHayMasPaginas() {
        RecipeListState state = new RecipeListState();
        state.onFirstPageLoaded(1);

        assertFalse(state.shouldShowLoadMore(null));
    }

    // --- Texto de estado ---

    @Test
    void sinFiltroElEstadoMuestraElTotal() {
        assertEquals("12 recetas", RecipeListState.statusText(12, 12, null));
        assertEquals("0 recetas", RecipeListState.statusText(0, 0, "   "));
    }

    @Test
    void conFiltroElEstadoMuestraLoMostradoSobreElTotal() {
        assertEquals("Mostrando 3 de 12", RecipeListState.statusText(12, 3, "sopa"));
    }

    private static List<String> ids(List<RecipeDtos.RecipeDto> recipes) {
        return recipes.stream().map(RecipeDtos.RecipeDto::id).toList();
    }

    private static RecipeDtos.RecipeDto recipe(String id, String title, boolean deleted) {
        return new RecipeDtos.RecipeDto(
                id, "fam-1", title, null,
                null, null, null, null,
                "2026-08-05T10:00:00Z", "2026-08-05T10:00:00Z", 1L, deleted,
                null, null, null);
    }
}
