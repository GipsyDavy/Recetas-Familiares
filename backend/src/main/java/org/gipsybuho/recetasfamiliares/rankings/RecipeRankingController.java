package org.gipsybuho.recetasfamiliares.rankings;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families/{familyId}/recipe-rankings")
public class RecipeRankingController {

    private final RecipeRankingService recipeRankingService;

    public RecipeRankingController(RecipeRankingService recipeRankingService) {
        this.recipeRankingService = recipeRankingService;
    }

    @GetMapping("/users")
    public List<UserRecipeRankingResponse> userRecipeRanking(
            @PathVariable String familyId,
            Authentication authentication
    ) {
        return recipeRankingService.userRecipeRanking(familyId, authentication.getName());
    }
}
