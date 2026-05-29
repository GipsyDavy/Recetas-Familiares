package org.gipsybuho.recetasfamiliares.core

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.gipsybuho.recetasfamiliares.BuildConfig
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import org.gipsybuho.recetasfamiliares.data.remote.AuthInterceptor
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.TokenRefreshAuthenticator
import org.gipsybuho.recetasfamiliares.data.repository.AuthRepository
import org.gipsybuho.recetasfamiliares.data.repository.FamilyNoteRepository
import org.gipsybuho.recetasfamiliares.data.repository.MenuItemRepository
import org.gipsybuho.recetasfamiliares.data.repository.FavoriteRepository
import org.gipsybuho.recetasfamiliares.data.repository.RecipePhotoRepository
import org.gipsybuho.recetasfamiliares.data.repository.RecipeRatingRepository
import org.gipsybuho.recetasfamiliares.data.repository.RecipeRepository
import org.gipsybuho.recetasfamiliares.data.repository.ShoppingListRepository
import org.gipsybuho.recetasfamiliares.data.repository.StockRepository
import org.gipsybuho.recetasfamiliares.data.repository.SyncRepository
import org.gipsybuho.recetasfamiliares.ui.theme.ThemePreference
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(context: Context) {

    val sessionStore = SessionStore(context)

    val database: RecetasDatabase = Room.databaseBuilder(
        context,
        RecetasDatabase::class.java,
        "recetas-familiares.db"
    )
        .addMigrations(MIGRATION_1_2)
        .build()

    private val baseUrl = BuildConfig.DEFAULT_API_BASE_URL

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(sessionStore))
        .authenticator(TokenRefreshAuthenticator(sessionStore, baseUrl))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .build()

    private val api: RecetasApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RecetasApi::class.java)

    val authRepository = AuthRepository(api, sessionStore)
    val recipeRepository = RecipeRepository(api, database, sessionStore)
    val stockRepository = StockRepository(api, database.stockDao(), sessionStore)
    val syncRepository = SyncRepository(api, database, sessionStore)
    val shoppingListRepository = ShoppingListRepository(api, database, sessionStore)
    val favoriteRepository = FavoriteRepository(api, database, sessionStore)
    val familyNoteRepository = FamilyNoteRepository(api, database, sessionStore)
    val recipePhotoRepository = RecipePhotoRepository(api, database, sessionStore)
    val menuItemRepository = MenuItemRepository(api, database, sessionStore)
    val recipeRatingRepository = RecipeRatingRepository(api, sessionStore)
    val themePreference = ThemePreference(context)
    val onboardingPreference = OnboardingPreference(context)

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes between v1 and v2
            }
        }
    }
}
