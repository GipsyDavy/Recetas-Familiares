package org.gipsybuho.recetasfamiliares.ratings;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families/{familyId}/recipes/{recipeId}/ratings")
public class RecipeRatingController {

    private final RecipeRatingService ratingService;

    public RecipeRatingController(RecipeRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping
    public List<RecipeRatingResponse> listRatings(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            Authentication authentication
    ) {
        return ratingService.listRatings(familyId, recipeId, authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeRatingResponse createRating(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @Valid @RequestBody CreateRatingRequest request,
            Authentication authentication
    ) {
        return ratingService.createRating(familyId, recipeId, authentication.getName(), request);
    }

    @PutMapping("/{ratingId}")
    public RecipeRatingResponse updateRating(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @PathVariable String ratingId,
            @Valid @RequestBody UpdateRatingRequest request,
            Authentication authentication
    ) {
        return ratingService.updateRating(familyId, recipeId, ratingId,
                authentication.getName(), request);
    }

    @DeleteMapping("/{ratingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRating(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @PathVariable String ratingId,
            Authentication authentication
    ) {
        ratingService.deleteRating(familyId, recipeId, ratingId, authentication.getName());
    }
}
