package org.gipsybuho.recetasfamiliares.network

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(val email: String, val password: String)

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
data class FamilyDto(val id: String, val name: String)

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
    val deleted: Boolean
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
    val position: Int,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val note: String? = null
)

@Serializable
data class RecipeStepDto(
    val id: String,
    val position: Int,
    val instruction: String,
    val timerMinutes: Int? = null
)

@Serializable
data class FavoriteRecipeDto(
    val recipeId: String,
    val recipeTitle: String? = null,
    val createdAt: String? = null
)

@Serializable
data class SyncPullResponseDto(
    val serverTime: String,
    val recipes: List<RecipeDto> = emptyList(),
    val stockItems: List<StockItemDto> = emptyList(),
    val familyNotes: List<FamilyNoteDto> = emptyList()
)
