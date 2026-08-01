package org.gipsybuho.recetasfamiliares.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE familyId = :familyId AND deleted = 0 ORDER BY updatedAt DESC")
    fun observeRecipes(familyId: String): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id AND familyId = :familyId LIMIT 1")
    fun observeRecipe(id: String, familyId: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE familyId = :familyId AND deleted = 0 ORDER BY updatedAt DESC")
    suspend fun findAll(familyId: String): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE familyId = :familyId AND syncVersion <= 0 AND deleted = 0")
    suspend fun findPendingCreate(familyId: String): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE familyId = :familyId AND syncVersion <= 0 AND deleted = 1")
    suspend fun findPendingDelete(familyId: String): List<RecipeEntity>

    @Query("SELECT id FROM recipes WHERE familyId = :familyId AND syncVersion <= 0")
    suspend fun findPendingIds(familyId: String): List<String>

    @Query("SELECT * FROM recipes WHERE id = :id AND familyId = :familyId AND deleted = 0 LIMIT 1")
    suspend fun findByIdForFamily(id: String, familyId: String): RecipeEntity?

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Upsert
    suspend fun upsertAll(recipes: List<RecipeEntity>)
}

@Dao
interface RecipeIngredientDao {
    @Query("""
        SELECT ri.* FROM recipe_ingredients ri
        INNER JOIN recipes r ON r.id = ri.recipeId
        WHERE ri.recipeId = :recipeId AND r.familyId = :familyId AND ri.deleted = 0
        ORDER BY ri.position ASC
    """)
    fun observeIngredients(recipeId: String, familyId: String): Flow<List<RecipeIngredientEntity>>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId IN (:recipeIds) AND deleted = 0 ORDER BY position ASC")
    suspend fun findByRecipeIds(recipeIds: List<String>): List<RecipeIngredientEntity>

    @Query("""
        SELECT ri.* FROM recipe_ingredients ri
        INNER JOIN recipes r ON r.id = ri.recipeId
        WHERE r.familyId = :familyId AND ri.deleted = 0
    """)
    fun observeAllIngredients(familyId: String): Flow<List<RecipeIngredientEntity>>

    @Query("""
        SELECT ri.id FROM recipe_ingredients ri
        INNER JOIN recipes r ON r.id = ri.recipeId
        WHERE r.familyId = :familyId AND ri.syncVersion <= 0
    """)
    suspend fun findPendingIds(familyId: String): List<String>

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteByRecipeId(recipeId: String)

    @Upsert
    suspend fun upsertAll(ingredients: List<RecipeIngredientEntity>)
}

@Dao
interface RecipeStepDao {
    @Query("""
        SELECT rs.* FROM recipe_steps rs
        INNER JOIN recipes r ON r.id = rs.recipeId
        WHERE rs.recipeId = :recipeId AND r.familyId = :familyId AND rs.deleted = 0
        ORDER BY rs.position ASC
    """)
    fun observeSteps(recipeId: String, familyId: String): Flow<List<RecipeStepEntity>>

    @Query("SELECT * FROM recipe_steps WHERE recipeId IN (:recipeIds) AND deleted = 0 ORDER BY position ASC")
    suspend fun findByRecipeIds(recipeIds: List<String>): List<RecipeStepEntity>

    @Query("""
        SELECT rs.id FROM recipe_steps rs
        INNER JOIN recipes r ON r.id = rs.recipeId
        WHERE r.familyId = :familyId AND rs.syncVersion <= 0
    """)
    suspend fun findPendingIds(familyId: String): List<String>

    @Query("DELETE FROM recipe_steps WHERE recipeId = :recipeId")
    suspend fun deleteByRecipeId(recipeId: String)

    @Upsert
    suspend fun upsertAll(steps: List<RecipeStepEntity>)
}

@Dao
interface StockDao {
    @Query("SELECT * FROM stock_items WHERE familyId = :familyId AND deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeStock(familyId: String): Flow<List<StockItemEntity>>

    @Query("SELECT * FROM stock_items WHERE familyId = :familyId AND syncVersion <= 0 AND deleted = 0")
    suspend fun findPendingCreate(familyId: String): List<StockItemEntity>

    @Query("SELECT * FROM stock_items WHERE familyId = :familyId AND syncVersion <= 0 AND deleted = 1")
    suspend fun findPendingDelete(familyId: String): List<StockItemEntity>

    @Query("SELECT id FROM stock_items WHERE familyId = :familyId AND syncVersion <= 0")
    suspend fun findPendingIds(familyId: String): List<String>

    @Query("DELETE FROM stock_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM stock_items WHERE familyId = :familyId AND deleted = 0 AND expiresAt IS NOT NULL")
    suspend fun findExpiringItems(familyId: String): List<StockItemEntity>

    @Query("""
        SELECT * FROM stock_items WHERE familyId = :familyId AND deleted = 0 AND (
            (expiresAt IS NOT NULL AND expiresAt <= :threshold)
            OR (lowStockThreshold IS NOT NULL AND quantity IS NOT NULL AND quantity <= lowStockThreshold)
        )
    """)
    suspend fun findCriticalItems(threshold: String, familyId: String): List<StockItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<StockItemEntity>)
}

@Dao
interface MenuItemDao {
    @Query("SELECT * FROM menu_items WHERE familyId = :familyId AND deleted = 0 ORDER BY plannedDate ASC")
    fun observeMenuItems(familyId: String): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE familyId = :familyId AND plannedDate >= :weekStart AND deleted = 0 ORDER BY plannedDate ASC")
    fun observeMenuItemsFrom(weekStart: String, familyId: String): Flow<List<MenuItemEntity>>

    @Upsert
    suspend fun upsertAll(items: List<MenuItemEntity>)
}

@Dao
interface ShoppingListDao {
    @Query("SELECT * FROM shopping_lists WHERE familyId = :familyId AND deleted = 0 ORDER BY updatedAt DESC")
    fun observeShoppingLists(familyId: String): Flow<List<ShoppingListEntity>>

    @Upsert
    suspend fun upsertAll(lists: List<ShoppingListEntity>)
}

@Dao
interface ShoppingListItemDao {
    @Query("""
        SELECT sli.* FROM shopping_list_items sli
        INNER JOIN shopping_lists sl ON sl.id = sli.shoppingListId
        WHERE sli.shoppingListId = :shoppingListId AND sl.familyId = :familyId AND sli.deleted = 0
        ORDER BY sli.position ASC
    """)
    fun observeItems(shoppingListId: String, familyId: String): Flow<List<ShoppingListItemEntity>>

    @Query("""
        SELECT sli.* FROM shopping_list_items sli
        INNER JOIN shopping_lists sl ON sl.id = sli.shoppingListId
        WHERE sl.familyId = :familyId AND sli.syncVersion <= 0 AND sli.deleted = 0
    """)
    suspend fun findPendingCheck(familyId: String): List<ShoppingListItemEntity>

    @Query("""
        SELECT sli.id FROM shopping_list_items sli
        INNER JOIN shopping_lists sl ON sl.id = sli.shoppingListId
        WHERE sl.familyId = :familyId AND sli.syncVersion <= 0
    """)
    suspend fun findPendingIds(familyId: String): List<String>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM shopping_list_items sli
            INNER JOIN shopping_lists sl ON sl.id = sli.shoppingListId
            WHERE sli.id = :itemId AND sl.familyId = :familyId
        )
    """)
    suspend fun belongsToFamily(itemId: String, familyId: String): Boolean

    @Upsert
    suspend fun upsertAll(items: List<ShoppingListItemEntity>)
}

@Dao
interface FavoriteRecipeDao {
    @Query("SELECT * FROM favorite_recipes WHERE familyId = :familyId AND deleted = 0 ORDER BY updatedAt DESC")
    fun observeFavorites(familyId: String): Flow<List<FavoriteRecipeEntity>>

    @Query("SELECT * FROM favorite_recipes WHERE familyId = :familyId AND recipeId = :recipeId AND deleted = 0 LIMIT 1")
    suspend fun findByRecipeId(recipeId: String, familyId: String): FavoriteRecipeEntity?

    @Query("SELECT * FROM favorite_recipes WHERE familyId = :familyId AND syncVersion <= 0 AND deleted = 0")
    suspend fun findPendingCreate(familyId: String): List<FavoriteRecipeEntity>

    @Query("SELECT * FROM favorite_recipes WHERE familyId = :familyId AND syncVersion <= 0 AND deleted = 1")
    suspend fun findPendingDelete(familyId: String): List<FavoriteRecipeEntity>

    @Query("SELECT id FROM favorite_recipes WHERE familyId = :familyId AND syncVersion <= 0")
    suspend fun findPendingIds(familyId: String): List<String>

    @Query("DELETE FROM favorite_recipes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Upsert
    suspend fun upsertAll(favorites: List<FavoriteRecipeEntity>)
}

@Dao
interface FamilyNoteDao {
    @Query("SELECT * FROM family_notes WHERE familyId = :familyId AND deleted = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeNotes(familyId: String): Flow<List<FamilyNoteEntity>>

    @Query("SELECT * FROM family_notes WHERE familyId = :familyId AND syncVersion <= 0 AND deleted = 0")
    suspend fun findPendingCreate(familyId: String): List<FamilyNoteEntity>

    @Query("SELECT * FROM family_notes WHERE familyId = :familyId AND syncVersion <= 0 AND deleted = 1")
    suspend fun findPendingDelete(familyId: String): List<FamilyNoteEntity>

    @Query("SELECT id FROM family_notes WHERE familyId = :familyId AND syncVersion <= 0")
    suspend fun findPendingIds(familyId: String): List<String>

    @Query("DELETE FROM family_notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Upsert
    suspend fun upsertAll(notes: List<FamilyNoteEntity>)
}

@Dao
interface RecipePhotoDao {
    @Query("""
        SELECT rp.* FROM recipe_photos rp
        INNER JOIN recipes r ON r.id = rp.recipeId
        WHERE rp.recipeId = :recipeId AND r.familyId = :familyId AND rp.deleted = 0
        ORDER BY rp.position ASC
    """)
    fun observePhotos(recipeId: String, familyId: String): Flow<List<RecipePhotoEntity>>

    @Query("""
        SELECT rp.* FROM recipe_photos rp
        INNER JOIN recipes r ON r.id = rp.recipeId
        WHERE rp.recipeId = :recipeId AND r.familyId = :familyId AND rp.deleted = 0
        ORDER BY rp.position ASC LIMIT 1
    """)
    suspend fun findFirstByRecipeId(recipeId: String, familyId: String): RecipePhotoEntity?

    @Query("""
        SELECT rp.* FROM recipe_photos rp
        INNER JOIN recipes r ON r.id = rp.recipeId
        WHERE r.familyId = :familyId AND rp.deleted = 0 AND r.deleted = 0
        ORDER BY rp.recipeId ASC, rp.position ASC
    """)
    fun observeCovers(familyId: String): Flow<List<RecipePhotoEntity>>

    @Upsert
    suspend fun upsertAll(photos: List<RecipePhotoEntity>)
}
