package org.gipsybuho.recetasfamiliares.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    fun observeRecipe(id: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE deleted = 0 ORDER BY updatedAt DESC")
    suspend fun findAll(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE syncVersion <= 0 AND deleted = 0")
    suspend fun findPendingCreate(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE syncVersion <= 0 AND deleted = 1")
    suspend fun findPendingDelete(): List<RecipeEntity>

    @Upsert
    suspend fun upsertAll(recipes: List<RecipeEntity>)
}

@Dao
interface RecipeIngredientDao {
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId AND deleted = 0 ORDER BY position ASC")
    fun observeIngredients(recipeId: String): Flow<List<RecipeIngredientEntity>>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId IN (:recipeIds) AND deleted = 0 ORDER BY position ASC")
    suspend fun findByRecipeIds(recipeIds: List<String>): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredients WHERE deleted = 0")
    fun observeAllIngredients(): Flow<List<RecipeIngredientEntity>>

    @Upsert
    suspend fun upsertAll(ingredients: List<RecipeIngredientEntity>)
}

@Dao
interface RecipeStepDao {
    @Query("SELECT * FROM recipe_steps WHERE recipeId = :recipeId AND deleted = 0 ORDER BY position ASC")
    fun observeSteps(recipeId: String): Flow<List<RecipeStepEntity>>

    @Query("SELECT * FROM recipe_steps WHERE recipeId IN (:recipeIds) AND deleted = 0 ORDER BY position ASC")
    suspend fun findByRecipeIds(recipeIds: List<String>): List<RecipeStepEntity>

    @Upsert
    suspend fun upsertAll(steps: List<RecipeStepEntity>)
}

@Dao
interface StockDao {
    @Query("SELECT * FROM stock_items WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeStock(): Flow<List<StockItemEntity>>

    @Query("SELECT * FROM stock_items WHERE syncVersion <= 0 AND deleted = 0")
    suspend fun findPendingCreate(): List<StockItemEntity>

    @Query("SELECT * FROM stock_items WHERE syncVersion <= 0 AND deleted = 1")
    suspend fun findPendingDelete(): List<StockItemEntity>

    @Query("SELECT * FROM stock_items WHERE deleted = 0 AND expiresAt IS NOT NULL")
    suspend fun findExpiringItems(): List<StockItemEntity>

    @Query("""
        SELECT * FROM stock_items WHERE deleted = 0 AND (
            (expiresAt IS NOT NULL AND expiresAt <= :threshold)
            OR (lowStockThreshold IS NOT NULL AND quantity IS NOT NULL AND quantity <= lowStockThreshold)
        )
    """)
    suspend fun findCriticalItems(threshold: String): List<StockItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<StockItemEntity>)
}

@Dao
interface MenuItemDao {
    @Query("SELECT * FROM menu_items WHERE deleted = 0 ORDER BY plannedDate ASC")
    fun observeMenuItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE plannedDate >= :weekStart AND deleted = 0 ORDER BY plannedDate ASC")
    fun observeMenuItemsFrom(weekStart: String): Flow<List<MenuItemEntity>>

    @Upsert
    suspend fun upsertAll(items: List<MenuItemEntity>)
}

@Dao
interface ShoppingListDao {
    @Query("SELECT * FROM shopping_lists WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeShoppingLists(): Flow<List<ShoppingListEntity>>

    @Upsert
    suspend fun upsertAll(lists: List<ShoppingListEntity>)
}

@Dao
interface ShoppingListItemDao {
    @Query("SELECT * FROM shopping_list_items WHERE shoppingListId = :shoppingListId AND deleted = 0 ORDER BY position ASC")
    fun observeItems(shoppingListId: String): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_items WHERE syncVersion <= 0 AND deleted = 0")
    suspend fun findPendingCheck(): List<ShoppingListItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<ShoppingListItemEntity>)
}

@Dao
interface FavoriteRecipeDao {
    @Query("SELECT * FROM favorite_recipes WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteRecipeEntity>>

    @Query("SELECT * FROM favorite_recipes WHERE recipeId = :recipeId AND deleted = 0 LIMIT 1")
    suspend fun findByRecipeId(recipeId: String): FavoriteRecipeEntity?

    @Query("SELECT * FROM favorite_recipes WHERE syncVersion <= 0 AND deleted = 0")
    suspend fun findPendingCreate(): List<FavoriteRecipeEntity>

    @Query("SELECT * FROM favorite_recipes WHERE syncVersion <= 0 AND deleted = 1")
    suspend fun findPendingDelete(): List<FavoriteRecipeEntity>

    @Upsert
    suspend fun upsertAll(favorites: List<FavoriteRecipeEntity>)
}

@Dao
interface FamilyNoteDao {
    @Query("SELECT * FROM family_notes WHERE deleted = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeNotes(): Flow<List<FamilyNoteEntity>>

    @Query("SELECT * FROM family_notes WHERE syncVersion <= 0 AND deleted = 0")
    suspend fun findPendingCreate(): List<FamilyNoteEntity>

    @Query("SELECT * FROM family_notes WHERE syncVersion <= 0 AND deleted = 1")
    suspend fun findPendingDelete(): List<FamilyNoteEntity>

    @Upsert
    suspend fun upsertAll(notes: List<FamilyNoteEntity>)
}

@Dao
interface RecipePhotoDao {
    @Query("SELECT * FROM recipe_photos WHERE recipeId = :recipeId AND deleted = 0 ORDER BY position ASC")
    fun observePhotos(recipeId: String): Flow<List<RecipePhotoEntity>>

    @Query("SELECT * FROM recipe_photos WHERE recipeId = :recipeId AND deleted = 0 ORDER BY position ASC LIMIT 1")
    suspend fun findFirstByRecipeId(recipeId: String): RecipePhotoEntity?

    @Upsert
    suspend fun upsertAll(photos: List<RecipePhotoEntity>)
}
