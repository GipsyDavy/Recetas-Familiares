package org.gipsybuho.recetasfamiliares.data.remote.dto

data class LoginRequestDto(
    val email: String,
    val password: String
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

data class AuthFamilyDto(
    val id: String,
    val name: String
)

data class FamilyDto(
    val id: String,
    val name: String
)

data class PageDto<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int
)

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

data class SyncPullDto(
    val serverTime: String,
    val recipes: List<RecipeDto>,
    val stockItems: List<StockItemDto>
)
