package org.gipsybuho.recetasfamiliares.recipes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.gipsybuho.recetasfamiliares.common.api.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/families/{familyId}/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    public PageResponse<RecipeResponse> listRecipes(
            @PathVariable String familyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return recipeService.listRecipes(familyId, authentication.getName(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeResponse createRecipe(
            @PathVariable String familyId,
            @Valid @RequestBody CreateRecipeRequest request,
            Authentication authentication
    ) {
        return recipeService.createRecipe(familyId, authentication.getName(), request);
    }

    @GetMapping("/{recipeId}")
    public RecipeResponse getRecipe(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            Authentication authentication
    ) {
        return recipeService.getRecipe(familyId, recipeId, authentication.getName());
    }

    @PutMapping("/{recipeId}")
    public RecipeResponse updateRecipe(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @Valid @RequestBody UpdateRecipeRequest request,
            Authentication authentication
    ) {
        return recipeService.updateRecipe(familyId, recipeId, authentication.getName(), request);
    }

    @DeleteMapping("/{recipeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecipe(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            Authentication authentication
    ) {
        recipeService.deleteRecipe(familyId, recipeId, authentication.getName());
    }
}
