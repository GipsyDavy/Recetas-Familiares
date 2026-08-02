package org.gipsybuho.recetasfamiliares.data.repository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeCreateDtos;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.gipsybuho.recetasfamiliares.data.cache.SimpleCache;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RecipeRepository {

    private final ApiClient api;
    private final AppSession session;
    private final Gson gson = new Gson();
    private final OkHttpClient photoClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)  // upload up to 8 MB
            .callTimeout(180, TimeUnit.SECONDS)
            .build();
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

    /**
     * Portada de una receta ya cargada en la cache local, por id. Existe para las
     * vistas que manejan MenuItemDto y no tienen el RecipeDto a mano.
     *
     * Devuelve null si la receta no esta cacheada, esta borrada o no tiene portada:
     * el llamante pinta el placeholder. No hace red: si la cache esta vacia hay que
     * repoblarla antes (ver WeeklyMenuView.loadRecipeCachePage).
     */
    public String coverUrlFor(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        for (RecipeDtos.RecipeDto recipe : cache.getItems()) {
            if (recipeId.equals(recipe.id())) {
                if (recipe.deleted()) {
                    return null;
                }
                String url = recipe.coverThumbnailUrl();
                return url != null && !url.isBlank() ? url : null;
            }
        }
        return null;
    }

    /** Load paginated recipes into cache. */
    public RecipeDtos.RecipePageResponse loadPage(int page, int size) throws ApiException {
        String familyId = session.getFamilyId();
        return loadPage(familyId, page, size);
    }

    public RecipeDtos.RecipePageResponse loadPage(String familyId, int page, int size) throws ApiException {
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

    /** Valoraciones de una receta (estrellas 1-5 + comentario opcional). */
    public List<RecipeDtos.RecipeRatingResponse> loadRatings(String recipeId) throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/recipes/" + recipeId + "/ratings";
        RecipeDtos.RecipeRatingResponse[] result = api.get(path, RecipeDtos.RecipeRatingResponse[].class);
        return List.of(result);
    }

    public RecipeDtos.RecipeRatingResponse createRating(String recipeId, int stars, String comment) throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/recipes/" + recipeId + "/ratings";
        return api.post(path, new RecipeDtos.RatingRequest(stars, comment), RecipeDtos.RecipeRatingResponse.class);
    }

    public RecipeDtos.RecipeRatingResponse updateRating(String recipeId, String ratingId, int stars, String comment) throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/recipes/" + recipeId + "/ratings/" + ratingId;
        return api.put(path, new RecipeDtos.RatingRequest(stars, comment), RecipeDtos.RecipeRatingResponse.class);
    }

    public void deleteRating(String recipeId, String ratingId) throws ApiException {
        String familyId = session.getFamilyId();
        api.delete("api/v1/families/" + familyId + "/recipes/" + recipeId + "/ratings/" + ratingId);
    }

    public RecipeDtos.RecipeDto update(String recipeId, RecipeCreateDtos.UpdateRecipeRequest request) throws ApiException {
        String familyId = session.getFamilyId();
        return api.put("api/v1/families/" + familyId + "/recipes/" + recipeId, request, RecipeDtos.RecipeDto.class);
    }

    public void delete(String recipeId) throws ApiException {
        String familyId = session.getFamilyId();
        api.delete("api/v1/families/" + familyId + "/recipes/" + recipeId);
    }

    public RecipeDtos.RecipeDto create(RecipeCreateDtos.CreateRecipeRequest request) throws ApiException {
        String familyId = session.getFamilyId();
        return api.post("api/v1/families/" + familyId + "/recipes", request, RecipeDtos.RecipeDto.class);
    }

    public void replaceIngredients(String recipeId, RecipeCreateDtos.ReplaceIngredientsRequest request) throws ApiException {
        String familyId = session.getFamilyId();
        api.put("api/v1/families/" + familyId + "/recipes/" + recipeId + "/ingredients", request);
    }

    public void replaceSteps(String recipeId, RecipeCreateDtos.ReplaceStepsRequest request) throws ApiException {
        String familyId = session.getFamilyId();
        api.put("api/v1/families/" + familyId + "/recipes/" + recipeId + "/steps", request);
    }

    /** Copia atomica en backend: cabecera, ingredientes, pasos y fotos (POST /copy). */
    public RecipeDtos.RecipeDto copyToFamily(RecipeDtos.RecipeDto source, String targetFamilyId) throws ApiException {
        if (source == null) {
            throw new IllegalArgumentException("Receta no valida");
        }
        if (targetFamilyId == null || targetFamilyId.isBlank()) {
            throw new IllegalArgumentException("Familia destino no valida");
        }
        String sourceFamilyId = source.familyId() != null && !source.familyId().isBlank()
                ? source.familyId()
                : session.getFamilyId();
        String path = "api/v1/families/" + sourceFamilyId + "/recipes/" + source.id() + "/copy";
        return api.post(path, new RecipeCreateDtos.CopyRecipeRequest(targetFamilyId), RecipeDtos.RecipeDto.class);
    }

    public List<RecipeDtos.RecipePhotoResponse> loadPhotos(String familyId, String recipeId) throws Exception {
        String path = "api/v1/families/" + familyId + "/recipes/" + recipeId + "/photos";
        Request request = new Request.Builder()
                .url(api.getBaseUrl() + path)
                .header("Authorization", "Bearer " + session.getAccessToken())
                .get()
                .build();

        try (Response response = photoClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(), "HTTP " + response.code() + ": " + body);
            }
            Type listType = new TypeToken<List<RecipeDtos.RecipePhotoResponse>>() {}.getType();
            return gson.fromJson(body, listType);
        } catch (IOException e) {
            throw new ApiException(0, "Network error: " + e.getMessage());
        }
    }

    public RecipeDtos.RecipePhotoResponse uploadPhoto(
            String familyId,
            String recipeId,
            File file,
            String contentType,
            String caption
    ) throws Exception {
        String path = "api/v1/families/" + familyId + "/recipes/" + recipeId + "/photos/upload";
        MediaType mediaType = MediaType.parse(contentType != null ? contentType : "application/octet-stream");
        MultipartBody.Builder multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(file, mediaType));
        if (caption != null && !caption.isBlank()) {
            multipart.addFormDataPart("caption", caption);
        }

        Request request = new Request.Builder()
                .url(api.getBaseUrl() + path)
                .header("Authorization", "Bearer " + session.getAccessToken())
                .post(multipart.build())
                .build();

        try (Response response = photoClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(), "HTTP " + response.code() + ": " + body);
            }
            return gson.fromJson(body, RecipeDtos.RecipePhotoResponse.class);
        } catch (IOException e) {
            throw new ApiException(0, "Network error: " + e.getMessage());
        }
    }

    public void deletePhoto(String familyId, String recipeId, String photoId) throws Exception {
        String path = "api/v1/families/" + familyId + "/recipes/" + recipeId + "/photos/" + photoId;
        Request request = new Request.Builder()
                .url(api.getBaseUrl() + path)
                .header("Authorization", "Bearer " + session.getAccessToken())
                .delete()
                .build();

        try (Response response = photoClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new ApiException(response.code(), "HTTP " + response.code() + ": " + body);
            }
        } catch (IOException e) {
            throw new ApiException(0, "Network error: " + e.getMessage());
        }
    }

    /** Replace recipe cache with data received from a sync pull. */
    public void updateFromSync(
            List<RecipeDtos.RecipeDto> recipes,
            List<RecipeDtos.RecipeIngredientDto> ingredients,
            List<RecipeDtos.RecipeStepDto> steps
    ) {
        cache.mergeById(recipes, RecipeDtos.RecipeDto::id, RecipeDtos.RecipeDto::deleted);
        ingredientCache.mergeById(ingredients, RecipeDtos.RecipeIngredientDto::id, RecipeDtos.RecipeIngredientDto::deleted);
        stepCache.mergeById(steps, RecipeDtos.RecipeStepDto::id, RecipeDtos.RecipeStepDto::deleted);
    }

    public void updatePhotosFromSync(List<SyncDtos.PhotoDtos.RecipePhotoDto> photos) {
        // Desktop no mantiene cache local de fotos: se cargan bajo demanda desde /photos.
    }

    public void clearCache() {
        cache.clear();
        ingredientCache.clear();
        stepCache.clear();
    }

    public void shutdown() {
        photoClient.dispatcher().executorService().shutdown();
        photoClient.connectionPool().evictAll();
    }
}
