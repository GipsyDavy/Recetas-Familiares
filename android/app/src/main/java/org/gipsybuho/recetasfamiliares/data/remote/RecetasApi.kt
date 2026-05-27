package org.gipsybuho.recetasfamiliares.data.remote

import org.gipsybuho.recetasfamiliares.data.remote.dto.AuthResponseDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.LoginRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.StockItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncPullDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RecetasApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthResponseDto

    @GET("api/v1/families")
    suspend fun families(): List<FamilyDto>

    @GET("api/v1/families/{familyId}/recipes")
    suspend fun recipes(
        @Path("familyId") familyId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): PageDto<RecipeDto>

    @GET("api/v1/families/{familyId}/recipes/{recipeId}")
    suspend fun recipe(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String
    ): RecipeDto

    @GET("api/v1/families/{familyId}/stock-items")
    suspend fun stockItems(
        @Path("familyId") familyId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100
    ): PageDto<StockItemDto>

    @GET("api/v1/families/{familyId}/sync/pull")
    suspend fun pullSync(
        @Path("familyId") familyId: String,
        @Query("since") since: String? = null
    ): SyncPullDto

    @POST("api/v1/families/{familyId}/sync/push")
    suspend fun pushSync(
        @Path("familyId") familyId: String,
        @Body request: Map<String, List<Any>> = emptyMap()
    ): SyncPullDto
}
