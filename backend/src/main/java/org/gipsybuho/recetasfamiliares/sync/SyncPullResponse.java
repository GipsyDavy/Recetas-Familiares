package org.gipsybuho.recetasfamiliares.sync;

import java.time.Instant;
import java.util.List;

import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientResponse;
import org.gipsybuho.recetasfamiliares.recipes.RecipeResponse;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepResponse;
import org.gipsybuho.recetasfamiliares.stock.StockItemResponse;

public record SyncPullResponse(
        Instant serverTime,
        List<RecipeResponse> recipes,
        List<RecipeIngredientResponse> ingredients,
        List<RecipeStepResponse> steps,
        List<StockItemResponse> stockItems
) {
}
