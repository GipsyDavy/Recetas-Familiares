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
    val updatedAt: String,
    val syncVersion: Long,
    val deleted: Boolean
)
