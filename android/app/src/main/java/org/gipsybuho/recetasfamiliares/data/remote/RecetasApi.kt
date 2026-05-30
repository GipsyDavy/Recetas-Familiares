package org.gipsybuho.recetasfamiliares.data.remote

import org.gipsybuho.recetasfamiliares.data.remote.dto.AddFavoriteRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.InviteMemberRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.AssignMenuItemRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.MenuItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.AuthResponseDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.CreateNoteRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.CreateRecipeRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.CreateStockItemRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeIngredientDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeIngredientItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeStepDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeStepItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ReplaceIngredientsRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ReplaceStepsRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateRecipeRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyNoteDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FavoriteRecipeDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.LoginRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.PageDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.CreateRatingRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipePhotoDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeRatingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateRatingRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ShoppingListItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.StockItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncPullDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncPushRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateNoteRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateUserRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UserResponseDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateShoppingListItemRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateStockItemRequestDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface RecetasApi {

    @GET("api/v1/users/me")
    suspend fun getMe(): UserResponseDto

    @PUT("api/v1/users/me")
    suspend fun updateMe(@Body request: UpdateUserRequestDto): UserResponseDto

    @Multipart
    @POST("api/v1/users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): UserResponseDto

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

    @POST("api/v1/families/{familyId}/recipes")
    suspend fun createRecipe(
        @Path("familyId") familyId: String,
        @Body request: CreateRecipeRequestDto
    ): RecipeDto

    @GET("api/v1/families/{familyId}/recipes/{recipeId}")
    suspend fun recipe(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String
    ): RecipeDto

    @PUT("api/v1/families/{familyId}/recipes/{recipeId}")
    suspend fun updateRecipe(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String,
        @Body request: UpdateRecipeRequestDto
    ): RecipeDto

    @DELETE("api/v1/families/{familyId}/recipes/{recipeId}")
    suspend fun deleteRecipe(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String
    )

    @PUT("api/v1/families/{familyId}/recipes/{recipeId}/ingredients")
    suspend fun replaceIngredients(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String,
        @Body request: ReplaceIngredientsRequestDto
    ): List<RecipeIngredientDto>

    @PUT("api/v1/families/{familyId}/recipes/{recipeId}/steps")
    suspend fun replaceSteps(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String,
        @Body request: ReplaceStepsRequestDto
    ): List<RecipeStepDto>

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
        @Body request: SyncPushRequestDto
    ): SyncPullDto

    @POST("api/v1/families/{familyId}/favorite-recipes")
    suspend fun addFavorite(
        @Path("familyId") familyId: String,
        @Body request: AddFavoriteRequestDto
    ): FavoriteRecipeDto

    @DELETE("api/v1/families/{familyId}/favorite-recipes/{favoriteId}")
    suspend fun removeFavorite(
        @Path("familyId") familyId: String,
        @Path("favoriteId") favoriteId: String
    )

    @PUT("api/v1/families/{familyId}/shopping-lists/{listId}/items/{itemId}")
    suspend fun updateShoppingListItem(
        @Path("familyId") familyId: String,
        @Path("listId") listId: String,
        @Path("itemId") itemId: String,
        @Body request: UpdateShoppingListItemRequestDto
    ): ShoppingListItemDto

    @POST("api/v1/families/{familyId}/stock-items")
    suspend fun createStockItem(
        @Path("familyId") familyId: String,
        @Body request: CreateStockItemRequestDto
    ): StockItemDto

    @PUT("api/v1/families/{familyId}/stock-items/{itemId}")
    suspend fun updateStockItem(
        @Path("familyId") familyId: String,
        @Path("itemId") itemId: String,
        @Body request: UpdateStockItemRequestDto
    ): StockItemDto

    @DELETE("api/v1/families/{familyId}/stock-items/{itemId}")
    suspend fun deleteStockItem(
        @Path("familyId") familyId: String,
        @Path("itemId") itemId: String
    )

    @POST("api/v1/families/{familyId}/notes")
    suspend fun createNote(
        @Path("familyId") familyId: String,
        @Body request: CreateNoteRequestDto
    ): FamilyNoteDto

    @PUT("api/v1/families/{familyId}/notes/{noteId}")
    suspend fun updateNote(
        @Path("familyId") familyId: String,
        @Path("noteId") noteId: String,
        @Body request: UpdateNoteRequestDto
    ): FamilyNoteDto

    @DELETE("api/v1/families/{familyId}/notes/{noteId}")
    suspend fun deleteNote(
        @Path("familyId") familyId: String,
        @Path("noteId") noteId: String
    )

    @GET("api/v1/families/{familyId}/recipes/{recipeId}/photos")
    suspend fun photos(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String
    ): List<RecipePhotoDto>

    @Multipart
    @POST("api/v1/families/{familyId}/recipes/{recipeId}/photos/upload")
    suspend fun uploadPhoto(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String,
        @Part file: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null
    ): RecipePhotoDto

    @DELETE("api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}")
    suspend fun deletePhoto(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String,
        @Path("photoId") photoId: String
    )

    @GET("api/v1/families/{familyId}/recipes/{recipeId}/ratings")
    suspend fun ratings(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String
    ): List<RecipeRatingDto>

    @POST("api/v1/families/{familyId}/recipes/{recipeId}/ratings")
    suspend fun createRating(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String,
        @Body request: CreateRatingRequestDto
    ): RecipeRatingDto

    @PUT("api/v1/families/{familyId}/recipes/{recipeId}/ratings/{ratingId}")
    suspend fun updateRating(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String,
        @Path("ratingId") ratingId: String,
        @Body request: UpdateRatingRequestDto
    ): RecipeRatingDto

    @DELETE("api/v1/families/{familyId}/recipes/{recipeId}/ratings/{ratingId}")
    suspend fun deleteRating(
        @Path("familyId") familyId: String,
        @Path("recipeId") recipeId: String,
        @Path("ratingId") ratingId: String
    )

    @POST("api/v1/families/{familyId}/menu-items")
    suspend fun assignMenuItem(
        @Path("familyId") familyId: String,
        @Body request: AssignMenuItemRequestDto
    ): MenuItemDto

    @DELETE("api/v1/families/{familyId}/menu-items/{menuItemId}")
    suspend fun removeMenuItem(
        @Path("familyId") familyId: String,
        @Path("menuItemId") menuItemId: String
    )

    @POST("api/v1/families/{familyId}/members")
    suspend fun inviteMember(
        @Path("familyId") familyId: String,
        @Body request: InviteMemberRequestDto
    ): Unit
}
