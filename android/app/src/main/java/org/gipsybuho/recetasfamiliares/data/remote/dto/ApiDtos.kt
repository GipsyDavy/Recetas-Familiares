package org.gipsybuho.recetasfamiliares.data.remote.dto

// ── Auth ──────────────────────────────────────────────────────────────────────

data class LoginRequestDto(
    val email: String,
    val password: String
)

data class RefreshRequestDto(
    val refreshToken: String
)

data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: AuthUserDto,
    val family: AuthFamilyDto
)

data class AuthUserDto(
    val id: String,
    val email: String,
    val displayName: String
)

data class UserResponseDto(val id: String, val email: String, val displayName: String, val avatarUrl: String? = null)
data class UpdateUserRequestDto(val displayName: String)

data class AuthFamilyDto(
    val id: String,
    val name: String
)

// ── Common ────────────────────────────────────────────────────────────────────

data class FamilyDto(
    val id: String,
    val name: String,
    val role: String? = null
)

data class FamilyMemberResponseDto(
    val userId: String,
    val displayName: String?,
    val email: String,
    val avatarUrl: String?,
    val role: String
)

data class InviteMemberRequestDto(
    val email: String,
    val role: String
)

data class PageDto<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int
)

// ── Sync pull response DTOs ───────────────────────────────────────────────────

data class RecipeDto(
    val id: String,
    val familyId: String,
    val title: String,
    val description: String?,
    val servings: Int?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val difficulty: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class RecipeIngredientDto(
    val id: String,
    val recipeId: String,
    val position: Int,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class RecipeStepDto(
    val id: String,
    val recipeId: String,
    val position: Int,
    val instruction: String,
    val timerMinutes: Int?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class StockItemDto(
    val id: String,
    val familyId: String,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val lowStockThreshold: Double?,
    val expiresAt: String?,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class MenuItemDto(
    val id: String,
    val familyId: String,
    val recipeId: String?,
    val recipeTitle: String?,
    val plannedDate: String,
    val mealType: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class AssignMenuItemRequestDto(
    val recipeId: String,
    val plannedDate: String,
    val mealType: String,
    val note: String? = null
)

data class ShoppingListDto(
    val id: String,
    val familyId: String,
    val name: String,
    val plannedFrom: String?,
    val plannedTo: String?,
    val note: String?,
    val completed: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class ShoppingListItemDto(
    val id: String,
    val shoppingListId: String,
    val position: Int,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class FavoriteRecipeDto(
    val id: String,
    val familyId: String,
    val recipeId: String,
    val recipeTitle: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class FamilyNoteDto(
    val id: String,
    val familyId: String,
    val recipeId: String?,
    val recipeTitle: String?,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class RecipeRatingDto(
    val id: String,
    val recipeId: String,
    val userId: String,
    val userDisplayName: String,
    val stars: Int,
    val comment: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class CreateRatingRequestDto(val stars: Int, val comment: String?)
data class UpdateRatingRequestDto(val stars: Int, val comment: String?)

data class RecipePhotoDto(
    val id: String,
    val recipeId: String,
    val position: Int,
    val url: String,
    val thumbnailUrl: String?,
    val caption: String?,
    val contentType: String?,
    val sizeBytes: Long?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

// ── Sync pull response container ──────────────────────────────────────────────

data class SyncPullDto(
    val serverTime: String,
    val recipes: List<RecipeDto>?,
    val ingredients: List<RecipeIngredientDto>?,
    val steps: List<RecipeStepDto>?,
    val stockItems: List<StockItemDto>?,
    val menuItems: List<MenuItemDto>?,
    val shoppingLists: List<ShoppingListDto>?,
    val shoppingListItems: List<ShoppingListItemDto>?,
    val favoriteRecipes: List<FavoriteRecipeDto>?,
    val familyNotes: List<FamilyNoteDto>?,
    val recipePhotos: List<RecipePhotoDto>?,
    val hasMore: Boolean?,
    val nextSince: String?
)

// ── Family stats ──────────────────────────────────────────────────────────────

data class FamilyStatsDto(
    val totalRecipes: Long,
    val totalMembers: Long,
    val totalStockItems: Long,
    val lastActivityAt: String?
)

// ── Mutation request DTOs ─────────────────────────────────────────────────────

data class CreateRecipeRequestDto(
    val title: String,
    val description: String?,
    val servings: Int?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val difficulty: String?
)

data class UpdateRecipeRequestDto(
    val title: String,
    val description: String?,
    val servings: Int?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val difficulty: String?
)

data class RecipeIngredientItemDto(
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val note: String?
)

data class RecipeStepItemDto(
    val instruction: String,
    val timerMinutes: Int?
)

data class ReplaceIngredientsRequestDto(val items: List<RecipeIngredientItemDto>)
data class ReplaceStepsRequestDto(val items: List<RecipeStepItemDto>)

data class CreateNoteRequestDto(
    val title: String,
    val body: String,
    val pinned: Boolean = false,
    val recipeId: String? = null
)

data class AddFavoriteRequestDto(val recipeId: String)

data class UpdateShoppingListItemRequestDto(
    val position: Int,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean,
    val note: String?
)

// ── Sync push request DTOs ────────────────────────────────────────────────────

data class SyncPushRequestDto(
    val recipes: List<SyncRecipePushItemDto> = emptyList(),
    val ingredients: List<SyncIngredientPushItemDto> = emptyList(),
    val steps: List<SyncStepPushItemDto> = emptyList(),
    val stockItems: List<SyncStockItemPushItemDto>? = null,
    val menuItems: List<SyncMenuItemPushItemDto>? = null,
    val shoppingLists: List<SyncShoppingListPushItemDto>? = null,
    val shoppingListItems: List<SyncShoppingListItemPushItemDto>? = null,
    val favoriteRecipes: List<SyncFavoriteRecipePushItemDto>? = null,
    val familyNotes: List<SyncFamilyNotePushItemDto>? = null,
    val recipePhotos: List<SyncRecipePhotoPushItemDto>? = null
)

data class SyncRecipePushItemDto(
    val id: String,
    val baseSyncVersion: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val difficulty: String? = null,
    val deleted: Boolean = false
)

data class SyncIngredientPushItemDto(
    val id: String,
    val baseSyncVersion: Long? = null,
    val recipeId: String,
    val position: Int,
    val name: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val note: String? = null,
    val deleted: Boolean = false
)

data class SyncStepPushItemDto(
    val id: String,
    val baseSyncVersion: Long? = null,
    val recipeId: String,
    val position: Int,
    val instruction: String? = null,
    val timerMinutes: Int? = null,
    val deleted: Boolean = false
)

data class SyncStockItemPushItemDto(
    val id: String,
    val baseSyncVersion: Long? = null,
    val name: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val lowStockThreshold: Double? = null,
    val expiresAt: String? = null,
    val note: String? = null,
    val deleted: Boolean = false
)

data class SyncMenuItemPushItemDto(
    val id: String,
    val baseSyncVersion: Long? = null,
    val recipeId: String? = null,
    val plannedDate: String? = null,
    val mealType: String? = null,
    val note: String? = null,
    val deleted: Boolean = false
)

data class SyncShoppingListPushItemDto(
    val id: String,
    val baseSyncVersion: Long? = null,
    val name: String? = null,
    val plannedFrom: String? = null,
    val plannedTo: String? = null,
    val note: String? = null,
    val completed: Boolean = false,
    val deleted: Boolean = false
)

data class SyncShoppingListItemPushItemDto(
    val id: String,
    val baseSyncVersion: Long? = null,
    val shoppingListId: String,
    val position: Int,
    val name: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val checked: Boolean = false,
    val note: String? = null,
    val deleted: Boolean = false
)

data class SyncFavoriteRecipePushItemDto(
    val id: String,
    val baseSyncVersion: Long? = null,
    val recipeId: String? = null,
    val deleted: Boolean = false
)

data class SyncFamilyNotePushItemDto(
    val id: String,
    val baseSyncVersion: Long? = null,
    val recipeId: String? = null,
    val title: String? = null,
    val body: String? = null,
    val pinned: Boolean = false,
    val deleted: Boolean = false
)

data class SyncRecipePhotoPushItemDto(
    val id: String,
    val baseSyncVersion: Long? = null,
    val recipeId: String? = null,
    val position: Int? = null,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val caption: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val deleted: Boolean = false
)

data class UpdateNoteRequestDto(
    val title: String,
    val body: String,
    val pinned: Boolean = false,
    val recipeId: String? = null
)

data class CreateStockItemRequestDto(
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val lowStockThreshold: Double?,
    val expiresAt: String?,
    val note: String?
)

data class UpdateStockItemRequestDto(
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val lowStockThreshold: Double?,
    val expiresAt: String?,
    val note: String?
)

// ── Chat familiar (fase 1) ──────────────────────────────────────────────────────

data class ChatAttachmentDto(
    val id: String,
    val url: String,
    val thumbnailUrl: String?,
    val contentType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null
)

data class ChatMessageDto(
    val id: String,
    val familyId: String,
    val authorUserId: String,
    val authorDisplayName: String,
    val body: String?,
    val attachments: List<ChatAttachmentDto>? = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean = false
)

data class SendChatMessageRequestDto(
    val id: String,
    val body: String
)

data class ChatHistoryDto(
    val items: List<ChatMessageDto>,
    val hasMore: Boolean,
    val nextBefore: String? = null
)

data class ChatExportDto(
    val familyId: String,
    val exportedAt: String,
    val totalMessages: Int,
    val messages: List<ChatMessageDto>
)
