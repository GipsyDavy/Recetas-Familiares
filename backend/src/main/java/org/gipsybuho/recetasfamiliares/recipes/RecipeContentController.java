package org.gipsybuho.recetasfamiliares.recipes;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families/{familyId}/recipes/{recipeId}")
public class RecipeContentController {

    private final RecipeContentService recipeContentService;

    public RecipeContentController(RecipeContentService recipeContentService) {
        this.recipeContentService = recipeContentService;
    }

    @GetMapping("/ingredients")
    public List<RecipeIngredientResponse> listIngredients(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            Authentication authentication
    ) {
        return recipeContentService.listIngredients(familyId, recipeId, authentication.getName(), includeDeleted);
    }

    @PutMapping("/ingredients")
    public List<RecipeIngredientResponse> replaceIngredients(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @Valid @RequestBody RecipeIngredientListRequest request,
            Authentication authentication
    ) {
        return recipeContentService.replaceIngredients(familyId, recipeId, authentication.getName(), request);
    }

    @GetMapping("/steps")
    public List<RecipeStepResponse> listSteps(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            Authentication authentication
    ) {
        return recipeContentService.listSteps(familyId, recipeId, authentication.getName(), includeDeleted);
    }

    @PutMapping("/steps")
    public List<RecipeStepResponse> replaceSteps(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @Valid @RequestBody RecipeStepListRequest request,
            Authentication authentication
    ) {
        return recipeContentService.replaceSteps(familyId, recipeId, authentication.getName(), request);
    }
}
