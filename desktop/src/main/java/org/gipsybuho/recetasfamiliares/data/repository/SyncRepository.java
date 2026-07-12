package org.gipsybuho.recetasfamiliares.data.repository;

import javafx.application.Platform;
import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SyncRepository {

    private static final int PULL_PAGE_SIZE = 200;
    private static final int MAX_PULL_PAGES = 50;

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
        if (familyId == null || familyId.isBlank()) {
            return;
        }
        String cursor = session.getLastSyncTime();
        SyncAccumulator accumulator = new SyncAccumulator();
        boolean completed = false;
        String serverTime = null;

        for (int page = 0; page < MAX_PULL_PAGES; page++) {
            SyncDtos.SyncPullResponse response = api.get(
                    buildPullPath(familyId, cursor),
                    SyncDtos.SyncPullResponse.class);
            accumulator.add(response);
            if (response.serverTime() != null) {
                serverTime = response.serverTime();
            }
            if (!response.hasMore()) {
                completed = true;
                break;
            }
            if (response.nextSince() == null || response.nextSince().isBlank()
                    || response.nextSince().equals(cursor)) {
                break;
            }
            cursor = response.nextSince();
        }

        String finalServerTime = serverTime;
        boolean finalCompleted = completed;

        // Las caches son ObservableList enlazadas a la UI: mutarlas solo en el FX thread.
        // pull() se invoca desde hilos de fondo (MainWindow.triggerSync).
        Runnable applyCaches = () -> {
            if (!Objects.equals(familyId, session.getFamilyId())) {
                return;
            }
            recipeRepo.updateFromSync(accumulator.recipes, accumulator.ingredients, accumulator.steps);
            recipeRepo.updatePhotosFromSync(accumulator.recipePhotos);
            stockRepo.updateFromSync(accumulator.stockItems);
            menuRepository.updateFromSync(accumulator.menuItems);
            shoppingListRepository.updateFromSync(accumulator.shoppingLists, accumulator.shoppingListItems);
            favoriteRepository.updateFromSync(accumulator.favoriteRecipes);
            noteRepository.updateFromSync(accumulator.familyNotes);
            if (finalCompleted && finalServerTime != null) {
                session.setLastSyncTime(finalServerTime);
            }
        };
        if (Platform.isFxApplicationThread()) {
            applyCaches.run();
        } else {
            Platform.runLater(applyCaches);
        }
    }

    /** Push local changes. Currently sends empty collections — offline queue not yet implemented. */
    public void push() throws ApiException {
        String familyId = session.getFamilyId();
        var request = new SyncDtos.SyncPushRequest(List.of(), List.of(), List.of());
        api.post("api/v1/families/" + familyId + "/sync/push", request, SyncDtos.SyncPullResponse.class);
    }

    private String buildPullPath(String familyId, String since) {
        StringBuilder path = new StringBuilder("api/v1/families/")
                .append(familyId)
                .append("/sync/pull?limit=")
                .append(PULL_PAGE_SIZE);
        if (since != null && !since.isBlank()) {
            path.append("&since=").append(URLEncoder.encode(since, StandardCharsets.UTF_8));
        }
        return path.toString();
    }

    private static final class SyncAccumulator {
        private final List<org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos.RecipeDto> recipes = new ArrayList<>();
        private final List<org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos.RecipeIngredientDto> ingredients = new ArrayList<>();
        private final List<org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos.RecipeStepDto> steps = new ArrayList<>();
        private final List<org.gipsybuho.recetasfamiliares.api.dto.StockDtos.StockItemDto> stockItems = new ArrayList<>();
        private final List<SyncDtos.MenuDtos.MenuItemDto> menuItems = new ArrayList<>();
        private final List<SyncDtos.ShoppingDtos.ShoppingListDto> shoppingLists = new ArrayList<>();
        private final List<SyncDtos.ShoppingDtos.ShoppingListItemDto> shoppingListItems = new ArrayList<>();
        private final List<SyncDtos.FavoriteDtos.FavoriteRecipeDto> favoriteRecipes = new ArrayList<>();
        private final List<SyncDtos.NoteDtos.FamilyNoteDto> familyNotes = new ArrayList<>();
        private final List<SyncDtos.PhotoDtos.RecipePhotoDto> recipePhotos = new ArrayList<>();

        private void add(SyncDtos.SyncPullResponse response) {
            addAll(recipes, response.recipes());
            addAll(ingredients, response.ingredients());
            addAll(steps, response.steps());
            addAll(stockItems, response.stockItems());
            addAll(menuItems, response.menuItems());
            addAll(shoppingLists, response.shoppingLists());
            addAll(shoppingListItems, response.shoppingListItems());
            addAll(favoriteRecipes, response.favoriteRecipes());
            addAll(familyNotes, response.familyNotes());
            addAll(recipePhotos, response.recipePhotos());
        }

        private static <T> void addAll(List<T> target, List<T> source) {
            if (source != null) {
                target.addAll(source);
            }
        }
    }
}
