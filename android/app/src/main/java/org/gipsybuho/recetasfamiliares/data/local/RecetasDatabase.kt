package org.gipsybuho.recetasfamiliares.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeStepEntity::class,
        StockItemEntity::class,
        MenuItemEntity::class,
        ShoppingListEntity::class,
        ShoppingListItemEntity::class,
        FavoriteRecipeEntity::class,
        FamilyNoteEntity::class,
        RecipePhotoEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class RecetasDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun recipeIngredientDao(): RecipeIngredientDao
    abstract fun recipeStepDao(): RecipeStepDao
    abstract fun stockDao(): StockDao
    abstract fun menuItemDao(): MenuItemDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun shoppingListItemDao(): ShoppingListItemDao
    abstract fun favoriteRecipeDao(): FavoriteRecipeDao
    abstract fun familyNoteDao(): FamilyNoteDao
    abstract fun recipePhotoDao(): RecipePhotoDao
}
