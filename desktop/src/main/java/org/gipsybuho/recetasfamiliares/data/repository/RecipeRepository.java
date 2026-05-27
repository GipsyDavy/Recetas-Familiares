package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.gipsybuho.recetasfamiliares.data.cache.SimpleCache;

import java.util.List;

public class RecipeRepository {

    private final ApiClient api;
    private final AppSession session;
    private final SimpleCache<RecipeDtos.RecipeDto> cache = new SimpleCache<>();
    private final SimpleCache<RecipeDtos.RecipeIngredientDto> ingredientCache = new SimpleCache<>();
    private final SimpleCache<RecipeDtos.RecipeStepDto> stepCache = new SimpleCache<>();

    public RecipeRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public SimpleCache<RecipeDtos.RecipeDto> getCache() { return cache; }
    public SimpleCache<RecipeDtos.RecipeIngredientDto> getIngredientCache() { return ingredientCache; }
    public SimpleCache<RecipeDtos.RecipeStepDto> getStepCache() { return stepCache; }

    /** Load paginated recipes into cache. */
    public RecipeDtos.RecipePageResponse loadPage(int page, int size) throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/recipes?page=" + page + "&size=" + size;
        return api.get(path, RecipeDtos.RecipePageResponse.class);
    }

    /** Load a single recipe's ingredients. */
    public List<RecipeDtos.RecipeIngredientDto> loadIngredients(String recipeId) throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/recipes/" + recipeId + "/ingredients";
        RecipeDtos.RecipeIngredientDto[] result = api.get(path, RecipeDtos.RecipeIngredientDto[].class);
        return List.of(result);
    }

    /** Load a single recipe's steps. */
    public List<RecipeDtos.RecipeStepDto> loadSteps(String recipeId) throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/recipes/" + recipeId + "/steps";
        RecipeDtos.RecipeStepDto[] result = api.get(path, RecipeDtos.RecipeStepDto[].class);
        return List.of(result);
    }

    /** Replace recipe cache with data received from a sync pull. */
    public void updateFromSync(
            List<RecipeDtos.RecipeDto> recipes,
            List<RecipeDtos.RecipeIngredientDto> ingredients,
            List<RecipeDtos.RecipeStepDto> steps
    ) {
        if (recipes != null) cache.replaceAll(recipes.stream().filter(r -> !r.deleted()).toList());
        if (ingredients != null) ingredientCache.replaceAll(ingredients.stream().filter(i -> !i.deleted()).toList());
        if (steps != null) stepCache.replaceAll(steps.stream().filter(s -> !s.deleted()).toList());
    }
}
