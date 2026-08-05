package org.gipsybuho.recetasfamiliares.ui.state;

import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Estado y reglas del listado de recetas, sin JavaFX.
 *
 * Vive aparte de RecipeListView para poder testear la paginacion y el filtro sin
 * arrancar el toolkit: en la vista esta logica estaba entrelazada con hilos
 * virtuales, Platform.runLater y nodos, y no habia forma de fijar sus bordes.
 *
 * No es thread-safe. La vista la usa siempre desde el JavaFX Application Thread,
 * dentro de los Platform.runLater que aplican la respuesta de red.
 */
public final class RecipeListState {

    private int currentPage = 0;
    private boolean hasMore = false;

    public int currentPage() {
        return currentPage;
    }

    public boolean hasMore() {
        return hasMore;
    }

    /** Pagina que pedira la siguiente pulsacion de "Cargar mas". */
    public int nextPage() {
        return currentPage + 1;
    }

    /** Vuelve al estado inicial: lo que hace "Actualizar" antes de pedir la pagina 0. */
    public void reset() {
        currentPage = 0;
        hasMore = false;
    }

    public void onFirstPageLoaded(int totalPages) {
        currentPage = 0;
        hasMore = totalPages > 1;
    }

    public void onNextPageLoaded(int loadedPage, int totalPages) {
        currentPage = loadedPage;
        hasMore = loadedPage < totalPages - 1;
    }

    /**
     * Con un filtro activo la lista muestra un subconjunto, asi que ofrecer mas
     * paginas descuadraria el recuento que ve el usuario.
     */
    public boolean shouldShowLoadMore(String query) {
        return hasMore && isBlank(query);
    }

    /** Contenido de la cache tras cargar la primera pagina. */
    public static List<RecipeDtos.RecipeDto> firstPage(List<RecipeDtos.RecipeDto> items) {
        return items.stream().filter(r -> !r.deleted()).toList();
    }

    /**
     * Contenido de la cache tras "Cargar mas".
     *
     * Deduplica por id porque la cache de recetas es compartida: el menu semanal
     * repuebla la suya con su propia pagina, asi que una receta de la pagina que
     * se anade ahora puede estar ya presente. Sin deduplicar, aparecia dos veces
     * en el listado.
     */
    public static List<RecipeDtos.RecipeDto> appendPage(
            List<RecipeDtos.RecipeDto> existing,
            List<RecipeDtos.RecipeDto> items
    ) {
        LinkedHashMap<String, RecipeDtos.RecipeDto> byId = new LinkedHashMap<>();
        for (RecipeDtos.RecipeDto recipe : existing) {
            byId.put(recipe.id(), recipe);
        }
        for (RecipeDtos.RecipeDto recipe : items) {
            if (!recipe.deleted()) {
                byId.putIfAbsent(recipe.id(), recipe);
            }
        }
        return new ArrayList<>(byId.values());
    }

    /** Filtro local por titulo. Una consulta vacia acepta cualquier receta. */
    public static boolean matchesQuery(RecipeDtos.RecipeDto recipe, String query) {
        if (isBlank(query)) {
            return true;
        }
        return recipe.title() != null
                && recipe.title().toLowerCase().contains(query.toLowerCase());
    }

    public static String statusText(int total, int shown, String query) {
        return isBlank(query)
                ? total + " recetas"
                : "Mostrando " + shown + " de " + total;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
