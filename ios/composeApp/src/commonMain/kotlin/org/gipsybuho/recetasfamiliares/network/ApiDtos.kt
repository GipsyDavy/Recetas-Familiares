package org.gipsybuho.recetasfamiliares.network

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
    val family: FamilyDto
)

@Serializable
data class UserDto(val id: String, val displayName: String, val email: String)

@Serializable
data class UserResponseDto(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null
)

@Serializable
data class FamilyDto(
    val id: String,
    val name: String,
    val role: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class CreateFamilyRequestDto(val name: String)

@Serializable
data class FamilyMemberResponseDto(
    val userId: String,
    val displayName: String? = null,
    val email: String,
    val avatarUrl: String? = null,
    val role: String
)

@Serializable
data class UserRecipeRankingDto(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val role: String? = null,
    val recipesCreated: Long,
    val ratingsReceived: Long,
    val averageStars: Double? = null,
    val score: Long
)

@Serializable
data class InviteMemberRequestDto(val email: String, val role: String)

@Serializable
data class PageDto<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int
)

@Serializable
data class RecipeDto(
    val id: String,
    val familyId: String,
    val title: String,
    val description: String? = null,
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val difficulty: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean,
    val createdByUserId: String? = null,
    val createdByDisplayName: String? = null
)

@Serializable
data class StockItemDto(
    val id: String,
    val familyId: String,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val lowStockThreshold: Double? = null,
    val expiresAt: String? = null,
    val note: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

@Serializable
data class FamilyNoteDto(
    val id: String,
    val familyId: String,
    val recipeId: String? = null,
    val recipeTitle: String? = null,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

@Serializable
data class MenuItemDto(
    val id: String,
    val familyId: String,
    val recipeId: String? = null,
    val recipeTitle: String? = null,
    val plannedDate: String,
    val mealType: String,
    val note: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

@Serializable
data class ShoppingListDto(
    val id: String,
    val familyId: String,
    val name: String,
    val plannedFrom: String? = null,
    val plannedTo: String? = null,
    val note: String? = null,
    val completed: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

@Serializable
data class ShoppingListItemDto(
    val id: String,
    val shoppingListId: String,
    val position: Int,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val checked: Boolean,
    val note: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

@Serializable
data class RecipeIngredientDto(
    val id: String,
    val recipeId: String? = null,
    val position: Int,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val note: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncVersion: Long = 0,
    val deleted: Boolean = false
)

@Serializable
data class RecipeStepDto(
    val id: String,
    val recipeId: String? = null,
    val position: Int,
    val instruction: String,
    val timerMinutes: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncVersion: Long = 0,
    val deleted: Boolean = false
)

@Serializable
data class FavoriteRecipeDto(
    val id: String? = null,
    val familyId: String? = null,
    val recipeId: String,
    val recipeTitle: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncVersion: Long = 0,
    val deleted: Boolean = false
)

@Serializable
data class RecipePhotoDto(
    val id: String,
    val recipeId: String,
    val position: Int = 0,
    val url: String,
    val thumbnailUrl: String? = null,
    val caption: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncVersion: Long = 0,
    val deleted: Boolean = false
)

@Serializable
data class CreateNoteRequest(val title: String, val body: String, val pinned: Boolean)

@Serializable
data class CreateStockItemRequest(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val expiresAt: String? = null,
    val lowStockThreshold: Double? = null,
    val note: String? = null
)

@Serializable
data class SyncPullResponseDto(
    val serverTime: String,
    val recipes: List<RecipeDto> = emptyList(),
    val ingredients: List<RecipeIngredientDto> = emptyList(),
    val steps: List<RecipeStepDto> = emptyList(),
    val stockItems: List<StockItemDto> = emptyList(),
    val menuItems: List<MenuItemDto> = emptyList(),
    val shoppingLists: List<ShoppingListDto> = emptyList(),
    val shoppingListItems: List<ShoppingListItemDto> = emptyList(),
    val favoriteRecipes: List<FavoriteRecipeDto> = emptyList(),
    val familyNotes: List<FamilyNoteDto> = emptyList(),
    val recipePhotos: List<RecipePhotoDto> = emptyList(),
    val hasMore: Boolean = false,
    val nextSince: String? = null
)
