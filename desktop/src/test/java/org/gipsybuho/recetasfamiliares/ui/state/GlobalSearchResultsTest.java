package org.gipsybuho.recetasfamiliares.ui.state;

import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filtrado de la busqueda global, extraido de GlobalSearchView para poder fijarlo
 * sin construir nodos. Incluye el corte de la vista previa de nota, que hasta
 * ahora podia lanzar StringIndexOutOfBoundsException y tumbar la busqueda entera.
 */
class GlobalSearchResultsTest {

    // --- Vista previa de nota: el bug ---

    @Test
    void laVistaPreviaNoRevientaConEspaciosRepetidos() {
        // El cuerpo mide mas de 80 caracteres, pero al colapsar los espacios baja
        // de 80. Calcular el limite sobre el cuerpo original y aplicarlo a la
        // cadena colapsada lanzaba StringIndexOutOfBoundsException.
        String body = "Receta de la abuela" + " ".repeat(70) + "con mucho carino";

        String preview = GlobalSearchResults.notePreview(body);

        assertTrue(preview.length() <= 80);
        assertEquals("Receta de la abuela con mucho carino", preview);
    }

    @Test
    void laVistaPreviaNoRevientaConSaltosDeLinea() {
        String body = "Primer parrafo de la nota familiar.\n\n\n"
                + "Segundo parrafo que continua.\n\n\n"
                + "Tercero.";

        String preview = GlobalSearchResults.notePreview(body);

        assertTrue(preview.length() <= 80);
        assertFalse(preview.contains("\n"), "La vista previa va en una sola linea");
    }

    @Test
    void laVistaPreviaCortaALosOchentaCaracteres() {
        String preview = GlobalSearchResults.notePreview("a".repeat(200));

        assertEquals(80, preview.length());
    }

    @Test
    void unaNotaCortaNoSeCorta() {
        assertEquals("Comprar pan", GlobalSearchResults.notePreview("Comprar pan"));
    }

    @Test
    void unaNotaSinCuerpoDaVistaPreviaVacia() {
        assertEquals("", GlobalSearchResults.notePreview(null));
        assertEquals("", GlobalSearchResults.notePreview(""));
    }

    // --- Filtrado ---

    @Test
    void encuentraRecetasPorTituloYPorDescripcion() {
        var results = GlobalSearchResults.from(
                List.of(recipe("r1", "Cocido", "plato de invierno"),
                        recipe("r2", "Paella", "arroz con azafran"),
                        recipe("r3", "Flan", "postre")),
                List.of(), List.of(), "arroz");

        assertEquals(List.of("r2"), results.recipes().stream().map(RecipeDtos.RecipeDto::id).toList());
    }

    @Test
    void laBusquedaIgnoraMayusculasYEspaciosAlrededor() {
        var results = GlobalSearchResults.from(
                List.of(recipe("r1", "Cocido madrileño", null)),
                List.of(stock("s1", "Garbanzos")),
                List.of(note("n1", "Cocido", "receta de la abuela")),
                "  COCIDO  ");

        assertEquals(1, results.recipes().size());
        assertEquals(1, results.notes().size());
        assertTrue(results.stock().isEmpty());
    }

    @Test
    void encuentraStockPorNombre() {
        var results = GlobalSearchResults.from(
                List.of(), List.of(stock("s1", "Garbanzos"), stock("s2", "Lentejas")), List.of(), "lente");

        assertEquals(List.of("s2"), results.stock().stream().map(StockDtos.StockItemDto::id).toList());
    }

    @Test
    void encuentraNotasPorTituloYPorCuerpo() {
        var results = GlobalSearchResults.from(
                List.of(), List.of(),
                List.of(note("n1", "Compra", "faltan huevos"), note("n2", "Cumple", "tarta")),
                "huevos");

        assertEquals(List.of("n1"), results.notes().stream().map(SyncDtos.NoteDtos.FamilyNoteDto::id).toList());
    }

    @Test
    void losCamposNulosNuncaCoinciden() {
        var results = GlobalSearchResults.from(
                List.of(recipe("r1", null, null)), List.of(), List.of(note("n1", null, null)), "algo");

        assertTrue(results.isEmpty());
    }

    @Test
    void sinCoincidenciasElResultadoEstaVacio() {
        var results = GlobalSearchResults.from(
                List.of(recipe("r1", "Cocido", null)),
                List.of(stock("s1", "Garbanzos")),
                List.of(note("n1", "Compra", "pan")),
                "sushi");

        assertTrue(results.isEmpty());
    }

    @Test
    void unaConsultaVaciaDevuelveTodo() {
        // Comportamiento actual de la vista: sin texto, la busqueda global lista
        // todo lo cacheado en vez de no mostrar nada.
        var results = GlobalSearchResults.from(
                List.of(recipe("r1", "Cocido", null)), List.of(stock("s1", "Garbanzos")), List.of(), "");

        assertEquals(1, results.recipes().size());
        assertEquals(1, results.stock().size());
        assertFalse(results.isEmpty());
    }

    @Test
    void unaConsultaNulaSeTrataComoVacia() {
        var results = GlobalSearchResults.from(List.of(recipe("r1", "Cocido", null)), List.of(), List.of(), null);

        assertEquals(1, results.recipes().size());
    }

    private static RecipeDtos.RecipeDto recipe(String id, String title, String description) {
        return new RecipeDtos.RecipeDto(
                id, "fam-1", title, description,
                null, null, null, null,
                "2026-08-05T10:00:00Z", "2026-08-05T10:00:00Z", 1L, false,
                null, null, null);
    }

    private static StockDtos.StockItemDto stock(String id, String name) {
        return new StockDtos.StockItemDto(
                id, "fam-1", name, 1.0, "kg", null, null, null,
                "2026-08-05T10:00:00Z", 1L, false);
    }

    private static SyncDtos.NoteDtos.FamilyNoteDto note(String id, String title, String body) {
        return new SyncDtos.NoteDtos.FamilyNoteDto(
                id, "fam-1", null, null, title, body, false,
                "2026-08-05T10:00:00Z", "2026-08-05T10:00:00Z", 1L, false);
    }
}
