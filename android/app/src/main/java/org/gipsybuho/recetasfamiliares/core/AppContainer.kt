package org.gipsybuho.recetasfamiliares.core

import android.content.Context
import androidx.room.Room
import org.gipsybuho.recetasfamiliares.BuildConfig
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import org.gipsybuho.recetasfamiliares.data.remote.AuthInterceptor
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.repository.AuthRepository
import org.gipsybuho.recetasfamiliares.data.repository.RecipeRepository
import org.gipsybuho.recetasfamiliares.data.repository.StockRepository
import org.gipsybuho.recetasfamiliares.data.repository.SyncRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(context: Context) {
    private val sessionStore = SessionStore(context)

    private val database = Room.databaseBuilder(
        context,
        RecetasDatabase::class.java,
        "recetas-familiares.db"
    ).build()

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(sessionStore))
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
        .build()

    private val api = Retrofit.Builder()
        .baseUrl(BuildConfig.DEFAULT_API_BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RecetasApi::class.java)

    val authRepository = AuthRepository(api, sessionStore)
    val recipeRepository = RecipeRepository(api, database.recipeDao(), sessionStore)
    val stockRepository = StockRepository(api, database.stockDao(), sessionStore)
    val syncRepository = SyncRepository(api, database.recipeDao(), database.stockDao(), sessionStore)
}
