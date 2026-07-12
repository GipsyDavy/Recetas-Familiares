package org.gipsybuho.recetasfamiliares.core

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.gipsybuho.recetasfamiliares.BuildConfig
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import org.gipsybuho.recetasfamiliares.data.remote.AuthInterceptor
import org.gipsybuho.recetasfamiliares.data.remote.DynamicBaseUrlInterceptor
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.TokenRefreshAuthenticator
import org.gipsybuho.recetasfamiliares.data.repository.AuthRepository
import org.gipsybuho.recetasfamiliares.data.repository.ChatRepository
import org.gipsybuho.recetasfamiliares.data.repository.FamilyMemberRepository
import org.gipsybuho.recetasfamiliares.data.repository.FamilyNoteRepository
import org.gipsybuho.recetasfamiliares.data.repository.MenuItemRepository
import org.gipsybuho.recetasfamiliares.data.repository.FavoriteRepository
import org.gipsybuho.recetasfamiliares.data.repository.RecipePhotoRepository
import org.gipsybuho.recetasfamiliares.data.repository.RecipeRatingRepository
import org.gipsybuho.recetasfamiliares.data.repository.RecipeRepository
import org.gipsybuho.recetasfamiliares.data.repository.ShoppingListRepository
import org.gipsybuho.recetasfamiliares.data.repository.StockRepository
import org.gipsybuho.recetasfamiliares.data.repository.SyncRepository
import org.gipsybuho.recetasfamiliares.data.repository.UserRepository
import org.gipsybuho.recetasfamiliares.ui.theme.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(context: Context) {

    val sessionStore = SessionStore(context)
    val serverUrlStore = ServerUrlStore(context)

    val database: RecetasDatabase = Room.databaseBuilder(
        context,
        RecetasDatabase::class.java,
        "recetas-familiares.db"
    )
        .addMigrations(MIGRATION_1_2)
        .build()

    private val retrofitBaseUrl = serverUrlStore.baseUrl
    private val baseUrlProvider: () -> String = { serverUrlStore.baseUrl }

    // Expuesto para que Coil cargue imagenes de /uploads/** con Authorization (SEC-3)
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(DynamicBaseUrlInterceptor(retrofitBaseUrl, baseUrlProvider))
        .addInterceptor(AuthInterceptor(sessionStore, baseUrlProvider))
        .authenticator(TokenRefreshAuthenticator(sessionStore, baseUrlProvider))
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
        .baseUrl(retrofitBaseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RecetasApi::class.java)

    val authRepository = AuthRepository(api, sessionStore) {
        // Cambio de usuario en el mismo dispositivo: la cache local es del
        // usuario anterior; los datos maestros viven en el servidor.
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }
    val recipeRepository = RecipeRepository(api, database, sessionStore)
    val stockRepository = StockRepository(api, database.stockDao(), sessionStore)
    val syncRepository = SyncRepository(api, database, sessionStore)
    val shoppingListRepository = ShoppingListRepository(api, database, sessionStore)
    val favoriteRepository = FavoriteRepository(api, database, sessionStore)
    val familyNoteRepository = FamilyNoteRepository(api, database, sessionStore)
    val recipePhotoRepository = RecipePhotoRepository(api, database, sessionStore)
    val menuItemRepository = MenuItemRepository(api, database, sessionStore)
    val recipeRatingRepository = RecipeRatingRepository(api, sessionStore)
    val userRepository = UserRepository(api, sessionStore)
    val familyMemberRepository = FamilyMemberRepository(api, sessionStore)
    val chatRepository = ChatRepository(api, httpClient, sessionStore, baseUrlProvider)
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
