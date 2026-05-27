package org.gipsybuho.recetasfamiliares.photos;

import java.util.List;

import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecipePhotoService {

    private final RecipePhotoRepository photoRepository;
    private final RecipeRepository recipeRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public RecipePhotoService(
            RecipePhotoRepository photoRepository,
            RecipeRepository recipeRepository,
            FamilyMemberRepository familyMemberRepository
    ) {
        this.photoRepository = photoRepository;
        this.recipeRepository = recipeRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipePhotoResponse> listPhotos(
            String familyId,
            String recipeId,
            String userId,
            boolean includeDeleted
    ) {
        requireActiveRecipeForMember(familyId, recipeId, userId);
        List<RecipePhotoEntity> photos = includeDeleted
                ? photoRepository.findByRecipe_IdOrderByPositionAsc(recipeId)
                : photoRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId);
        return photos.stream().map(this::toResponse).toList();
    }

    @Transactional
    public RecipePhotoResponse createPhoto(
            String familyId,
            String recipeId,
            String userId,
            CreateRecipePhotoRequest request
    ) {
        RecipeEntity recipe = requireActiveRecipeForEditor(familyId, recipeId, userId);
        validateMetadata(request.url(), request.thumbnailUrl(), request.contentType());
        RecipePhotoEntity photo = new RecipePhotoEntity(
                recipe,
                request.position(),
                request.url().trim(),
                trimToNull(request.thumbnailUrl()),
                trimToNull(request.caption()),
                trimToNull(request.contentType()),
                request.sizeBytes()
        );
        recipe.markContentChanged();
        recipeRepository.save(recipe);
        return toResponse(photoRepository.save(photo));
    }

    @Transactional(readOnly = true)
    public RecipePhotoResponse getPhoto(String familyId, String recipeId, String photoId, String userId) {
        requireActiveRecipeForMember(familyId, recipeId, userId);
        return toResponse(requireActivePhoto(familyId, recipeId, photoId));
    }

    @Transactional
    public RecipePhotoResponse updatePhoto(
            String familyId,
            String recipeId,
            String photoId,
            String userId,
            UpdateRecipePhotoRequest request
    ) {
        RecipeEntity recipe = requireActiveRecipeForEditor(familyId, recipeId, userId);
        RecipePhotoEntity photo = requireActivePhoto(familyId, recipeId, photoId);
        validateMetadata(request.url(), request.thumbnailUrl(), request.contentType());
        photo.update(
                request.position(),
                request.url().trim(),
                trimToNull(request.thumbnailUrl()),
                trimToNull(request.caption()),
                trimToNull(request.contentType()),
                request.sizeBytes()
        );
        recipe.markContentChanged();
        recipeRepository.save(recipe);
        return toResponse(photoRepository.save(photo));
    }

    @Transactional
    public void deletePhoto(String familyId, String recipeId, String photoId, String userId) {
        RecipeEntity recipe = requireActiveRecipeForEditor(familyId, recipeId, userId);
        RecipePhotoEntity photo = requireActivePhoto(familyId, recipeId, photoId);
        photo.softDelete();
        recipe.markContentChanged();
        recipeRepository.save(recipe);
        photoRepository.save(photo);
    }

    private RecipeEntity requireActiveRecipeForMember(String familyId, String recipeId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
        return recipeRepository.findByIdAndFamily_IdAndDeletedFalse(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));
    }

    private RecipeEntity requireActiveRecipeForEditor(String familyId, String recipeId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndRoleInAndDeletedFalse(
                familyId,
                userId,
                List.of(FamilyRole.OWNER, FamilyRole.ADMIN)
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family write access denied");
        }
        return recipeRepository.findByIdAndFamily_IdAndDeletedFalse(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));
    }

    private RecipePhotoEntity requireActivePhoto(String familyId, String recipeId, String photoId) {
        return photoRepository.findByIdAndRecipe_IdAndRecipe_Family_IdAndDeletedFalse(photoId, recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe photo not found"));
    }

    private RecipePhotoResponse toResponse(RecipePhotoEntity photo) {
        return new RecipePhotoResponse(
                photo.getId(),
                photo.getRecipeId(),
                photo.getPosition(),
                photo.getUrl(),
                photo.getThumbnailUrl(),
                photo.getCaption(),
                photo.getContentType(),
                photo.getSizeBytes(),
                photo.getCreatedAt(),
                photo.getUpdatedAt(),
                photo.getSyncVersion(),
                photo.isDeleted()
        );
    }

    public static void validateMetadata(String url, String thumbnailUrl, String contentType) {
        validateHttpsUrl(url, "Photo URL must be http or https");
        if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
            validateHttpsUrl(thumbnailUrl, "Thumbnail URL must be http or https");
        }
        String normalizedContentType = trimToNull(contentType);
        if (normalizedContentType != null
                && !normalizedContentType.equals("image/jpeg")
                && !normalizedContentType.equals("image/png")
                && !normalizedContentType.equals("image/webp")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported photo content type");
        }
    }

    private static void validateHttpsUrl(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        String normalized = value.trim().toLowerCase();
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
