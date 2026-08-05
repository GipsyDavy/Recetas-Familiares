package org.gipsybuho.recetasfamiliares.ui.state;

import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;

import java.util.List;

/**
 * Resultado del filtrado de la busqueda global, sin JavaFX.
 *
 * Vive aparte de GlobalSearchView porque alli el filtrado estaba mezclado con la
 * construccion de nodos y no habia forma de probarlo.
 */
public record GlobalSearchResults(
        List<RecipeDtos.RecipeDto> recipes,
        List<StockDtos.StockItemDto> stock,
        List<SyncDtos.NoteDtos.FamilyNoteDto> notes
) {

    private static final int NOTE_PREVIEW_LENGTH = 80;

    public static GlobalSearchResults from(
            List<RecipeDtos.RecipeDto> recipes,
            List<StockDtos.StockItemDto> stock,
            List<SyncDtos.NoteDtos.FamilyNoteDto> notes,
            String rawQuery
    ) {
        String lower = (rawQuery == null ? "" : rawQuery.trim()).toLowerCase();

        return new GlobalSearchResults(
                recipes.stream()
                        .filter(r -> matches(r.title(), lower) || matches(r.description(), lower))
                        .toList(),
                stock.stream()
                        .filter(s -> matches(s.name(), lower))
                        .toList(),
                notes.stream()
                        .filter(n -> matches(n.title(), lower) || matches(n.body(), lower))
                        .toList()
        );
    }

    public boolean isEmpty() {
        return recipes.isEmpty() && stock.isEmpty() && notes.isEmpty();
    }

    /** Cuerpo de la nota en una sola linea, recortado para la fila de resultado. */
    public static String notePreview(String body) {
        if (body == null) {
            return "";
        }
        // El limite se calcula sobre la cadena ya colapsada, no sobre el cuerpo
        // original: colapsar los espacios la acorta, y usar la longitud original
        // lanzaba StringIndexOutOfBoundsException con cualquier nota con parrafos.
        String collapsed = body.replaceAll("\\s+", " ");
        return collapsed.substring(0, Math.min(NOTE_PREVIEW_LENGTH, collapsed.length()));
    }

    private static boolean matches(String field, String lower) {
        return field != null && field.toLowerCase().contains(lower);
    }
}
