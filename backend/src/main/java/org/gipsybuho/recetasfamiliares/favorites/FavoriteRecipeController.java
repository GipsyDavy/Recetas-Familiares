package org.gipsybuho.recetasfamiliares.favorites;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.gipsybuho.recetasfamiliares.common.api.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/families/{familyId}/favorite-recipes")
public class FavoriteRecipeController {

    private final FavoriteRecipeService favoriteRecipeService;

    public FavoriteRecipeController(FavoriteRecipeService favoriteRecipeService) {
        this.favoriteRecipeService = favoriteRecipeService;
    }

    @GetMapping
    public PageResponse<FavoriteRecipeResponse> listFavorites(
            @PathVariable String familyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return favoriteRecipeService.listFavorites(familyId, authentication.getName(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteRecipeResponse createFavorite(
            @PathVariable String familyId,
            @Valid @RequestBody CreateFavoriteRecipeRequest request,
            Authentication authentication
    ) {
        return favoriteRecipeService.createFavorite(familyId, authentication.getName(), request);
    }

    @GetMapping("/{favoriteId}")
    public FavoriteRecipeResponse getFavorite(
            @PathVariable String familyId,
            @PathVariable String favoriteId,
            Authentication authentication
    ) {
        return favoriteRecipeService.getFavorite(familyId, favoriteId, authentication.getName());
    }

    @DeleteMapping("/{favoriteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFavorite(
            @PathVariable String familyId,
            @PathVariable String favoriteId,
            Authentication authentication
    ) {
        favoriteRecipeService.deleteFavorite(familyId, favoriteId, authentication.getName());
    }
}
