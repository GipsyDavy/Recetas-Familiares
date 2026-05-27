package org.gipsybuho.recetasfamiliares.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecipeEntity::class,
        StockItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class RecetasDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun stockDao(): StockDao
}
