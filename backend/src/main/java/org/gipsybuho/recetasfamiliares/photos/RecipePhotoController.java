package org.gipsybuho.recetasfamiliares.photos;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/families/{familyId}/recipes/{recipeId}/photos")
public class RecipePhotoController {

    private final RecipePhotoService photoService;

    public RecipePhotoController(RecipePhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping
    public List<RecipePhotoResponse> listPhotos(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            Authentication authentication
    ) {
        return photoService.listPhotos(familyId, recipeId, authentication.getName(), includeDeleted);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipePhotoResponse createPhoto(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @Valid @RequestBody CreateRecipePhotoRequest request,
            Authentication authentication
    ) {
        return photoService.createPhoto(familyId, recipeId, authentication.getName(), request);
    }

    /** Multipart upload: stores the file and registers the photo metadata in one step. */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipePhotoResponse uploadPhoto(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "caption", required = false) String caption,
            Authentication authentication
    ) {
        return photoService.uploadAndCreatePhoto(
                familyId, recipeId, authentication.getName(), file, caption);
    }

    @GetMapping("/{photoId}")
    public RecipePhotoResponse getPhoto(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @PathVariable String photoId,
            Authentication authentication
    ) {
        return photoService.getPhoto(familyId, recipeId, photoId, authentication.getName());
    }

    @PutMapping("/{photoId}")
    public RecipePhotoResponse updatePhoto(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @PathVariable String photoId,
            @Valid @RequestBody UpdateRecipePhotoRequest request,
            Authentication authentication
    ) {
        return photoService.updatePhoto(familyId, recipeId, photoId, authentication.getName(), request);
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(
            @PathVariable String familyId,
            @PathVariable String recipeId,
            @PathVariable String photoId,
            Authentication authentication
    ) {
        photoService.deletePhoto(familyId, recipeId, photoId, authentication.getName());
    }
}
