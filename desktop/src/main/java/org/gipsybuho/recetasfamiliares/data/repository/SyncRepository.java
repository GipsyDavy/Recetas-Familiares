package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

import java.util.List;

public class SyncRepository {

    private final ApiClient api;
    private final AppSession session;
    private final RecipeRepository recipeRepo;
    private final StockRepository stockRepo;

    public SyncRepository(ApiClient api, AppSession session,
                          RecipeRepository recipeRepo, StockRepository stockRepo) {
        this.api = api;
        this.session = session;
        this.recipeRepo = recipeRepo;
        this.stockRepo = stockRepo;
    }

    /** Pull incremental changes from server and update in-memory caches. */
    public void pull() throws ApiException {
        String familyId = session.getFamilyId();
        String since = session.getLastSyncTime();
        String path = "api/v1/families/" + familyId + "/sync/pull"
                + (since != null ? "?since=" + since : "");

        SyncDtos.SyncPullResponse response = api.get(path, SyncDtos.SyncPullResponse.class);

        recipeRepo.updateFromSync(response.recipes(), response.ingredients(), response.steps());
        stockRepo.updateFromSync(response.stockItems());
        session.setLastSyncTime(response.serverTime());
    }

    /** Push local changes. Currently sends empty collections — offline queue not yet implemented. */
    public void push() throws ApiException {
        String familyId = session.getFamilyId();
        var request = new SyncDtos.SyncPushRequest(List.of(), List.of(), List.of());
        api.post("api/v1/families/" + familyId + "/sync/push", request, SyncDtos.SyncPullResponse.class);
    }
}
