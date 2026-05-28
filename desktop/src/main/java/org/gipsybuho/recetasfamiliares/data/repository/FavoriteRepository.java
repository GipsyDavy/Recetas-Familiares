package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.gipsybuho.recetasfamiliares.data.cache.SimpleCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FavoriteRepository {

    private final ApiClient api;
    private final AppSession session;
    private final SimpleCache<SyncDtos.FavoriteDtos.FavoriteRecipeDto> cache = new SimpleCache<>();

    public FavoriteRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public SimpleCache<SyncDtos.FavoriteDtos.FavoriteRecipeDto> getCache() { return cache; }

    public List<SyncDtos.FavoriteDtos.FavoriteRecipeDto> loadAll() throws ApiException {
        String familyId = session.getFamilyId();
        String path = "api/v1/families/" + familyId + "/favorite-recipes?page=0&size=200";
        SyncDtos.FavoriteDtos.FavoritePageResponse page =
                api.get(path, SyncDtos.FavoriteDtos.FavoritePageResponse.class);
        List<SyncDtos.FavoriteDtos.FavoriteRecipeDto> items =
                page.content() == null ? List.of()
                : page.content().stream().filter(f -> !f.deleted()).toList();
        cache.replaceAll(items);
        return items;
    }

    public Optional<SyncDtos.FavoriteDtos.FavoriteRecipeDto> findByRecipeId(String recipeId) {
        return cache.getItems().stream().filter(f -> recipeId.equals(f.recipeId())).findFirst();
    }

    public SyncDtos.FavoriteDtos.FavoriteRecipeDto add(String recipeId) throws ApiException {
        String familyId = session.getFamilyId();
        var req = new SyncDtos.FavoriteDtos.AddFavoriteRequest(recipeId);
        SyncDtos.FavoriteDtos.FavoriteRecipeDto result =
                api.post("api/v1/families/" + familyId + "/favorite-recipes", req,
                        SyncDtos.FavoriteDtos.FavoriteRecipeDto.class);
        List<SyncDtos.FavoriteDtos.FavoriteRecipeDto> updated = new ArrayList<>(cache.getItems());
        updated.add(result);
        cache.replaceAll(updated);
        return result;
    }

    public void remove(String favoriteId) throws ApiException {
        String familyId = session.getFamilyId();
        api.delete("api/v1/families/" + familyId + "/favorite-recipes/" + favoriteId);
        cache.replaceAll(cache.getItems().stream()
                .filter(f -> !favoriteId.equals(f.id())).toList());
    }
}
