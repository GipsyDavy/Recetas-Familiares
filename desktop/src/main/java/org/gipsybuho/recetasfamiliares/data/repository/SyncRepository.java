package org.gipsybuho.recetasfamiliares.data.repository;

import javafx.application.Platform;
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
    private final MenuRepository menuRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final FavoriteRepository favoriteRepository;
    private final NoteRepository noteRepository;

    public SyncRepository(ApiClient api, AppSession session,
                          RecipeRepository recipeRepo, StockRepository stockRepo,
                          MenuRepository menuRepository,
                          ShoppingListRepository shoppingListRepository,
                          FavoriteRepository favoriteRepository,
                          NoteRepository noteRepository) {
        this.api = api;
        this.session = session;
        this.recipeRepo = recipeRepo;
        this.stockRepo = stockRepo;
        this.menuRepository = menuRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.favoriteRepository = favoriteRepository;
        this.noteRepository = noteRepository;
    }

    /** Pull incremental changes from server and update in-memory caches. */
    public void pull() throws ApiException {
        String familyId = session.getFamilyId();
        String since = session.getLastSyncTime();
        String path = "api/v1/families/" + familyId + "/sync/pull"
                + (since != null ? "?since=" + since : "");

        SyncDtos.SyncPullResponse response = api.get(path, SyncDtos.SyncPullResponse.class);

        // Las caches son ObservableList enlazadas a la UI: mutarlas solo en el FX thread.
        // pull() se invoca desde hilos de fondo (MainWindow.triggerSync).
        Runnable applyCaches = () -> {
            recipeRepo.updateFromSync(response.recipes(), response.ingredients(), response.steps());
            stockRepo.updateFromSync(response.stockItems());
            menuRepository.updateFromSync(response.menuItems());
            shoppingListRepository.updateFromSync(response.shoppingLists());
            favoriteRepository.getCache().replaceAll(response.favoriteRecipes() != null
                    ? response.favoriteRecipes().stream().filter(f -> !f.deleted()).toList()
                    : List.of());
        };
        if (Platform.isFxApplicationThread()) {
            applyCaches.run();
        } else {
            Platform.runLater(applyCaches);
        }
        session.setLastSyncTime(response.serverTime());
    }

    /** Push local changes. Currently sends empty collections — offline queue not yet implemented. */
    public void push() throws ApiException {
        String familyId = session.getFamilyId();
        var request = new SyncDtos.SyncPushRequest(List.of(), List.of(), List.of());
        api.post("api/v1/families/" + familyId + "/sync/push", request, SyncDtos.SyncPullResponse.class);
    }
}
