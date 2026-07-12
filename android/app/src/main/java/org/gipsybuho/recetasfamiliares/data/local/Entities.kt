package org.gipsybuho.recetasfamiliares.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
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
    val deleted: Boolean,
    val createdByUserId: String? = null,
    val createdByDisplayName: String? = null
)

@Entity(tableName = "recipe_ingredients")
data class RecipeIngredientEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "recipe_steps")
data class RecipeStepEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val instruction: String,
    val timerMinutes: Int?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

@Entity(tableName = "stock_items")
data class StockItemEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "shopping_list_items")
data class ShoppingListItemEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "favorite_recipes")
data class FavoriteRecipeEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val recipeId: String,
    val recipeTitle: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)

@Entity(tableName = "family_notes")
data class FamilyNoteEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "recipe_photos")
data class RecipePhotoEntity(
    @PrimaryKey val id: String,
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
