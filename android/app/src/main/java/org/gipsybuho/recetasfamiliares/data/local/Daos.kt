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

    @Upsert
    suspend fun upsertAll(recipes: List<RecipeEntity>)
}

@Dao
interface StockDao {
    @Query("SELECT * FROM stock_items WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeStock(): Flow<List<StockItemEntity>>

    @Upsert
    suspend fun upsertAll(items: List<StockItemEntity>)
}
