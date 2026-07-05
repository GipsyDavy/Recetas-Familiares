package org.gipsybuho.recetasfamiliares.sync;

import java.time.Instant;
import java.util.List;

import org.gipsybuho.recetasfamiliares.favorites.FavoriteRecipeResponse;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteResponse;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoResponse;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientResponse;
import org.gipsybuho.recetasfamiliares.recipes.RecipeResponse;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepResponse;
import org.gipsybuho.recetasfamiliares.menus.MenuItemResponse;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListItemResponse;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListResponse;
import org.gipsybuho.recetasfamiliares.stock.StockItemResponse;

public record SyncPullResponse(
        Instant serverTime,
        List<RecipeResponse> recipes,
        List<RecipeIngredientResponse> ingredients,
        List<RecipeStepResponse> steps,
        List<StockItemResponse> stockItems,
        List<MenuItemResponse> menuItems,
        List<ShoppingListResponse> shoppingLists,
        List<ShoppingListItemResponse> shoppingListItems,
        List<FavoriteRecipeResponse> favoriteRecipes,
        List<FamilyNoteResponse> familyNotes,
        List<RecipePhotoResponse> recipePhotos,
        boolean hasMore,
        Instant nextSince
) {

    /** Respuesta completa sin paginacion (push y pull sin limit). */
    public SyncPullResponse(
            Instant serverTime,
            List<RecipeResponse> recipes,
            List<RecipeIngredientResponse> ingredients,
            List<RecipeStepResponse> steps,
            List<StockItemResponse> stockItems,
            List<MenuItemResponse> menuItems,
            List<ShoppingListResponse> shoppingLists,
            List<ShoppingListItemResponse> shoppingListItems,
            List<FavoriteRecipeResponse> favoriteRecipes,
            List<FamilyNoteResponse> familyNotes,
            List<RecipePhotoResponse> recipePhotos
    ) {
        this(serverTime, recipes, ingredients, steps, stockItems, menuItems, shoppingLists,
                shoppingListItems, favoriteRecipes, familyNotes, recipePhotos, false, null);
    }
}
