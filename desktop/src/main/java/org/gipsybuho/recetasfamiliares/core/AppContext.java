package org.gipsybuho.recetasfamiliares.core;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.data.repository.AuthRepository;
import org.gipsybuho.recetasfamiliares.data.repository.ChatRepository;
import org.gipsybuho.recetasfamiliares.data.repository.PrivateChatRepository;
import org.gipsybuho.recetasfamiliares.data.repository.FavoriteRepository;
import org.gipsybuho.recetasfamiliares.data.repository.MenuRepository;
import org.gipsybuho.recetasfamiliares.data.repository.NoteRepository;
import org.gipsybuho.recetasfamiliares.data.repository.RecipeRepository;
import org.gipsybuho.recetasfamiliares.data.repository.ShoppingListRepository;
import org.gipsybuho.recetasfamiliares.data.repository.StockRepository;
import org.gipsybuho.recetasfamiliares.data.repository.SyncRepository;
import org.gipsybuho.recetasfamiliares.data.repository.FamilyRepository;
import org.gipsybuho.recetasfamiliares.data.repository.UserRepository;

/** Singleton dependency container — no DI framework, intentionally simple. */
public final class AppContext {

    private static AppContext instance;

    private final AppSession session;
    private final ApiClient apiClient;
    private final ImageCache imageCache;
    private final AuthRepository authRepository;
    private final RecipeRepository recipeRepository;
    private final StockRepository stockRepository;
    private final MenuRepository menuRepository;
    private final SyncRepository syncRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final FavoriteRepository favoriteRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final ChatRepository chatRepository;
    private final PrivateChatRepository privateChatRepository;

    private AppContext() {
        session = new AppSession();
        apiClient = new ApiClient(session);
        imageCache = new ImageCache(apiClient, 200);
        authRepository = new AuthRepository(apiClient, session);
        userRepository = new UserRepository(apiClient, session);
        familyRepository = new FamilyRepository(apiClient, session);
        recipeRepository = new RecipeRepository(apiClient, session);
        stockRepository = new StockRepository(apiClient, session);
        menuRepository = new MenuRepository(apiClient, session);
        shoppingListRepository = new ShoppingListRepository(apiClient, session);
        favoriteRepository = new FavoriteRepository(apiClient, session);
        noteRepository = new NoteRepository(apiClient, session);
        chatRepository = new ChatRepository(apiClient, session);
        privateChatRepository = new PrivateChatRepository(apiClient, session);
        syncRepository = new SyncRepository(apiClient, session, recipeRepository, stockRepository,
                menuRepository, shoppingListRepository, favoriteRepository, noteRepository);
    }

    public static synchronized AppContext getInstance() {
        if (instance == null) instance = new AppContext();
        return instance;
    }

    public AppSession getSession() { return session; }
    public ApiClient getApiClient() { return apiClient; }
    public ImageCache getImageCache() { return imageCache; }
    public AuthRepository getAuthRepository() { return authRepository; }
    public RecipeRepository getRecipeRepository() { return recipeRepository; }
    public StockRepository getStockRepository() { return stockRepository; }
    public MenuRepository getMenuRepository() { return menuRepository; }
    public SyncRepository getSyncRepository() { return syncRepository; }
    public ShoppingListRepository getShoppingListRepository() { return shoppingListRepository; }
    public FavoriteRepository getFavoriteRepository() { return favoriteRepository; }
    public NoteRepository getNoteRepository() { return noteRepository; }
    public UserRepository getUserRepository() { return userRepository; }
    public FamilyRepository getFamilyRepository() { return familyRepository; }
    public ChatRepository getChatRepository() { return chatRepository; }
    public PrivateChatRepository getPrivateChatRepository() { return privateChatRepository; }

    public void switchActiveFamily(String familyId, FamilyRole role) {
        session.setFamilyId(familyId);
        session.setFamilyRole(role);
        clearFamilyScopedCaches();
    }

    public void clearFamilyScopedCaches() {
        recipeRepository.clearCache();
        stockRepository.clearCache();
        menuRepository.clearCache();
        favoriteRepository.clearCache();
        noteRepository.clearCache();
        chatRepository.resetPrivateChatState();
        imageCache.clearCache();
    }

    public void shutdown() {
        recipeRepository.shutdown();
        apiClient.shutdown();
    }
}
