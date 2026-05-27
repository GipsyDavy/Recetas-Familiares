package org.gipsybuho.recetasfamiliares.core;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.data.repository.AuthRepository;
import org.gipsybuho.recetasfamiliares.data.repository.RecipeRepository;
import org.gipsybuho.recetasfamiliares.data.repository.StockRepository;
import org.gipsybuho.recetasfamiliares.data.repository.SyncRepository;

/** Singleton dependency container — no DI framework, intentionally simple. */
public final class AppContext {

    private static AppContext instance;

    private final AppSession session;
    private final ApiClient apiClient;
    private final AuthRepository authRepository;
    private final RecipeRepository recipeRepository;
    private final StockRepository stockRepository;
    private final SyncRepository syncRepository;

    private AppContext() {
        session = new AppSession();
        apiClient = new ApiClient(session);
        authRepository = new AuthRepository(apiClient, session);
        recipeRepository = new RecipeRepository(apiClient, session);
        stockRepository = new StockRepository(apiClient, session);
        syncRepository = new SyncRepository(apiClient, session, recipeRepository, stockRepository);
    }

    public static synchronized AppContext getInstance() {
        if (instance == null) instance = new AppContext();
        return instance;
    }

    public AppSession getSession() { return session; }
    public ApiClient getApiClient() { return apiClient; }
    public AuthRepository getAuthRepository() { return authRepository; }
    public RecipeRepository getRecipeRepository() { return recipeRepository; }
    public StockRepository getStockRepository() { return stockRepository; }
    public SyncRepository getSyncRepository() { return syncRepository; }

    public void shutdown() {
        apiClient.shutdown();
    }
}
